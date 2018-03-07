package org.cru.contentscoring.core.queue;

import com.day.cq.mailer.MessageGatewayService;
import com.google.common.collect.Lists;
import org.cru.contentscoring.core.models.ContentScore;
import org.cru.contentscoring.core.models.ContentScoreUpdateRequest;
import org.cru.contentscoring.core.models.RetryElement;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class UploadQueueTest {
    private static final long MAX_SIZE = 4000000L;
    private static final long WAIT_TIME = 30L * 1000L;
    private static final int MAX_RETRIES = 3;
    private static final String API_ENDPOINT = "http://somewhere-out.there.com";
    private static final String ERROR_EMAIL_RECIPIENTS = "test.email@here.com";

    @Mock
    private MessageGatewayService messageGatewayService;

    private UploadQueue uploadQueue;

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
    }

    @Test
    public void testGetNoPendingBatches() {
        assertThat(uploadQueue.getPendingBatches(), is(equalTo(Lists.newArrayList())));
    }

    @Test
    public void testGetPendingBatches() {
        ContentScore contentScore = buildScore(1, 2, 2, 1, BigDecimal.ONE);

        ContentScoreUpdateRequest request = new ContentScoreUpdateRequest();
        request.setUri("https://some-uri.com/page.html");
        request.setScore(contentScore);

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
        ContentScore contentScore = buildScore(3, 2, 2, 1, BigDecimal.ONE);

        ContentScoreUpdateRequest request = new ContentScoreUpdateRequest();
        request.setUri("https://some-uri.com/page.html");
        request.setScore(contentScore);

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







