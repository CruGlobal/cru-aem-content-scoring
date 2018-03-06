package org.cru.contentscoring.core.service.impl;

import com.day.cq.mailer.MessageGatewayService;
import com.day.cq.wcm.api.Page;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.cru.contentscoring.core.models.ContentScore;
import org.cru.contentscoring.core.models.ContentScoreUpdateRequest;
import org.cru.contentscoring.core.models.ScoreType;
import org.cru.contentscoring.core.queue.UploadQueue;
import org.cru.contentscoring.core.service.ContentScoreUpdateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Map;

@Component
@Service(ContentScoreUpdateService.class)
public class ContentScoreUpdateServiceImpl implements ContentScoreUpdateService {
    private static final Logger LOG = LoggerFactory.getLogger(ContentScoreUpdateServiceImpl.class);

    static final String CONTENT_SCORE_UPDATED = "contentScoreLastUpdated";

    @Property(label = "API Endpoint", description = "The endpoint to which service requests can be submitted.")
    static final String API_ENDPOINT = "apiEndpoint";
    private String apiEndpoint;

    private static final Long DEFAULT_MAX_SIZE = 4000000L;
    @Property(label = "Max Size", description = "Max size of the batch sent to the Content Scoring API.")
    static final String MAX_SIZE = "maxSize";

    private static final Long DEFAULT_WAIT_TIME = 30L * 1000L;
    @Property(label = "Wait Time", description = "Time (in milliseconds) to wait between sending.")
    static final String WAIT_TIME = "waitTime";

    private static final Integer DEFAULT_MAX_RETRIES = 3;
    @Property(label = "Max Retries", description = "Max number of retries for unsuccessful attempts.")
    static final String MAX_RETRIES = "maxRetries";

    @Property(
        label = "Error Email Recipients",
        description = "When max number of retries is reached, an email will be sent. "
            + "Write recipients here separated by comma.")
    static final String ERROR_EMAIL_RECIPIENTS = "errorEmailRecipients";


    @Reference
    private MessageGatewayService messageGatewayService;

    static UploadQueue internalQueueManager;
    static Thread queueManagerThread;

    @Activate
    public void activate(final Map<String, Object> config) {
        apiEndpoint = PropertiesUtil.toString(config.get(API_ENDPOINT), null);
        LOG.debug("configure: apiEndpoint='{}''", apiEndpoint);

        startQueueManager(config);
    }

    private void startQueueManager(final Map<String, Object> config) {
        long maxSize = PropertiesUtil.toLong(config.get(MAX_SIZE), DEFAULT_MAX_SIZE);
        long waitTime = PropertiesUtil.toLong(config.get(WAIT_TIME), DEFAULT_WAIT_TIME);
        int maxRetries = PropertiesUtil.toInteger(config.get(MAX_RETRIES), DEFAULT_MAX_RETRIES);
        String errorEmailRecipients = PropertiesUtil.toString(config.get(ERROR_EMAIL_RECIPIENTS), "");

        if (internalQueueManager == null) {
            internalQueueManager = new UploadQueue(
                maxSize,
                waitTime,
                maxRetries,
                apiEndpoint,
                errorEmailRecipients,
                messageGatewayService,
                null);
        } else {
            internalQueueManager = new UploadQueue(
                maxSize,
                waitTime,
                maxRetries,
                apiEndpoint,
                errorEmailRecipients,
                messageGatewayService,
                internalQueueManager.getPendingBatches());
        }
        queueManagerThread = new Thread(internalQueueManager);
        queueManagerThread.start();
        LOG.debug("Initializing QueueManager, Thread id: {} ", queueManagerThread.getId());
    }

    @Override
    public void updateContentScore(final Page page) throws RepositoryException {
        ValueMap pageProperties = page.getProperties();

        if (hasNoScores(pageProperties)) {
            // Nothing to update
            return;
        }

        String pageId = page.getPath();

        ContentScoreUpdateRequest request = new ContentScoreUpdateRequest();
        request.setUri(pageId);
        request.setScore(createScore(pageProperties));

        sendUpdateRequest(request);
        setContentScoreUpdatedDate(page);
    }

    @VisibleForTesting
    boolean hasNoScores(final ValueMap pageProperties) {
        return pageProperties.get(ScoreType.UNAWARE.getPropertyName()) == null
            && pageProperties.get(ScoreType.CURIOUS.getPropertyName()) == null
            && pageProperties.get(ScoreType.FOLLOWER.getPropertyName()) == null
            && pageProperties.get(ScoreType.GUIDE.getPropertyName()) == null;
    }

    @VisibleForTesting
    ContentScore createScore(final ValueMap pageProperties) {
        ContentScore contentScore = new ContentScore();

        contentScore.setUnaware(getScore(pageProperties, ScoreType.UNAWARE.getPropertyName()));
        contentScore.setCurious(getScore(pageProperties, ScoreType.CURIOUS.getPropertyName()));
        contentScore.setFollower(getScore(pageProperties, ScoreType.FOLLOWER.getPropertyName()));
        contentScore.setGuide(getScore(pageProperties, ScoreType.GUIDE.getPropertyName()));

        String scoreConfidence = pageProperties.get("scoreConfidence", String.class);
        Preconditions.checkNotNull(scoreConfidence, "scoreConfidence is required");
        contentScore.setConfidence(new BigDecimal(scoreConfidence));

        return contentScore;
    }

    private int getScore(final ValueMap pageProperties, final String scoreName) {
        String score = pageProperties.get(scoreName, String.class);
        Preconditions.checkNotNull(score, scoreName + " is required");
        return Integer.parseInt(score);
    }

    private void sendUpdateRequest(final ContentScoreUpdateRequest request) {
        if (!queueManagerThread.isAlive()) {
            LOG.debug("Thread is dead. Starting...");
            queueManagerThread.start();
        }
        internalQueueManager.put(request);
        LOG.debug("Page {} added to the queue", request.getUri());
    }

    @VisibleForTesting
    void setContentScoreUpdatedDate(final Page page) throws RepositoryException {
        Node node = page.getContentResource().adaptTo(Node.class);
        try {
            node.getSession().refresh(true);
            node.setProperty(CONTENT_SCORE_UPDATED, Calendar.getInstance());
            node.getSession().save();
        } catch (RepositoryException e) {
            node.getSession().refresh(false);
            node.setProperty(CONTENT_SCORE_UPDATED, Calendar.getInstance());
            node.getSession().save();
        }
    }

    @Deactivate
    void deactivate() {
        internalQueueManager.stop();
    }
}
