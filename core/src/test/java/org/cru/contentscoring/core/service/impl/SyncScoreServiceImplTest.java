package org.cru.contentscoring.core.service.impl;

import com.day.cq.tagging.Tag;
import com.day.cq.tagging.TagManager;
import com.day.crx.JcrConstants;
import com.google.common.collect.Maps;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
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

import static org.cru.contentscoring.core.service.impl.SyncScoreServiceImpl.SCALE_OF_BELIEF_TAG_PREFIX;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SyncScoreServiceImplTest {
    private static final String ABSOLUTE_PATH = "/content/someApp/some/path";
    private static final int SCORE = 5;

    @Mock
    private ResourceResolver resourceResolver;

    @Mock
    private TagManager tagManager;

    @Mock
    private Session session;

    @Mock
    private Resource jcrContent;

    @InjectMocks
    private SyncScoreServiceImpl syncScoreService;

    @Before
    public void setup() throws Exception {
        when(resourceResolver.adaptTo(Session.class)).thenReturn(session);
        doNothing().when(session).save();

        when(jcrContent.getName()).thenReturn(JcrConstants.JCR_CONTENT);
        when(resourceResolver.adaptTo(TagManager.class)).thenReturn(tagManager);
    }

    @Test
    public void testScoreIsSynced() throws Exception {
        Resource resource = mock(Resource.class);
        when(resourceResolver.getResource(ABSOLUTE_PATH)).thenReturn(resource);
        when(tagManager.getTags(jcrContent)).thenReturn(new Tag[0]);

        Tag scoreTag = mock(Tag.class);
        when(scoreTag.getTagID()).thenReturn(SCALE_OF_BELIEF_TAG_PREFIX + SCORE);
        when(tagManager.resolve(SCALE_OF_BELIEF_TAG_PREFIX + SCORE)).thenReturn(scoreTag);

        Map<String, Object> propertyMap = Maps.newHashMap();
        mockForUpdateScore(resource, propertyMap);

        syncScoreService.syncScore(resourceResolver, SCORE, ABSOLUTE_PATH);

        assertSuccessful(propertyMap, new Tag[] { scoreTag });
    }

    @Test
    public void testScoreIsSyncedWithExistingTags() throws Exception {
        Resource resource = mock(Resource.class);
        when(resourceResolver.getResource(ABSOLUTE_PATH)).thenReturn(resource);

        Tag existingTag = mock(Tag.class);
        when(existingTag.getTagID()).thenReturn("namespace:someTag/1");
        when(tagManager.getTags(jcrContent)).thenReturn(new Tag[] { existingTag });

        Tag scoreTag = mock(Tag.class);
        when(scoreTag.getTagID()).thenReturn(SCALE_OF_BELIEF_TAG_PREFIX + SCORE);
        when(tagManager.resolve(SCALE_OF_BELIEF_TAG_PREFIX + SCORE)).thenReturn(scoreTag);

        Map<String, Object> propertyMap = Maps.newHashMap();
        mockForUpdateScore(resource, propertyMap);

        syncScoreService.syncScore(resourceResolver, SCORE, ABSOLUTE_PATH);

        assertSuccessful(propertyMap, new Tag[] { existingTag, scoreTag });
    }

    @Test
    public void testScoreIsSyncedWithExistingTagsAndPriorScoreTag() throws Exception {
        Resource resource = mock(Resource.class);
        when(resourceResolver.getResource(ABSOLUTE_PATH)).thenReturn(resource);

        Tag existingTag = mock(Tag.class);
        when(existingTag.getTagID()).thenReturn("namespace:someTag/1");

        Tag existingScoreTag = mock(Tag.class);
        when(existingScoreTag.getTagID()).thenReturn(SCALE_OF_BELIEF_TAG_PREFIX + 2);

        when(tagManager.getTags(jcrContent)).thenReturn(new Tag[] { existingTag, existingScoreTag });

        Tag scoreTag = mock(Tag.class);
        when(scoreTag.getTagID()).thenReturn(SCALE_OF_BELIEF_TAG_PREFIX + SCORE);
        when(tagManager.resolve(SCALE_OF_BELIEF_TAG_PREFIX + SCORE)).thenReturn(scoreTag);

        Map<String, Object> propertyMap = Maps.newHashMap();
        mockForUpdateScore(resource, propertyMap);

        syncScoreService.syncScore(resourceResolver, SCORE, ABSOLUTE_PATH);

        assertSuccessful(propertyMap, new Tag[] { existingTag, scoreTag });
    }

    private void mockForUpdateScore(final Resource resource, final Map<String, Object> propertyMap) throws Exception {
        when(resource.getPath()).thenReturn(ABSOLUTE_PATH);
        when(resource.getChild("jcr:content")).thenReturn(jcrContent);

        Node contentNode = mock(Node.class);
        when(contentNode.getPath()).thenReturn(ABSOLUTE_PATH + "/jcr:content");

        doAnswer(setProperty(propertyMap)).when(contentNode).setProperty(anyString(), any(String.class));
        doAnswer(setProperty(propertyMap)).when(contentNode).setProperty(anyString(), any(Calendar.class));

        when(jcrContent.adaptTo(Node.class)).thenReturn(contentNode);

        Node pageNode = mock(Node.class);
        when(resource.adaptTo(Node.class)).thenReturn(pageNode);
        doAnswer(setProperty(propertyMap)).when(pageNode).setProperty(anyString(), any(Boolean.class));
    }

    private Answer<Property> setProperty(final Map<String, Object> propertyMap) {
        return invocation -> {
            propertyMap.put((String) invocation.getArguments()[0], invocation.getArguments()[1]);
            return null;
        };
    }

    private void assertSuccessful(final Map<String, Object> propertyMap, final Tag[] tags) {
        assertThat(propertyMap.get("cq:lastModifiedBy"), is(equalTo("scale-of-belief")));
        assertThat(propertyMap.get("cq:lastModified"), is(notNullValue()));
        assertThat(propertyMap.get("contentScoreLastUpdated"), is(notNullValue()));

        verify(tagManager).setTags(eq(jcrContent), aryEq(tags));
    }
}
