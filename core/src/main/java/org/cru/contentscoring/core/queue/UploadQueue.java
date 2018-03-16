package org.cru.contentscoring.core.queue;

import com.day.cq.mailer.MessageGateway;
import com.day.cq.mailer.MessageGatewayService;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.cru.contentscoring.core.models.ContentScoreUpdateRequest;
import org.cru.contentscoring.core.models.ErrorResponse;
import org.cru.contentscoring.core.models.RetryElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class UploadQueue implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(UploadQueue.class);

    private long waitTime;
    private int maxRetries;
    private boolean stop;
    private String apiEndpoint;
    private String errorEmailRecipients;
    private MessageGatewayService messageGatewayService;

    private ConcurrentLinkedQueue<ContentScoreUpdateRequest> queue;
    ArrayDeque<RetryElement> retryQueue;

    public UploadQueue(
        long waitTime,
        int maxRetries,
        String apiEndpoint,
        String errorEmailRecipients,
        MessageGatewayService messageGatewayService,
        List<ContentScoreUpdateRequest> pendingBatches) {

        this.waitTime = waitTime;
        this.maxRetries = maxRetries;
        this.apiEndpoint = apiEndpoint;
        this.errorEmailRecipients = errorEmailRecipients;
        this.messageGatewayService = messageGatewayService;

        stop = false;
        queue = new ConcurrentLinkedQueue<>();
        retryQueue = new ArrayDeque<>();

        if (pendingBatches != null && !pendingBatches.isEmpty()) {
            queue.addAll(pendingBatches);
        }
    }

    public List<ContentScoreUpdateRequest> getPendingBatches() {
        List<ContentScoreUpdateRequest> pendingBatches =
            Lists.newArrayList(queue.toArray(new ContentScoreUpdateRequest[queue.size()]));

        List<RetryElement> retryElements = Lists.newArrayList(retryQueue.toArray(new RetryElement[retryQueue.size()]));
        retryElements
            .stream()
            .map(RetryElement::getBatch)
            .forEach(pendingBatches::addAll);

        return pendingBatches;
    }

    public void put(ContentScoreUpdateRequest request) {
        queue.offer(request);
        synchronized (this) {
            this.notify();
        }
    }

    public void stop() {
        stop = true;
    }

    @Override
    public void run() {
        while (!stop) {
            try {
                if (queue.isEmpty() && retryQueue.isEmpty()) {
                    LOG.debug("Queue goes to sleep until a new element is available.");
                    synchronized (this) {
                        this.wait();
                    }
                } else if (!queue.isEmpty()) {
                    if (waitTime > 0) {
                        LOG.debug("Queue waits {} seconds before sending next batch.", waitTime / 1000);
                        Thread.sleep(waitTime);
                    }
                    LOG.debug("Queue size: {}", queue.size());
                    updateContentScoreRequest(getBatch());
                }
                if (!retryQueue.isEmpty()) {
                    LOG.warn("Retry queue has elements, trying to send in {} seconds.", waitTime / 1000);
                    Thread.sleep(waitTime);
                    updateContentScoreRequest(retryQueue.poll());
                }
            } catch (Exception e) {
                LOG.error("UploadQueue: ", e);
            }
        }
    }

    @VisibleForTesting
    void updateContentScoreRequest(List<ContentScoreUpdateRequest> requests) {
        try {
            Map<ContentScoreUpdateRequest, String> failedRequests = sendRequestBatch(requests);

            if (!failedRequests.isEmpty()) {
                handleFailedFirstAttempt(new ArrayList<>(failedRequests.keySet()));
            }
        } catch (Exception e) {
            handleFailedFirstAttempt(requests);
        }
    }

    @VisibleForTesting
    void handleFailedFirstAttempt(List<ContentScoreUpdateRequest> failedRequests) {
        RetryElement retryElement = new RetryElement(failedRequests, 1);
        retryQueue.add(retryElement);
        LOG.warn("RetryElement Added {}", retryElement.toString());
    }

    @VisibleForTesting
    void updateContentScoreRequest(RetryElement retryElement) throws EmailException, AddressException {
        try {
            Map<ContentScoreUpdateRequest, String> failedRequests = sendRequestBatch(retryElement.getBatch());

            if (failedRequests.isEmpty()) {
                LOG.info("RetryElement successfully indexed {}", retryElement.toString());
                return;
            }
            RetryElement narrowedRetryElement = new RetryElement(
                new ArrayList<>(failedRequests.keySet()),
                retryElement.getRetries());

            handleFailedRetry(narrowedRetryElement, Joiner.on(',').join(failedRequests.values()));
        } catch (Exception e) {
            handleFailedRetry(retryElement, e.getMessage());
        }
    }

    @VisibleForTesting
    void handleFailedRetry(RetryElement retryElement, String errorMessage) throws EmailException, AddressException {
        if (maxRetries >= retryElement.incrementRetries()) {
            retryQueue.add(retryElement);
            LOG.warn("RetryElement Added {}", retryElement.toString());
        } else {
            String error = MessageFormat.format(
                "UploadQueue: Max number of retries reached for: {0}\nError message was: {1}",
                retryElement.toString(),
                errorMessage);

            LOG.error(error);
            sendEmail(error);
        }
    }

    @VisibleForTesting
    void sendEmail(String error) throws EmailException, AddressException {
        MessageGateway<HtmlEmail> messageGateway = messageGatewayService.getGateway(HtmlEmail.class);

        List<InternetAddress> emailRecipients = Lists.newArrayList();
        for (String to : errorEmailRecipients.split(",")) {
            emailRecipients.add(new InternetAddress(to));
        }

        Email email = new HtmlEmail()
            .setHtmlMsg(buildEmailBody(error))
            .setTo(emailRecipients)
            .setSubject("Cru AEM Content Scoring Error");
        messageGateway.send((HtmlEmail) email);
    }

    @VisibleForTesting
    String buildEmailBody(String error) {
        return "<h1>An error was found when updating the content score in AEM</h1>"
            + "<p></p><p></p>"
            + "<p>" + error.replace("\n", "</p><p>") + "</p>";
    }

    @VisibleForTesting
    Map<ContentScoreUpdateRequest, String> sendRequestBatch(List<ContentScoreUpdateRequest> requests) throws Exception {
        Client client = ClientBuilder.newClient();
        WebTarget webTarget = client
            .target(apiEndpoint)
            .path("score");

        Map<ContentScoreUpdateRequest, String> failedRequests = Maps.newHashMap();

        for (ContentScoreUpdateRequest request : requests) {
            sendRequest(webTarget, request, failedRequests);
        }

        return failedRequests;
    }

    @VisibleForTesting
    void sendRequest(
        WebTarget webTarget,
        ContentScoreUpdateRequest request,
        Map<ContentScoreUpdateRequest, String> failedRequests) throws IOException {

        ObjectMapper objectMapper = new ObjectMapper();
        String jsonRequest = objectMapper.writeValueAsString(request);

        Response response = webTarget
            .request()
            .post(Entity.entity(jsonRequest, MediaType.APPLICATION_JSON));

        if (response.getStatus() != 200) {
            String jsonResponse = response.readEntity(String.class);
            String errorMessage = parseErrorMessage(jsonResponse, objectMapper);

            if (response.getStatus() >= 500) {
                LOG.debug(
                    "Internal server error when sending request for {}: {}",
                    request.getUri(),
                    errorMessage);
                failedRequests.put(request, errorMessage);
                return;
            }
            if (response.getStatus() >= 400) {
                LOG.debug(
                    "Client error when sending request for {}: {}",
                    request.getUri(),
                    errorMessage);
                failedRequests.put(request, errorMessage); //TODO: Should we retry client exceptions?
            }
        }
    }

    private String parseErrorMessage(final String jsonResponse, final ObjectMapper objectMapper) throws IOException {
        JsonParser jsonParser = objectMapper.getFactory().createParser(jsonResponse);
        ErrorResponse errorResponse = jsonParser.readValueAs(ErrorResponse.class);
        return errorResponse.getMessage();
    }

    private List<ContentScoreUpdateRequest> getBatch() throws JsonProcessingException {
        List<ContentScoreUpdateRequest> requests = Lists.newArrayList();
        ContentScoreUpdateRequest nextRequest = queue.poll();

        while (nextRequest != null) {
            requests.add(nextRequest);
            nextRequest = queue.poll();
        }
        return requests;
    }
}
