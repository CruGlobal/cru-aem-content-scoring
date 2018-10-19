package org.cru.contentscoring.core.service.impl;

import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;
import com.day.crx.JcrConstants;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.settings.SlingSettingsService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.Session;
import java.util.Calendar;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SyncScoreServiceImplTest {
    private static final String HOST = "www.someApp.com";
    private static final String VANITY_PATH = "/some/path";
    private static final String PATH_SCOPE = "/content/someApp";
    private static final String ABSOLUTE_PATH = PATH_SCOPE + VANITY_PATH;
    private static final String RESOURCE_PROTOCOL = "https";

    private static final int SCORE = 5;
    
    @Mock
    private SlingSettingsService slingSettingsService;

    @Mock
    private ResourceResolver resourceResolver;

    @Mock
    private Session session;

    @Mock
    private QueryBuilder queryBuilder;

    @InjectMocks
    private SyncScoreServiceImpl syncScoreService;

    private Resource protocolResource;

    @Before
    public void setup() throws Exception {
        when(resourceResolver.adaptTo(Session.class)).thenReturn(session);
        doNothing().when(session).save();

        when(slingSettingsService.getRunModes()).thenReturn(Sets.newHashSet("author", "local"));

        Resource parentMapResource;
        parentMapResource = mock(Resource.class);
        protocolResource = mock(Resource.class);

        when(parentMapResource.getChild(RESOURCE_PROTOCOL)).thenReturn(protocolResource);
        when(resourceResolver.getResource("/etc/map.publish.local")).thenReturn(parentMapResource);

        mockSlingMap(HOST, PATH_SCOPE);
    }

    @Test
    public void testAbsolutePathScoreIsSynced() throws Exception {
        Resource existingResource = mock(Resource.class);
        when(resourceResolver.getResource(ABSOLUTE_PATH)).thenReturn(existingResource);
        Resource contentResource = mock(Resource.class);
        when(existingResource.getChild("jcr:content")).thenReturn(contentResource);

        Map<String, Object> propertyMap = Maps.newHashMap();
        mockForUpdateScore(propertyMap);

        syncScoreService.syncScore(resourceResolver, SCORE, ABSOLUTE_PATH, HOST, RESOURCE_PROTOCOL);

        assertSuccessful(propertyMap);
    }

    @Test
    public void testAbsolutePathNotFound() throws Exception {
        when(resourceResolver.getResource(ABSOLUTE_PATH)).thenReturn(null);

        syncScoreService.syncScore(resourceResolver, SCORE, ABSOLUTE_PATH, HOST, RESOURCE_PROTOCOL);

        assertSkipped();
    }

    @Test
    public void testMissingContentInMap() throws Exception {
        when(resourceResolver.getResource(PATH_SCOPE)).thenReturn(null);

        syncScoreService.syncScore(resourceResolver, SCORE, VANITY_PATH, HOST, RESOURCE_PROTOCOL);

        verify(resourceResolver, never()).getResource(VANITY_PATH);
        assertSkipped();
    }

    @Test
    public void testSuccessfulVanityLookupForRedirectSyncsScore() throws Exception {
        Resource parent = mock(Resource.class);
        when(resourceResolver.getResource(PATH_SCOPE)).thenReturn(parent);

        Resource vanityResource = mock(Resource.class);

        Map<String, Object> baseMap = Maps.newHashMap();
        baseMap.put("sling:target", ABSOLUTE_PATH);
        ValueMap valueMap = new ValueMapDecorator(baseMap);

        when(vanityResource.isResourceType("sling:redirect")).thenReturn(true);
        when(vanityResource.getValueMap()).thenReturn(valueMap);
        when(resourceResolver.getResource(VANITY_PATH)).thenReturn(vanityResource);

        Map<String, Object> propertyMap = Maps.newHashMap();
        mockForUpdateScore(propertyMap);

        syncScoreService.syncScore(resourceResolver, SCORE, VANITY_PATH, HOST, RESOURCE_PROTOCOL);

        assertSuccessful(propertyMap);
    }

    @Test
    public void testSuccessfulVanityLookupForNonRedirectSyncsScore() throws Exception {
        Resource parent = mock(Resource.class);
        when(resourceResolver.getResource(PATH_SCOPE)).thenReturn(parent);

        Resource vanityResource = mock(Resource.class);

        when(vanityResource.isResourceType("sling:redirect")).thenReturn(false);
        when(vanityResource.getPath()).thenReturn(ABSOLUTE_PATH);
        when(resourceResolver.getResource(VANITY_PATH)).thenReturn(vanityResource);

        Map<String, Object> propertyMap = Maps.newHashMap();
        mockForUpdateScore(propertyMap);

        syncScoreService.syncScore(resourceResolver, SCORE, VANITY_PATH, HOST, RESOURCE_PROTOCOL);

        assertSuccessful(propertyMap);
    }

    @Test
    public void testSuccessfulVanityLookupForManualSearchSyncsScore() throws Exception {
        Resource parent = mock(Resource.class);
        when(resourceResolver.getResource(PATH_SCOPE)).thenReturn(parent);

        Hit hit = mock(Hit.class);
        Resource hitResource = mock(Resource.class);
        Resource pageResource = mock(Resource.class);
        when(pageResource.getPath()).thenReturn(ABSOLUTE_PATH);
        when(hitResource.getParent()).thenReturn(pageResource);
        when(hit.getResource()).thenReturn(hitResource);

        mockSearch(hit);

        Map<String, Object> propertyMap = Maps.newHashMap();
        mockForUpdateScore(propertyMap);

        syncScoreService.syncScore(resourceResolver, SCORE, VANITY_PATH, HOST, RESOURCE_PROTOCOL);

        assertSuccessful(propertyMap);
    }

    @Test
    public void testVanityLookupForManualSearchFindsMultipleResults() throws Exception {
        Resource parent = mock(Resource.class);
        when(resourceResolver.getResource(PATH_SCOPE)).thenReturn(parent);

        Hit hit = mockHit(ABSOLUTE_PATH);
        Hit hit2 = mockHit("/content/otherApp/some/path");

        Query query1 = mock(Query.class);
        mockSearch(query1, hit, hit2);

        Query query2 = mock(Query.class);
        mockSearch(query2);
        when(queryBuilder.createQuery(any(PredicateGroup.class), eq(session))).thenReturn(query1).thenReturn(query2);

        Map<String, Object> propertyMap = Maps.newHashMap();
        mockForUpdateScore(propertyMap);

        syncScoreService.syncScore(resourceResolver, SCORE, VANITY_PATH, HOST, RESOURCE_PROTOCOL);

        assertSkipped();
    }

    @Test
    public void testVanityLookupForManualSearchFindsNoResultsNoContentEither() throws Exception {
        Resource parent = mock(Resource.class);
        when(resourceResolver.getResource(PATH_SCOPE)).thenReturn(parent);

        mockSearch();

        Map<String, Object> propertyMap = Maps.newHashMap();
        mockForUpdateScore(propertyMap);

        syncScoreService.syncScore(resourceResolver, SCORE, VANITY_PATH, HOST, RESOURCE_PROTOCOL);

        assertSkipped();
    }

    @Test
    public void testVanityLookupForManualSearchFindsNoResultsButActualPathFound() throws Exception {
        Resource parent = mock(Resource.class);
        when(parent.getPath()).thenReturn(PATH_SCOPE);
        when(resourceResolver.getResource(PATH_SCOPE)).thenReturn(parent);

        Query query1 = mock(Query.class);
        mockSearch(query1);

        Hit hit = mock(Hit.class);
        when(hit.getPath()).thenReturn(ABSOLUTE_PATH);

        Query query2 = mock(Query.class);
        mockSearch(query2, hit);

        when(queryBuilder.createQuery(any(PredicateGroup.class), eq(session))).thenReturn(query1).thenReturn(query2);

        Map<String, Object> propertyMap = Maps.newHashMap();
        mockForUpdateScore(propertyMap);

        syncScoreService.syncScore(resourceResolver, SCORE, VANITY_PATH, HOST, RESOURCE_PROTOCOL);

        assertSuccessful(propertyMap);
    }

    @Test
    public void testMissingConfigButContentExists() throws Exception {
        String resourcePath = "/otherApp/path";
        String pathScope = "/content";
        String jcrPath = pathScope + resourcePath;

        Resource parent = mock(Resource.class);
        when(resourceResolver.getResource(pathScope)).thenReturn(parent);

        Hit hit = mock(Hit.class);
        Resource hitResource = mock(Resource.class);
        Resource pageResource = mock(Resource.class);
        when(pageResource.getPath()).thenReturn(jcrPath);
        when(hitResource.getParent()).thenReturn(pageResource);
        when(hit.getResource()).thenReturn(hitResource);

        mockSearch(hit);

        Map<String, Object> propertyMap = Maps.newHashMap();
        mockForUpdateScore(propertyMap, jcrPath);

        syncScoreService.syncScore(resourceResolver, SCORE, resourcePath, "otherApp.org", RESOURCE_PROTOCOL);

        assertSuccessful(propertyMap, jcrPath);
    }

    @Test
    public void testHomePage() throws Exception {
        String internalRedirect = PATH_SCOPE + "/us/en.html";
        String actualPath = PATH_SCOPE + "/us/en";

        mockSlingMap("www.someApp_com", internalRedirect);

        Map<String, Object> propertyMap = Maps.newHashMap();
        mockForUpdateScore(propertyMap, actualPath);

        syncScoreService.syncScore(resourceResolver, SCORE, "/", HOST, RESOURCE_PROTOCOL);

        assertSuccessful(propertyMap, actualPath);
    }

    @Test
    public void testMultiplePagesFoundDifferentLanguages() throws Exception {
        Resource jcrContent = mock(Resource.class);
        when(jcrContent.getName()).thenReturn(JcrConstants.JCR_CONTENT);

        Resource usChild = mock(Resource.class);
        when(usChild.getName()).thenReturn("us");
        Resource enChild = mock(Resource.class);
        when(enChild.getName()).thenReturn("en");
        when(usChild.getChildren()).thenReturn(Lists.newArrayList(enChild, jcrContent));

        Resource mxChild = mock(Resource.class);
        when(mxChild.getName()).thenReturn("mx");
        Resource esChild = mock(Resource.class);
        when(esChild.getName()).thenReturn("es");
        when(mxChild.getChildren()).thenReturn(Lists.newArrayList(esChild, jcrContent));

        Resource parent = mock(Resource.class);
        when(parent.getChildren()).thenReturn(Lists.newArrayList(usChild, mxChild, jcrContent));
        when(parent.getPath()).thenReturn(PATH_SCOPE);
        when(resourceResolver.getResource(PATH_SCOPE)).thenReturn(parent);

        Query query1 = mock(Query.class);
        mockSearch(query1);

        Hit hit = mock(Hit.class);
        when(hit.getPath()).thenReturn("/content/someApp/us/en/some/path");
        Hit hit2 = mock(Hit.class);
        when(hit2.getPath()).thenReturn("/content/someApp/mx/es/some/path");

        Query query2 = mock(Query.class);
        mockSearch(query2, hit, hit2);
        when(queryBuilder.createQuery(any(PredicateGroup.class), eq(session))).thenReturn(query1).thenReturn(query2);

        Map<String, Object> propertyMap = Maps.newHashMap();
        mockForUpdateScore(propertyMap);

        String internalRedirect = PATH_SCOPE + "/us/en.html";
        String actualPath = PATH_SCOPE + "/us/en" + VANITY_PATH;

        mockSlingMap("www.someApp_com", internalRedirect);
        mockSlingMap(HOST, PATH_SCOPE);
        mockForUpdateScore(propertyMap, actualPath);

        syncScoreService.syncScore(resourceResolver, SCORE, VANITY_PATH, HOST, RESOURCE_PROTOCOL);

        assertSuccessful(propertyMap);
    }

    @Test
    public void testRemoveExtension() {
        String webPath = "/some/path.html";
        String resourcePath = "/some/path";
        assertThat(syncScoreService.removeExtension(webPath), is(equalTo(resourcePath)));
    }

    @Test
    public void testRemoveExtensionOnPathWithoutExtension() {
        String webPath = "/some/path";
        String resourcePath = "/some/path";
        assertThat(syncScoreService.removeExtension(webPath), is(equalTo(resourcePath)));
    }

    private Hit mockHit(final String jcrPath) throws Exception {
        Resource pageResource = mock(Resource.class);
        when(pageResource.getPath()).thenReturn(jcrPath);

        Resource hitResource = mock(Resource.class);
        when(hitResource.getParent()).thenReturn(pageResource);

        Hit hit = mock(Hit.class);
        when(hit.getResource()).thenReturn(hitResource);

        return hit;
    }

    private void mockSearch(final Hit... hits) {
        Query query = mock(Query.class);
        mockSearch(query, hits);
        when(queryBuilder.createQuery(any(PredicateGroup.class), eq(session))).thenReturn(query);
    }

    private void mockSearch(final Query query, final Hit... hits) {
        SearchResult searchResult = mock(SearchResult.class);
        when(searchResult.getHits()).thenReturn(Lists.newArrayList(hits));
        when(query.getResult()).thenReturn(searchResult);
        when(query.getResult()).thenReturn(searchResult);
    }

    private void mockForUpdateScore(final Map<String, Object> propertyMap) throws Exception {
        mockForUpdateScore(propertyMap, ABSOLUTE_PATH);
    }

    private void mockForUpdateScore(final Map<String, Object> propertyMap, final String jcrPath) throws Exception {
        Resource existingResource = mock(Resource.class);
        when(resourceResolver.getResource(jcrPath)).thenReturn(existingResource);
        Resource contentResource = mock(Resource.class);
        when(existingResource.getChild("jcr:content")).thenReturn(contentResource);

        Node contentNode = mock(Node.class);
        when(contentNode.getPath()).thenReturn(jcrPath + "/jcr:content");
        doAnswer(setProperty(propertyMap)).when(contentNode).setProperty(anyString(), any(String.class));
        doAnswer(setProperty(propertyMap)).when(contentNode).setProperty(anyString(), any(Calendar.class));

        when(contentResource.adaptTo(Node.class)).thenReturn(contentNode);
    }

    private void mockSlingMap(final String host, final String internalRedirect) {
        Map<String, Object> baseMap = Maps.newHashMap();
        baseMap.put("sling:internalRedirect", new String[] { internalRedirect });
        ValueMap valueMap = new ValueMapDecorator(baseMap);

        Resource slingMapResource = mock(Resource.class);
        when(slingMapResource.getValueMap()).thenReturn(valueMap);

        when(protocolResource.getChild(host)).thenReturn(slingMapResource);
    }

    private Answer<Property> setProperty(final Map<String, Object> propertyMap) {
        return invocation -> {
            propertyMap.put((String) invocation.getArguments()[0], invocation.getArguments()[1]);
            return null;
        };
    }

    private void assertSuccessful(final Map<String, Object> propertyMap) throws Exception {
        assertSuccessful(propertyMap, ABSOLUTE_PATH);
    }

    private void assertSuccessful(final Map<String, Object> propertyMap, final String jcrPath) throws Exception {

        assertThat(propertyMap.get("score"), is(equalTo(Integer.toString(SCORE))));
        assertThat(propertyMap.get("cq:lastModifiedBy"), is(equalTo("scale-of-belief")));
        assertThat(propertyMap.get("cq:lastModified"), is(notNullValue()));
        assertThat(propertyMap.get("contentScoreLastUpdated"), is(notNullValue()));

        verify(session).save();
    }

    private void assertSkipped() throws Exception {
        verify(session, never()).save();
    }
}
