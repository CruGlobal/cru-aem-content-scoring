package org.cru.contentscoring.core.service.impl;

import com.day.cq.commons.Externalizer;
import com.day.cq.mailer.MessageGatewayService;
import com.day.cq.tagging.Tag;
import com.day.cq.wcm.api.Page;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.cru.contentscoring.core.models.ContentScoreUpdateRequest;
import org.cru.contentscoring.core.queue.UploadQueue;
import org.cru.contentscoring.core.service.ContentScoreUpdateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.Calendar;
import java.util.Map;
import java.util.UUID;

@Component
@Service(ContentScoreUpdateService.class)
public class ContentScoreUpdateServiceImpl implements ContentScoreUpdateService {
    private static final Logger LOG = LoggerFactory.getLogger(ContentScoreUpdateServiceImpl.class);

    static final String CONTENT_SCORE_UPDATED = "contentScoreLastUpdated";

    @Property(label = "API Endpoint", description = "The endpoint to which service requests can be submitted.")
    static final String API_ENDPOINT = "apiEndpoint";
    private String apiEndpoint;

    @Property(
        label = "Externalizers",
        description = "List of externalizers needed for content scoring",
        cardinality = Integer.MAX_VALUE)
    static final String EXTERNALIZERS = "externalizers";
    Map<String, String> externalizersConfigs;

    private static final Long DEFAULT_WAIT_TIME = 5L * 1000L;
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

    private static final String API_KEY_LOCATION = "AEM_CONTENT_SCORING_API_KEY";
    private UUID apiKey;


    @Reference
    private MessageGatewayService messageGatewayService;

    static UploadQueue internalQueueManager;
    static Thread queueManagerThread;

    @Activate
    public void activate(final Map<String, Object> config) {
        apiEndpoint = PropertiesUtil.toString(config.get(API_ENDPOINT), null);
        LOG.debug("configure: apiEndpoint='{}''", apiEndpoint);

        String apiKeyString = System.getenv(API_KEY_LOCATION);
        Preconditions.checkNotNull(apiKeyString, "API Key is null!");
        apiKey = UUID.fromString(apiKeyString);

        externalizersConfigs = PropertiesUtil.toMap(config.get(EXTERNALIZERS), null);

        startQueueManager(config);
    }

    private void startQueueManager(final Map<String, Object> config) {
        long waitTime = PropertiesUtil.toLong(config.get(WAIT_TIME), DEFAULT_WAIT_TIME);
        int maxRetries = PropertiesUtil.toInteger(config.get(MAX_RETRIES), DEFAULT_MAX_RETRIES);
        String errorEmailRecipients = PropertiesUtil.toString(config.get(ERROR_EMAIL_RECIPIENTS), "");

        if (internalQueueManager == null) {
            internalQueueManager = new UploadQueue(
                waitTime,
                maxRetries,
                apiEndpoint,
                apiKey,
                errorEmailRecipients,
                messageGatewayService,
                null);
        } else {
            internalQueueManager = new UploadQueue(
                waitTime,
                maxRetries,
                apiEndpoint,
                apiKey,
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
        int score = getScore(page);
        if (score == -1) {
            return;
        }

        handleRequest(page, getPageUrl(page, false), score); // send the real URL
        handleRequest(page, getPageUrl(page, true), score);  // send the vanity if it has one
    }

    private void handleRequest(final Page page, final String pageUrl, final int score) throws RepositoryException {
        if (pageUrl != null) {
            ContentScoreUpdateRequest request = new ContentScoreUpdateRequest();
            request.setUri(pageUrl);
            request.setScore(score);

            sendUpdateRequest(request);
            setContentScoreUpdatedDate(page);
        }
    }

    @VisibleForTesting
    int getScore(final Page page) {
        String scoreString = null;

        Tag[] tags = page.getTags();
        for (Tag tag : tags) {
            if (tag.getTagID().startsWith(SyncScoreServiceImpl.SCALE_OF_BELIEF_TAG_PREFIX)) {
                scoreString = tag.getTagID().replace(SyncScoreServiceImpl.SCALE_OF_BELIEF_TAG_PREFIX, "");
                break;
            }
        }
        if (scoreString == null) {
            // Score is required for update
            return -1;
        }

        int score = Integer.parseInt(scoreString);
        Preconditions.checkArgument(
            score >= 0 && score <= 8,
            "Score must be between 0 and 8, but is " + score);

        return score;
    }

    @VisibleForTesting
    String getPageUrl(final Page page, final boolean useVanity) {
        String path = page.getVanityUrl();
        boolean vanityUrl = !Strings.isNullOrEmpty(path);

        if (!vanityUrl) {
            path = page.getPath();
        }

        Resource resource = page.adaptTo(Resource.class);
        ResourceResolver resolver = resource.getResourceResolver();
        Externalizer externalizer = resolver.adaptTo(Externalizer.class);

        String[] publishConfiguration = getPublishConfiguration(page.getPath());
        String domain = publishConfiguration[1];
        String pageUrl = externalizer.externalLink(resolver, domain, path);

        if (useVanity && vanityUrl) {
            pageUrl = pageUrl.replace(publishConfiguration[0], "");
        } else if ((!useVanity && vanityUrl) || (useVanity && !vanityUrl)) {
            return null;
        } else {
            pageUrl += ".html";
        }
        return pageUrl;
    }

    @VisibleForTesting
    String[] getPublishConfiguration(final String path) {
        if (externalizersConfigs != null) {
            for (String key : externalizersConfigs.keySet()) {
                if(path.contains(key)) {
                    return new String[] {key, externalizersConfigs.get(key)};
                }
            }
        }

        IllegalStateException exception = new IllegalStateException("Externalizer not found for " + path);
        LOG.error("Failed to find Externalizer", exception);
        throw exception;
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
