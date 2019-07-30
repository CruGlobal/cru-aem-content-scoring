package org.cru.contentscoring.core.service.impl;

import com.day.cq.commons.Externalizer;
import com.day.cq.tagging.Tag;
import com.day.cq.wcm.api.Page;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.cru.contentscoring.core.models.ContentScoreUpdateRequest;
import org.cru.contentscoring.core.queue.UploadQueue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.cru.contentscoring.core.service.impl.ContentScoreUpdateServiceImpl.API_ENDPOINT;
import static org.cru.contentscoring.core.service.impl.ContentScoreUpdateServiceImpl.CONTENT_SCORE_UPDATED;
import static org.cru.contentscoring.core.service.impl.ContentScoreUpdateServiceImpl.ERROR_EMAIL_RECIPIENTS;
import static org.cru.contentscoring.core.service.impl.ContentScoreUpdateServiceImpl.EXTERNALIZERS;
import static org.cru.contentscoring.core.service.impl.ContentScoreUpdateServiceImpl.MAX_RETRIES;
import static org.cru.contentscoring.core.service.impl.ContentScoreUpdateServiceImpl.WAIT_TIME;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ContentScoreUpdateServiceImplTest {
    private static final String UNAWARE_SCORE = "1";
    private static final String HTML_EXTENSION = ".html";

    private static final Map<String, String> CONFIGURED_EXTERNALIZERS = buildExternalizers();

    private Page page;

    @InjectMocks
    private ContentScoreUpdateServiceImpl updateService;

    @Before
    public void setup() throws Exception {
        String pagePath = "/content/test/us/en/page-path";
        page = mockPage(pagePath, pagePath, "https://page.com" + pagePath);
    }

    @Test
    public void testActivation() {
        Map<String, Object> config = Maps.newHashMap();
        config.put(API_ENDPOINT, "http://somewhere-out.there.com");
        config.put(WAIT_TIME, 10000L);
        config.put(MAX_RETRIES, 5);
        config.put(ERROR_EMAIL_RECIPIENTS, "some.email@example.com,another.email@example.com");

        List<String> configExternalizers = Lists.newArrayList();
        configExternalizers.add("/content/test/us/en=test-publish");
        configExternalizers.add("/content/foo/us/en=foo-publish");
        config.put(EXTERNALIZERS, configExternalizers);

        updateService.activate(config);
        assertThat(ContentScoreUpdateServiceImpl.internalQueueManager, is(not(nullValue())));
        assertThat(ContentScoreUpdateServiceImpl.queueManagerThread, is(not(nullValue())));

        assertThat(updateService.externalizersConfigs, is(not(nullValue())));
        assertThat(updateService.externalizersConfigs, is(equalTo(CONFIGURED_EXTERNALIZERS)));
    }

    private static Map<String, String> buildExternalizers() {
        Map<String, String> externalizers = Maps.newHashMap();
        externalizers.put("/content/test/us/en", "test-publish");
        externalizers.put("/content/foo/us/en", "foo-publish");

        return externalizers;
    }

    @Test
    public void testPageHasScore() {
        Tag scoreTag = mock(Tag.class);
        when(scoreTag.getTagID()).thenReturn(SyncScoreServiceImpl.SCALE_OF_BELIEF_TAG_PREFIX + UNAWARE_SCORE);
        when(page.getTags()).thenReturn(new Tag[] {scoreTag});

        assertThat(updateService.getScore(page), is(Integer.parseInt(UNAWARE_SCORE)));
    }

    @Test
    public void testPageMissingScore() {
        when(page.getTags()).thenReturn(new Tag[0]);
        assertThat(updateService.getScore(page), is(-1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidScore() {
        Tag scoreTag = mock(Tag.class);
        when(scoreTag.getTagID()).thenReturn(SyncScoreServiceImpl.SCALE_OF_BELIEF_TAG_PREFIX + 100);
        when(page.getTags()).thenReturn(new Tag[] {scoreTag});

        updateService.getScore(page);
    }

    @Test
    public void testSetContentScoreUpdatedDate() throws RepositoryException {
        Page mockPage = mock(Page.class);
        Resource mockResource = mock(Resource.class);
        when(mockPage.getContentResource()).thenReturn(mockResource);

        Node mockNode = mock(Node.class);
        when(mockResource.adaptTo(Node.class)).thenReturn(mockNode);

        Session mockSession = mock(Session.class);
        doNothing().when(mockSession).refresh(true);
        doNothing().when(mockSession).save();
        when(mockNode.getSession()).thenReturn(mockSession);

        updateService.setContentScoreUpdatedDate(mockPage);

        verify(mockNode, times(1)).setProperty(eq(CONTENT_SCORE_UPDATED), any(Calendar.class));
    }

    @Test
    public void testSetContentScoreUpdateDateRetry() throws RepositoryException {
        Page mockPage = mock(Page.class);
        Resource mockResource = mock(Resource.class);
        when(mockPage.getContentResource()).thenReturn(mockResource);

        Node mockNode = mock(Node.class);
        when(mockResource.adaptTo(Node.class)).thenReturn(mockNode);

        Session mockSession = mock(Session.class);
        doThrow(new RepositoryException()).when(mockSession).refresh(true);
        when(mockNode.getSession()).thenReturn(mockSession);

        updateService.setContentScoreUpdatedDate(mockPage);

        verify(mockSession).refresh(false);
        verify(mockNode).setProperty(eq(CONTENT_SCORE_UPDATED), any(Calendar.class));
        verify(mockSession).save();
    }

    @Test
    public void testGetVanityUrl() throws Exception {
        String vanityPath = "/vanity-url";
        String pagePath = "/content/test/us/en/page-path";

        Page page = mockPage(pagePath, vanityPath, "https://vanity.com/content/test/us/en/" + vanityPath);
        when(page.getVanityUrl()).thenReturn(vanityPath);

        String vanityUrl = updateService.getPageUrl(page, true);
        assertThat(vanityUrl, is(equalTo("https://vanity.com/" + vanityPath)));
    }

    @Test
    public void testGetPageUrl() throws Exception {
        String pagePath = "/content/test/us/en/page-path";
        Page page = mockPage(pagePath, pagePath, "https://page.com" + pagePath);

        String pageUrl = updateService.getPageUrl(page, false);
        assertThat(pageUrl, is(equalTo("https://page.com" + pagePath + HTML_EXTENSION)));
    }

    @Test
    public void testDoNotSkipPageUrl() throws Exception {
        String vanityPath = "/vanity-url";
        String pagePath = "/content/test/us/en/page-path";
        String prefix = "https://vanity.com/content/test/us/en";

        Page page = mockPage(pagePath, vanityPath, prefix + vanityPath);
        when(page.getVanityUrl()).thenReturn(vanityPath);

        String vanityUrl = updateService.getPageUrl(page, false);
        assertThat(vanityUrl, is(equalTo(prefix + vanityPath + HTML_EXTENSION)));
    }

    @Test
    public void testSkipVanityUrl() throws Exception {
        String pagePath = "/content/test/us/en/page-path";
        Page page = mockPage(pagePath, pagePath, "https://page.com" + pagePath);

        String pageUrl = updateService.getPageUrl(page, true);
        assertThat(pageUrl, is(nullValue()));
    }

    @Test
    public void testPageWithVanityUrlSendsBothUrls() throws Exception {
        initializeQueue();
        String vanityPath = "/vanity-url";
        String pagePath = "/content/test/us/en/page-path";
        String prefix = "https://vanity.com/content/test/us/en";

        Page page = mockPage(pagePath, vanityPath, prefix + vanityPath);
        when(page.getVanityUrl()).thenReturn(vanityPath);

        updateService.updateContentScore(page);
        List<ContentScoreUpdateRequest> pending =
            ContentScoreUpdateServiceImpl.internalQueueManager.getPendingBatches();

        assertThat(pending, is(not(nullValue())));
        assertThat(pending.size(), is(equalTo(2)));

        int correctPaths = 0;

        for (ContentScoreUpdateRequest request : pending) {
            if (request.getUri().equals("https://vanity.com" + vanityPath)
                || request.getUri().equals(prefix + vanityPath + HTML_EXTENSION)) {
                correctPaths++;
            }
        }

        assertThat(correctPaths, is(equalTo(2)));
    }

    @Test
    public void testPageWithoutVanityUrlSendsOneUrl() throws Exception {
        initializeQueue();
        String pagePath = "/content/test/us/en/page-path";
        String prefix = "https://page.com";

        Page page = mockPage(pagePath, pagePath, prefix + pagePath);

        updateService.updateContentScore(page);
        List<ContentScoreUpdateRequest> pending =
            ContentScoreUpdateServiceImpl.internalQueueManager.getPendingBatches();

        ContentScoreUpdateRequest request = Iterables.getOnlyElement(pending);
        assertThat(request.getUri(), is(equalTo(prefix + pagePath + HTML_EXTENSION)));
    }

    private void initializeQueue() {
        ContentScoreUpdateServiceImpl.internalQueueManager = new UploadQueue(
            2 * 60 * 1000L,
            1,
            "http://somewhere.com/endpoint",
            UUID.randomUUID(),
            "",
            null,
            null);
        if (ContentScoreUpdateServiceImpl.queueManagerThread == null) {
            ContentScoreUpdateServiceImpl.queueManagerThread =
                new Thread(ContentScoreUpdateServiceImpl.internalQueueManager);
        }
    }

    private Page mockPage(final String pagePath, final String externalizerPath, final String externalLink)
        throws Exception {

        Page page = mock(Page.class);
        when(page.getPath()).thenReturn(pagePath);

        Resource resource = mock(Resource.class);
        when(page.adaptTo(Resource.class)).thenReturn(resource);

        Resource contentResource = mock(Resource.class);
        Node contentNode = mock(Node.class);
        when(contentResource.adaptTo(Node.class)).thenReturn(contentNode);
        Session session = mock(Session.class);
        when(contentNode.getSession()).thenReturn(session);
        when(page.getContentResource()).thenReturn(contentResource);

        Tag scoreTag = mock(Tag.class);
        when(scoreTag.getTagID()).thenReturn(SyncScoreServiceImpl.SCALE_OF_BELIEF_TAG_PREFIX + "6");
        when(page.getTags()).thenReturn(new Tag[] {scoreTag});

        ResourceResolver resolver = mock(ResourceResolver.class);
        when(resource.getResourceResolver()).thenReturn(resolver);

        Externalizer externalizer = mock(Externalizer.class);
        when(resolver.adaptTo(Externalizer.class)).thenReturn(externalizer);

        when(externalizer.externalLink(resolver, "test-publish", externalizerPath))
            .thenReturn(externalLink);

        updateService.externalizersConfigs = CONFIGURED_EXTERNALIZERS;

        return page;
    }

    @Test
    public void testGetExistingPublishConfiguration() {
        updateService.externalizersConfigs = CONFIGURED_EXTERNALIZERS;

        String path = "/content/foo/us/en/some-page";
        String[] configuration = updateService.getPublishConfiguration(path);

        assertThat(configuration, is(not(nullValue())));
        assertThat(configuration[0], is(equalTo("/content/foo/us/en")));
        assertThat(configuration[1], is(equalTo("foo-publish")));
    }

    @Test(expected = IllegalStateException.class)
    public void testGetNonExistingPublishConfiguration() {
        updateService.externalizersConfigs = CONFIGURED_EXTERNALIZERS;

        String path = "/content/fail/us/en/some-page";
        updateService.getPublishConfiguration(path);
    }
}
