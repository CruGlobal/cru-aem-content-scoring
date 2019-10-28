package org.cru.contentscoring.core.service.impl;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.cru.contentscoring.core.models.ContentScoreUpdateRequest;
import org.cru.contentscoring.core.queue.UploadQueue;
import org.cru.contentscoring.core.service.ContentScoreUpdateService;
import org.cru.contentscoring.core.util.ExperienceFragmentUtil;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.mailer.MessageGatewayService;
import com.day.cq.tagging.Tag;
import com.day.cq.wcm.api.Page;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

@Component(service = ContentScoreUpdateService.class, configurationPolicy = ConfigurationPolicy.REQUIRE)
@Designate(ocd = ContentScoreUpdateServiceImpl.Config.class)
public class ContentScoreUpdateServiceImpl implements ContentScoreUpdateService {

    @ObjectClassDefinition
    @interface Config {
        @AttributeDefinition(
                name = "API Endpoint",
                description = "The endpoint to which service requests can be submitted.")
        String apiEndpoint();

        @AttributeDefinition(
                name = "Wait Time",
                description = "Time (in milliseconds) to wait between sending.")
        long waitTime();

        @AttributeDefinition(
                name = "Max Retries",
                description = "Max number of retries for unsuccessful attempts.")
        int maxRetries();

        @AttributeDefinition(
                name = "Error Email Recipients",
                description = "When max number of retries is reached, an email will be sent. "
                        + "Write recipients here separated by comma.")
        String errorEmailRecipients();

        @AttributeDefinition(
                name = "URL Mapper Endpoint",
                description = "URL mapper endpoint on the publishers")
        String urlMapperEndpoint();
    }

    private static final Logger LOG = LoggerFactory.getLogger(ContentScoreUpdateServiceImpl.class);

    static final String CONTENT_SCORE_UPDATED = "contentScoreLastUpdated";

    static final String API_ENDPOINT = "apiEndpoint";
    private String apiEndpoint;

    private static final Long DEFAULT_WAIT_TIME = 5L * 1000L;
    static final String WAIT_TIME = "waitTime";

    private static final Integer DEFAULT_MAX_RETRIES = 3;
    static final String MAX_RETRIES = "maxRetries";

    static final String ERROR_EMAIL_RECIPIENTS = "errorEmailRecipients";

    static final String URL_MAPPER_ENDPOINT = "urlMapperEndpoint";

    private static final String API_KEY_LOCATION = "AEM_CONTENT_SCORING_API_KEY";
    static final String VANITY_PATH = "sling:vanityPath";
    static final String VANITY_REDIRECT = "sling:redirect";
    private UUID apiKey;
    private String urlMapperEndpoint;

    Client client;


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

        urlMapperEndpoint = (String) config.get(URL_MAPPER_ENDPOINT);
        Preconditions.checkNotNull(urlMapperEndpoint, "URL Mapper Endpoint must be configured in aem_osgi_config.");

        startQueueManager(config);
        client = ClientBuilder.newBuilder().build().register(JacksonJsonProvider.class);
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
        Resource jcrContent = page.getContentResource();
        if (ExperienceFragmentUtil.isExperienceFragment(jcrContent)
            || ExperienceFragmentUtil.isExperienceFragmentVariation(jcrContent)) {
            return;
        }

        int score = getScore(page);
        if (score == -1) {
            return;
        }

        Set<String> urlsToSend = determinePageUrlsToSend(page);

        for (String url : urlsToSend) {
            handleRequest(page, url, score);
        }
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
    Set<String> determinePageUrlsToSend(final Page page) {
        Set<String> pathsToSend = new HashSet<>();
        pathsToSend.add(page.getPath());

        ValueMap properties = page.getContentResource().adaptTo(ValueMap.class);
        if (properties != null) {
            boolean redirectVanity = properties.get(VANITY_REDIRECT, false);

            if (!redirectVanity) {
                String[] vanityPaths = properties.get(VANITY_PATH, new String[0]);
                pathsToSend.addAll(Arrays.asList(vanityPaths));
            }
            // otherwise, the vanity URL is never actually landed upon,
            // since it redirects to the page path.
        }

        return getUrlsFromPaths(pathsToSend);
    }

    private Set<String> getUrlsFromPaths(final Set<String> paths) {
        WebTarget webTarget = client.target(urlMapperEndpoint);

        for (String path : paths) {
            webTarget = webTarget.queryParam("path", path);
        }
        Response response = webTarget
            .request()
            .get();
        return response.readEntity(new GenericType<Set<String>>(){});
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
