package org.cru.contentscoring.core.queue;

import com.day.cq.mailer.MessageGateway;
import com.day.cq.mailer.MessageGatewayService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.cru.contentscoring.core.models.ContentScoreUpdateRequest;
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
import java.io.UnsupportedEncodingException;
import java.text.MessageFormat;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class UploadQueue implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(UploadQueue.class);

    private long maxSize;
    private long waitTime;
    private int maxRetries;
    private boolean stop;
    private String apiEndpoint;
    private String errorEmailRecipients;
    private MessageGatewayService messageGatewayService;

    private ConcurrentLinkedQueue<ContentScoreUpdateRequest> queue;
    private ArrayDeque<RetryElement> retryQueue;

    public UploadQueue(
        long maxSize,
        long waitTime,
        int maxRetries,
        String apiEndpoint,
        String errorEmailRecipients,
        MessageGatewayService messageGatewayService,
        List<ContentScoreUpdateRequest> pendingBatches) {

        this.maxSize = maxSize;
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
            Arrays.asList(queue.toArray(new ContentScoreUpdateRequest[queue.size()]));

        pendingBatches.addAll(Arrays.asList(retryQueue.toArray(new ContentScoreUpdateRequest[retryQueue.size()])));
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

    private void updateContentScoreRequest(List<ContentScoreUpdateRequest> requests) {
        try {
            sendRequest(requests);
        } catch (Exception e) {
            RetryElement retryElement = new RetryElement(requests, 1);
            retryQueue.add(retryElement);
            LOG.warn("RetryElement Added {}", retryElement.toString());
        }
    }

    private void updateContentScoreRequest(RetryElement retryElement) throws EmailException, AddressException {
        try {
            sendRequest(retryElement.getBatch());
            LOG.info("RetryElement successfully indexed {}", retryElement.toString());
        } catch (Exception e) {
            if (maxRetries >= retryElement.incrementRetries()) {
                retryQueue.add(retryElement);
                LOG.warn("RetryElement Added {}", retryElement.toString());
            } else {
                String error = MessageFormat.format(
                    "UploadQueue: Max number of retries reached for: {0}\nError message was: {1}",
                    retryElement.toString(),
                    e.getMessage());

                LOG.error(error);
                sendEmail(error);
            }
        }
    }

    private void sendEmail(String error) throws EmailException, AddressException {
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

    private String buildEmailBody(String error) {
        return "<h1>An error was found when updating the content score in AEM</h1>"
            + "<p></p><p></p>"
            + "<p>" + error.replace("\n", "</p><p>") + "</p>";
    }

    private void sendRequest(List<ContentScoreUpdateRequest> requests) throws Exception {
        Client client = ClientBuilder.newClient();
        WebTarget webTarget = client
            .target(apiEndpoint)
            .path("score");

        for (ContentScoreUpdateRequest request : requests) {
            Response response = webTarget
                .request()
                .post(Entity.entity(request, MediaType.APPLICATION_JSON));

            if (response.getStatus() >= 500) {
                throw new Exception("Some internal exception");
            }
            if (response.getStatus() >= 400) {
                throw new Exception("Some client exception");
            }
        }
    }

    private List<ContentScoreUpdateRequest> getBatch() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();

        List<ContentScoreUpdateRequest> requests = Lists.newArrayList();
        List<ContentScoreUpdateRequest> currentRequests = Lists.newArrayList();

        ContentScoreUpdateRequest nextRequest = queue.poll();
        currentRequests.add(nextRequest);

        while (nextRequest != null && getLength(objectMapper.writeValueAsString(currentRequests)) < maxSize) {
            requests.add(nextRequest);
            nextRequest = queue.poll();
            currentRequests.add(nextRequest);
        }

        if (requests.isEmpty() && nextRequest != null) {
            throw new RuntimeException(
                String.format(
                    "Batch size bigger than batch Max Size allowed. (MaxSize=%d) (ElementSize= %d) Element=%s",
                    maxSize,
                    getLength(objectMapper.writeValueAsString(currentRequests)),
                    objectMapper.writeValueAsString(nextRequest)));
        } else if (nextRequest != null) {
            put(nextRequest);
        }
        return requests;
    }

    private long getLength(String requestBody) {
        long contentLength = requestBody.length();
        try {
            byte[] bytes = requestBody.getBytes("UTF-8");
            contentLength = bytes.length;
        } catch (UnsupportedEncodingException e) {
            LOG.error("UnsupportedEncodingException: ", e);
        }
        return contentLength;
    }
}
