package org.cru.contentscoring.core.queue;

import com.day.cq.mailer.MessageGateway;
import com.day.cq.mailer.MessageGatewayService;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.cru.contentscoring.core.models.ContentScore;
import org.cru.contentscoring.core.models.ContentScoreUpdateRequest;
import org.cru.contentscoring.core.models.RetryElement;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.mail.internet.AddressException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.contains;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.class)
public class UploadQueueTest {
    private static final long MAX_SIZE = 4000000L;
    private static final long WAIT_TIME = 30L * 1000L;
    private static final int MAX_RETRIES = 3;
    private static final String API_ENDPOINT = "http://somewhere-out.there.com";
    private static final String ERROR_EMAIL_RECIPIENTS = "test.email@here.com";

    @Mock
    private MessageGatewayService messageGatewayService;
    @Mock
    private MessageGateway<HtmlEmail> messageGateway;

    private UploadQueue uploadQueue;
    private UploadQueue uploadQueueSpy;
    private ContentScoreUpdateRequest request;

    @Before
    public void setup() {
        uploadQueue = new UploadQueue(
            MAX_SIZE,
            WAIT_TIME,
            MAX_RETRIES,
            API_ENDPOINT,
            ERROR_EMAIL_RECIPIENTS,
            messageGatewayService,
            null);
        uploadQueueSpy = spy(uploadQueue);

        request = new ContentScoreUpdateRequest();
        request.setUri("https://some-uri.com/page.html");
        request.setScore(buildScore(1, 2, 3, 4, BigDecimal.ONE));

        when(messageGatewayService.getGateway(HtmlEmail.class)).thenReturn(messageGateway);
    }

    @Test
    public void testGetNoPendingBatches() {
        assertThat(uploadQueue.getPendingBatches(), is(equalTo(Lists.newArrayList())));
    }

    @Test
    public void testGetPendingBatches() {
        List<ContentScoreUpdateRequest> pendingBatches = Lists.newArrayList(request);

        uploadQueue = new UploadQueue(
            MAX_SIZE,
            WAIT_TIME,
            MAX_RETRIES,
            API_ENDPOINT,
            ERROR_EMAIL_RECIPIENTS,
            messageGatewayService,
            pendingBatches);

        List<ContentScoreUpdateRequest> returnedBatches = uploadQueue.getPendingBatches();

        assertThat(returnedBatches, is(equalTo(pendingBatches)));
    }

    @Test
    public void testGetPendingBatchesWithRetry() {
        List<ContentScoreUpdateRequest> queue = Lists.newArrayList(request);

        ContentScore retryScore = buildScore(1, 1, 3, 4, BigDecimal.ONE);

        ContentScoreUpdateRequest retryRequest = new ContentScoreUpdateRequest();
        retryRequest.setUri("https://some-uri.com/retry-page.html");
        retryRequest.setScore(retryScore);

        RetryElement retryElement = new RetryElement(Lists.newArrayList(retryRequest), 1);

        uploadQueue = new UploadQueue(
            MAX_SIZE,
            WAIT_TIME,
            MAX_RETRIES,
            API_ENDPOINT,
            ERROR_EMAIL_RECIPIENTS,
            messageGatewayService,
            queue);

        uploadQueue.retryQueue.add(retryElement);

        List<ContentScoreUpdateRequest> pendingBatches = uploadQueue.getPendingBatches();

        assertThat(pendingBatches.size(), is(equalTo(2)));
        assertThat(pendingBatches.contains(request), is(equalTo(true)));
        assertThat(pendingBatches.contains(retryRequest), is(equalTo(true)));
    }

    @Test
    public void testPutRequestIntoQueue() {
        // getPendingBatches() is tested above
        List<ContentScoreUpdateRequest> existing = uploadQueue.getPendingBatches();
        assertThat(existing.size(), is(equalTo(0)));

        uploadQueue.put(request);

        existing = uploadQueue.getPendingBatches();
        assertThat(existing.size(), is(equalTo(1)));
        assertThat(existing.contains(request), is(equalTo(true)));
    }

    @Test
    public void testSuccessfulSendRequest() {
        Response successfulResponse = mock(Response.class);
        when(successfulResponse.getStatus()).thenReturn(200);

        WebTarget webTarget = mockWebTarget(successfulResponse);

        Map<ContentScoreUpdateRequest, String> failedRequests = Maps.newHashMap();

        uploadQueue.sendRequest(webTarget, request, failedRequests);

        assertThat(failedRequests.size(), is(equalTo(0)));
    }

    @Test
    public void testInternalErrorSendRequest() {
        String errorMessage = "We Failed";
        WebTarget webTarget = mockErrorWebTarget(errorMessage, 500);

        Map<ContentScoreUpdateRequest, String> failedRequests = Maps.newHashMap();

        uploadQueue.sendRequest(webTarget, request, failedRequests);

        assertThat(failedRequests.size(), is(equalTo(1)));
        assertThat(failedRequests.get(request), is(equalTo(errorMessage)));
    }

    @Test
    public void testClientErrorSendRequest() {
        String errorMessage = "You Failed";
        WebTarget webTarget = mockErrorWebTarget(errorMessage, 400);

        Map<ContentScoreUpdateRequest, String> failedRequests = Maps.newHashMap();

        uploadQueue.sendRequest(webTarget, request, failedRequests);

        assertThat(failedRequests.size(), is(equalTo(1)));
        assertThat(failedRequests.get(request), is(equalTo(errorMessage)));
    }

    private WebTarget mockErrorWebTarget(final String errorMessage, final int statusCode) {
        Response errorResponse = mock(Response.class);
        when(errorResponse.getStatus()).thenReturn(statusCode);
        when(errorResponse.readEntity(String.class)).thenReturn(errorMessage);

        return mockWebTarget(errorResponse);
    }

    private WebTarget mockWebTarget(final Response response) {
        Invocation.Builder builder = mock(Invocation.Builder.class);
        when(builder.post(Entity.entity(request, MediaType.APPLICATION_JSON))).thenReturn(response);

        WebTarget webTarget = mock(WebTarget.class);
        when(webTarget.request()).thenReturn(builder);
        return webTarget;
    }

    @Test
    public void testSendEmail() throws EmailException, AddressException {
        String errorMessage = "Error Message\nSecond Line";
        uploadQueue.sendEmail(errorMessage);

        verify(messageGateway).send(any(HtmlEmail.class));
    }

    @Test
    public void testBuildEmailBody() {
        String errorMessage = "Error Message\nSecond Line";
        String htmlErrorMessage =
            "<h1>An error was found when updating the content score in AEM</h1>"
            + "<p></p><p></p>"
            + "<p>Error Message</p>"
            + "<p>Second Line</p>";

        assertThat(uploadQueue.buildEmailBody(errorMessage), is(equalTo(htmlErrorMessage)));
    }

    @Test
    public void testHandleFailedRetryNotMaxed() throws EmailException, AddressException {
        assertThat(uploadQueue.retryQueue.size(), is(equalTo(0)));

        RetryElement retryElement = new RetryElement(Lists.newArrayList(request), 1);
        uploadQueue.handleFailedRetry(retryElement, "Failed");

        assertThat(retryElement.getRetries(), is(equalTo(2)));
        assertThat(uploadQueue.retryQueue.size(), is(equalTo(1)));
    }

    @Test
    public void testHandleFailedRetryMaxed() throws EmailException, AddressException {
        RetryElement retryElement = new RetryElement(Lists.newArrayList(request), MAX_RETRIES + 1);
        uploadQueue.handleFailedRetry(retryElement, "Failed");

        verify(messageGateway).send(any(HtmlEmail.class));
    }

    @Test
    public void testUpdateContentScoreRequestSuccess() throws Exception {
        List<ContentScoreUpdateRequest> batch = Lists.newArrayList(request);
        RetryElement retryElement = new RetryElement(batch, 1);
        doReturn(Maps.newHashMap()).when(uploadQueueSpy).sendRequestBatch(batch);

        uploadQueueSpy.updateContentScoreRequest(retryElement);

        verify(uploadQueueSpy, never()).handleFailedRetry(any(RetryElement.class), anyString());
    }

    @Test
    public void testUpdateContentScoreRequestFailure() throws Exception {
        List<ContentScoreUpdateRequest> batch = Lists.newArrayList(request);
        RetryElement retryElement = new RetryElement(batch, 1);

        Map<ContentScoreUpdateRequest, String> failedRequests = Maps.newHashMap();
        failedRequests.put(request, "Error");
        doReturn(failedRequests).when(uploadQueueSpy).sendRequestBatch(batch);

        uploadQueueSpy.updateContentScoreRequest(retryElement);

        verify(uploadQueueSpy).handleFailedRetry(any(RetryElement.class), eq("Error"));
    }

    @Test
    public void testUpdateContentScoreRequestMultipleFailures() throws Exception {
        ContentScoreUpdateRequest request2 = new ContentScoreUpdateRequest();
        request2.setUri("some-uri");
        request2.setScore(buildScore(2, 3, 4, 1, BigDecimal.ONE));

        List<ContentScoreUpdateRequest> batch = Lists.newArrayList(request, request2);
        RetryElement retryElement = new RetryElement(batch, 1);

        Map<ContentScoreUpdateRequest, String> failedRequests = Maps.newHashMap();
        failedRequests.put(request, "Error");
        failedRequests.put(request2, "Second Error");
        doReturn(failedRequests).when(uploadQueueSpy).sendRequestBatch(batch);

        uploadQueueSpy.updateContentScoreRequest(retryElement);

        verify(uploadQueueSpy).handleFailedRetry(any(RetryElement.class), contains(","));
    }

    @Test
    public void testUpdateContentScoreRequestFirstTimeSuccess() throws Exception {
        List<ContentScoreUpdateRequest> batch = Lists.newArrayList(request);
        doReturn(Maps.newHashMap()).when(uploadQueueSpy).sendRequestBatch(batch);

        uploadQueueSpy.updateContentScoreRequest(batch);

        verify(uploadQueueSpy, never()).handleFailedFirstAttempt(any(List.class));
    }

    @Test
    public void testUpdateContentScoreRequestFirstTimeFailure() throws Exception {
        List<ContentScoreUpdateRequest> batch = Lists.newArrayList(request);

        Map<ContentScoreUpdateRequest, String> failedRequests = Maps.newHashMap();
        failedRequests.put(request, "Error");
        doReturn(failedRequests).when(uploadQueueSpy).sendRequestBatch(batch);

        uploadQueueSpy.updateContentScoreRequest(batch);

        verify(uploadQueueSpy).handleFailedFirstAttempt(new ArrayList<>(failedRequests.keySet()));
    }

    private ContentScore buildScore(
        final int unaware,
        final int curious,
        final int follower,
        final int guide,
        final BigDecimal confidence) {

        ContentScore contentScore = new ContentScore();
        contentScore.setUnaware(unaware);
        contentScore.setCurious(curious);
        contentScore.setFollower(follower);
        contentScore.setGuide(guide);
        contentScore.setConfidence(confidence);

        return contentScore;
    }
}







