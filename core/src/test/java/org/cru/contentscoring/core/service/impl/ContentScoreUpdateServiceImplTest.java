package org.cru.contentscoring.core.service.impl;

import com.day.cq.commons.Externalizer;
import com.day.cq.tagging.Tag;
import com.day.cq.wcm.api.Page;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.cru.contentscoring.core.models.ContentScoreUpdateRequest;
import org.cru.contentscoring.core.queue.UploadQueue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.cru.contentscoring.core.service.impl.ContentScoreUpdateServiceImpl.API_ENDPOINT;
import static org.cru.contentscoring.core.service.impl.ContentScoreUpdateServiceImpl.CONTENT_SCORE_UPDATED;
import static org.cru.contentscoring.core.service.impl.ContentScoreUpdateServiceImpl.ERROR_EMAIL_RECIPIENTS;
import static org.cru.contentscoring.core.service.impl.ContentScoreUpdateServiceImpl.EXTERNALIZERS;
import static org.cru.contentscoring.core.service.impl.ContentScoreUpdateServiceImpl.MAX_RETRIES;
import static org.cru.contentscoring.core.service.impl.ContentScoreUpdateServiceImpl.URL_MAPPER_ENDPOINT;
import static org.cru.contentscoring.core.service.impl.ContentScoreUpdateServiceImpl.VANITY_PATH;
import static org.cru.contentscoring.core.service.impl.ContentScoreUpdateServiceImpl.VANITY_REDIRECT;
import static org.cru.contentscoring.core.service.impl.ContentScoreUpdateServiceImpl.WAIT_TIME;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
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
    private static final String DOMAIN = "test-publish";

    private static final Map<String, String> CONFIGURED_EXTERNALIZERS = buildExternalizers();

    private Page page;

    @Mock
    private Externalizer externalizer;

    @Mock
    private ResourceResolver resolver;

    @InjectMocks
    private ContentScoreUpdateServiceImpl updateService;

    @Before
    public void setup() throws Exception {
        String site = "https://page.com";
        String pagePath = "/content/test/us/en/page-path";
        page = mockPage(site, pagePath, pagePath, site + pagePath);
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

        String urlMapperEndpoint = "http://local.cru.org:4503/bin/cru/url/mapper.txt";
        config.put(URL_MAPPER_ENDPOINT, urlMapperEndpoint);

        updateService.activate(config);
        assertThat(ContentScoreUpdateServiceImpl.internalQueueManager, is(not(nullValue())));
        assertThat(ContentScoreUpdateServiceImpl.queueManagerThread, is(not(nullValue())));

        assertThat(updateService.externalizersConfigs, is(not(nullValue())));
        assertThat(updateService.externalizersConfigs, is(equalTo(CONFIGURED_EXTERNALIZERS)));
    }

    private static Map<String, String> buildExternalizers() {
        Map<String, String> externalizers = Maps.newHashMap();
        externalizers.put("/content/test/us/en", DOMAIN);
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
    public void testDeterminePageUrlsToSendNoVanities() throws Exception {
        String site = "https://page.com";
        String pagePath = "/content/test/us/en/page-path";
        Page page = mockPage(site, pagePath, pagePath, site + pagePath);

        mockResponse(Sets.newHashSet(site + pagePath + HTML_EXTENSION));

        Set<String> urlsToSend = updateService.determinePageUrlsToSend(page);
        String url = Iterables.getOnlyElement(urlsToSend);
        assertThat(url, is(equalTo(site + pagePath + HTML_EXTENSION)));
    }

    @Test
    public void testDeterminePageUrlsToSendOneVanity() throws Exception {
        String site = "https://vanity.com";
        String vanityPath = "/vanity-url";
        String pagePath = "/content/test/us/en/page-path";

        Page page = mockPage(site, pagePath, vanityPath, site + vanityPath);
        when(page.getVanityUrl()).thenReturn(vanityPath);

        Map<String, Object> properties = new HashMap<>();
        properties.put(VANITY_PATH, new String[] {vanityPath});
        ValueMap valueMap = new ValueMapDecorator(properties);
        Resource contentResource = page.getContentResource();
        when(contentResource.adaptTo(ValueMap.class)).thenReturn(valueMap);

        mockResponse(Sets.newHashSet(site + pagePath + HTML_EXTENSION, site + vanityPath));

        Set<String> urlsToSend = updateService.determinePageUrlsToSend(page);
        assertThat(urlsToSend.size(), is(equalTo(2)));
        assertThat(urlsToSend, hasItems(
            site + pagePath + HTML_EXTENSION,
            site + vanityPath));
    }

    @Test
    public void testDeterminePageUrlsToSendVanityRedirect() throws Exception {
        String site = "https://vanity.com";
        String vanityPath = "/vanity-url";
        String pagePath = "/content/test/us/en/page-path";

        Page page = mockPage(site, pagePath, vanityPath, site + vanityPath);
        when(page.getVanityUrl()).thenReturn(vanityPath);

        Map<String, Object> properties = new HashMap<>();
        properties.put(VANITY_PATH, new String[] {vanityPath});
        properties.put(VANITY_REDIRECT, Boolean.TRUE);

        ValueMap valueMap = new ValueMapDecorator(properties);
        Resource contentResource = page.getContentResource();
        when(contentResource.adaptTo(ValueMap.class)).thenReturn(valueMap);

        mockResponse(Sets.newHashSet(site + pagePath + HTML_EXTENSION));

        Set<String> urlsToSend = updateService.determinePageUrlsToSend(page);
        assertThat(urlsToSend.size(), is(equalTo(1)));
        assertThat(Iterables.getOnlyElement(urlsToSend), is(equalTo(site + pagePath + HTML_EXTENSION)));
    }

    @Test
    public void testDeterminePageUrlsToSendMultipleVanities() throws Exception {
        String site = "https://vanity.com";
        String vanityPath = "/vanity-url";
        String secondVanity = "/content/test/us/en/vanity-url";
        String pagePath = "/content/test/us/en/page-path";

        Page page = mockPage(site, pagePath, vanityPath, site + vanityPath);
        when(page.getVanityUrl()).thenReturn(vanityPath);

        when(externalizer.externalLink(resolver, DOMAIN, secondVanity)).thenReturn(site + secondVanity);

        Map<String, Object> properties = new HashMap<>();
        properties.put(VANITY_PATH, new String[] {vanityPath, secondVanity});
        ValueMap valueMap = new ValueMapDecorator(properties);
        Resource contentResource = page.getContentResource();
        when(contentResource.adaptTo(ValueMap.class)).thenReturn(valueMap);

        mockResponse(Sets.newHashSet(site + pagePath + HTML_EXTENSION, site + vanityPath, site + secondVanity));

        Set<String> urlsToSend = updateService.determinePageUrlsToSend(page);
        assertThat(urlsToSend.size(), is(equalTo(3)));
        assertThat(urlsToSend, hasItems(
            site + pagePath + HTML_EXTENSION,
            site + vanityPath,
            site + secondVanity));
    }

    @Test
    public void testPageWithVanityUrlSendsBothUrls() throws Exception {
        initializeQueue();
        String site = "https://vanity.com";
        String vanityPath = "/vanity-url";
        String pagePath = "/content/test/us/en/page-path";

        Page page = mockPage(site, pagePath, vanityPath, site + vanityPath);
        when(page.getVanityUrl()).thenReturn(vanityPath);

        Map<String, Object> properties = new HashMap<>();
        properties.put(VANITY_PATH, new String[] {vanityPath});
        ValueMap valueMap = new ValueMapDecorator(properties);
        Resource contentResource = page.getContentResource();
        when(contentResource.adaptTo(ValueMap.class)).thenReturn(valueMap);

        mockResponse(Sets.newHashSet(site + pagePath + HTML_EXTENSION, site + vanityPath));

        updateService.updateContentScore(page);
        List<ContentScoreUpdateRequest> pending =
            ContentScoreUpdateServiceImpl.internalQueueManager.getPendingBatches();

        assertThat(pending, is(not(nullValue())));
        assertThat(pending.size(), is(equalTo(2)));

        int correctPaths = 0;

        for (ContentScoreUpdateRequest request : pending) {
            if (request.getUri().equals(site + vanityPath)
                || request.getUri().equals(site + pagePath + HTML_EXTENSION)) {
                correctPaths++;
            }
        }

        assertThat(correctPaths, is(equalTo(2)));
    }

    @Test
    public void testPageWithoutVanityUrlSendsOneUrl() throws Exception {
        initializeQueue();
        String pagePath = "/content/test/us/en/page-path";
        String site = "https://page.com";

        Page page = mockPage(site, pagePath, pagePath, site + pagePath);

        mockResponse(Sets.newHashSet(site + pagePath + HTML_EXTENSION));

        updateService.updateContentScore(page);
        List<ContentScoreUpdateRequest> pending =
            ContentScoreUpdateServiceImpl.internalQueueManager.getPendingBatches();

        ContentScoreUpdateRequest request = Iterables.getOnlyElement(pending);
        assertThat(request.getUri(), is(equalTo(site + pagePath + HTML_EXTENSION)));
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

    @SuppressWarnings("unchecked")
    private void mockResponse(Set<String> results) {
        Response response = mock(Response.class);
        when(response.readEntity(any(GenericType.class))).thenReturn(results);

        Invocation.Builder builder = mock(Invocation.Builder.class);
        when(builder.get()).thenReturn(response);

        WebTarget webTarget = mock(WebTarget.class);
        when(webTarget.queryParam(eq("path"), anyString())).thenReturn(webTarget);
        when(webTarget.queryParam("domain", DOMAIN)).thenReturn(webTarget);
        when(webTarget.request()).thenReturn(builder);

        Client client = mock(Client.class);
        when(client.target(anyString())).thenReturn(webTarget);

        ClientBuilder clientBuilder = mock(ClientBuilder.class);
        when(clientBuilder.register(any())).thenReturn(clientBuilder);
        when(clientBuilder.build()).thenReturn(client);
        updateService.clientBuilder = clientBuilder;
    }

    private Page mockPage(
        final String site,
        final String pagePath,
        final String externalizerPath,
        final String externalLink) throws Exception {

        Page page = mock(Page.class);
        when(page.getPath()).thenReturn(pagePath);

        Resource resource = mock(Resource.class);
        when(page.adaptTo(Resource.class)).thenReturn(resource);

        Map<String, Object> properties = new HashMap<>();

        Resource contentResource = mock(Resource.class);
        Node contentNode = mock(Node.class);
        when(contentResource.adaptTo(Node.class)).thenReturn(contentNode);
        when(contentResource.getResourceType()).thenReturn("/Site/components/page/page");
        when(contentResource.getValueMap()).thenReturn(new ValueMapDecorator(properties));

        Session session = mock(Session.class);
        when(contentNode.getSession()).thenReturn(session);
        when(page.getContentResource()).thenReturn(contentResource);

        Tag scoreTag = mock(Tag.class);
        when(scoreTag.getTagID()).thenReturn(SyncScoreServiceImpl.SCALE_OF_BELIEF_TAG_PREFIX + "6");
        when(page.getTags()).thenReturn(new Tag[] {scoreTag});

        when(resource.getResourceResolver()).thenReturn(resolver);
        when(resolver.adaptTo(Externalizer.class)).thenReturn(externalizer);

        when(externalizer.externalLink(resolver, DOMAIN, externalizerPath))
            .thenReturn(externalLink);
        when(externalizer.externalLink(resolver, DOMAIN, pagePath))
            .thenReturn(site + pagePath);

        updateService.externalizersConfigs = CONFIGURED_EXTERNALIZERS;

        return page;
    }

    @Test
    public void testGetExistingPublishConfiguration() {
        updateService.externalizersConfigs = CONFIGURED_EXTERNALIZERS;

        String path = "/content/foo/us/en/some-page";
        String domain = updateService.getDomain(path);

        assertThat(domain, is(not(nullValue())));
        assertThat(domain, is(equalTo("foo-publish")));
    }

    @Test(expected = IllegalStateException.class)
    public void testGetNonExistingPublishConfiguration() {
        updateService.externalizersConfigs = CONFIGURED_EXTERNALIZERS;

        String path = "/content/fail/us/en/some-page";
        updateService.getDomain(path);
    }
}
