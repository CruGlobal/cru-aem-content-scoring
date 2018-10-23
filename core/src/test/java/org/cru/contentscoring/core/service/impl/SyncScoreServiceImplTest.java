package org.cru.contentscoring.core.service.impl;

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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
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
    }

    @Test
    public void testScoreIsSynced() throws Exception {
        Resource resource = mock(Resource.class);
        when(resourceResolver.getResource(ABSOLUTE_PATH)).thenReturn(resource);

        Map<String, Object> propertyMap = Maps.newHashMap();
        mockForUpdateScore(resource, propertyMap);

        syncScoreService.syncScore(resourceResolver, SCORE, resource);

        assertSuccessful(propertyMap);
    }

    private void mockForUpdateScore(final Resource resource, final Map<String, Object> propertyMap) throws Exception {
        when(resource.getPath()).thenReturn(ABSOLUTE_PATH);
        when(resource.getChild("jcr:content")).thenReturn(jcrContent);

        Node contentNode = mock(Node.class);
        when(contentNode.getPath()).thenReturn(ABSOLUTE_PATH + "/jcr:content");

        doAnswer(setProperty(propertyMap)).when(contentNode).setProperty(anyString(), any(String.class));
        doAnswer(setProperty(propertyMap)).when(contentNode).setProperty(anyString(), any(Calendar.class));

        when(jcrContent.adaptTo(Node.class)).thenReturn(contentNode);
    }

    private Answer<Property> setProperty(final Map<String, Object> propertyMap) {
        return invocation -> {
            propertyMap.put((String) invocation.getArguments()[0], invocation.getArguments()[1]);
            return null;
        };
    }

    private void assertSuccessful(final Map<String, Object> propertyMap) throws Exception {
        assertThat(propertyMap.get("score"), is(equalTo(Integer.toString(SCORE))));
        assertThat(propertyMap.get("cq:lastModifiedBy"), is(equalTo("scale-of-belief")));
        assertThat(propertyMap.get("cq:lastModified"), is(notNullValue()));
        assertThat(propertyMap.get("contentScoreLastUpdated"), is(notNullValue()));

        verify(session).save();
    }
}
