package org.cru.contentscoring.core.service.impl;

import com.day.cq.wcm.api.Page;
import com.google.common.collect.Maps;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.cru.contentscoring.core.models.ContentScore;
import org.cru.contentscoring.core.models.ScoreType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.Calendar;
import java.util.Map;

import static org.cru.contentscoring.core.service.impl.ContentScoreUpdateServiceImpl.API_ENDPOINT;
import static org.cru.contentscoring.core.service.impl.ContentScoreUpdateServiceImpl.CONTENT_SCORE_UPDATED;
import static org.cru.contentscoring.core.service.impl.ContentScoreUpdateServiceImpl.ERROR_EMAIL_RECIPIENTS;
import static org.cru.contentscoring.core.service.impl.ContentScoreUpdateServiceImpl.MAX_RETRIES;
import static org.cru.contentscoring.core.service.impl.ContentScoreUpdateServiceImpl.MAX_SIZE;
import static org.cru.contentscoring.core.service.impl.ContentScoreUpdateServiceImpl.WAIT_TIME;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ContentScoreUpdateServiceImplTest {
    private static final String UNAWARE_SCORE = "1";
    private static final String CURIOUS_SCORE = "2";
    private static final String FOLLOWER_SCORE = "5";
    private static final String GUIDE_SCORE = "3";

    @InjectMocks
    private ContentScoreUpdateServiceImpl updateService;

    @Test
    public void testActivation() {
        Map<String, Object> config = Maps.newHashMap();
        config.put(API_ENDPOINT, "http://somewhere-out.there.com");
        config.put(MAX_SIZE, 6000000L);
        config.put(WAIT_TIME, 10000L);
        config.put(MAX_RETRIES, 5);
        config.put(ERROR_EMAIL_RECIPIENTS, "some.email@example.com,another.email@example.com");

        updateService.activate(config);
        assertThat(ContentScoreUpdateServiceImpl.internalQueueManager, is(not(nullValue())));
        assertThat(ContentScoreUpdateServiceImpl.queueManagerThread, is(not(nullValue())));
    }

    @Test
    public void testPageHasNoScores() {
        ValueMap pageProperties = new ValueMapDecorator(Maps.newHashMap());
        pageProperties.put("someProperty", "someValue");

        assertThat(updateService.hasNoScores(pageProperties), is(true));
    }

    @Test
    public void testPageHasUnawareScore() {
        ValueMap pageProperties = new ValueMapDecorator(Maps.newHashMap());
        pageProperties.put(ScoreType.UNAWARE.getPropertyName(), UNAWARE_SCORE);

        assertThat(updateService.hasNoScores(pageProperties), is(false));
    }

    @Test
    public void testPageHasCuriousScore() {
        ValueMap pageProperties = new ValueMapDecorator(Maps.newHashMap());
        pageProperties.put(ScoreType.CURIOUS.getPropertyName(), CURIOUS_SCORE);

        assertThat(updateService.hasNoScores(pageProperties), is(false));
    }

    @Test
    public void testPageHasFollowerScore() {
        ValueMap pageProperties = new ValueMapDecorator(Maps.newHashMap());
        pageProperties.put(ScoreType.FOLLOWER.getPropertyName(), FOLLOWER_SCORE);

        assertThat(updateService.hasNoScores(pageProperties), is(false));
    }

    @Test
    public void testPageHasGuideScore() {
        ValueMap pageProperties = new ValueMapDecorator(Maps.newHashMap());
        pageProperties.put(ScoreType.GUIDE.getPropertyName(), GUIDE_SCORE);

        assertThat(updateService.hasNoScores(pageProperties), is(false));
    }

    @Test
    public void testCreateUnawareOnlyScore() {
        ValueMap pageProperties = new ValueMapDecorator(Maps.newHashMap());
        pageProperties.put(ScoreType.UNAWARE.getPropertyName(), UNAWARE_SCORE);

        ContentScore contentScore = updateService.createScore(pageProperties);

        assertThat(contentScore.getUnaware(), is(equalTo(Integer.parseInt(UNAWARE_SCORE))));
        assertThat(contentScore.getCurious(), is(equalTo(0)));
        assertThat(contentScore.getFollower(), is(equalTo(0)));
        assertThat(contentScore.getGuide(), is(equalTo(0)));
    }

    @Test
    public void testCreateCuriousOnlyScore() {
        ValueMap pageProperties = new ValueMapDecorator(Maps.newHashMap());
        pageProperties.put(ScoreType.CURIOUS.getPropertyName(), CURIOUS_SCORE);

        ContentScore contentScore = updateService.createScore(pageProperties);

        assertThat(contentScore.getUnaware(), is(equalTo(0)));
        assertThat(contentScore.getCurious(), is(equalTo(Integer.parseInt(CURIOUS_SCORE))));
        assertThat(contentScore.getFollower(), is(equalTo(0)));
        assertThat(contentScore.getGuide(), is(equalTo(0)));
    }

    @Test
    public void testCreateFollowerOnlyScore() {
        ValueMap pageProperties = new ValueMapDecorator(Maps.newHashMap());
        pageProperties.put(ScoreType.FOLLOWER.getPropertyName(), FOLLOWER_SCORE);

        ContentScore contentScore = updateService.createScore(pageProperties);

        assertThat(contentScore.getUnaware(), is(equalTo(0)));
        assertThat(contentScore.getCurious(), is(equalTo(0)));
        assertThat(contentScore.getFollower(), is(equalTo(Integer.parseInt(FOLLOWER_SCORE))));
        assertThat(contentScore.getGuide(), is(equalTo(0)));
    }

    @Test
    public void testCreateGuideOnlyScore() {
        ValueMap pageProperties = new ValueMapDecorator(Maps.newHashMap());
        pageProperties.put(ScoreType.GUIDE.getPropertyName(), GUIDE_SCORE);

        ContentScore contentScore = updateService.createScore(pageProperties);

        assertThat(contentScore.getUnaware(), is(equalTo(0)));
        assertThat(contentScore.getCurious(), is(equalTo(0)));
        assertThat(contentScore.getFollower(), is(equalTo(0)));
        assertThat(contentScore.getGuide(), is(equalTo(Integer.parseInt(GUIDE_SCORE))));
    }

    @Test
    public void testCreateAllScores() {
        ValueMap pageProperties = new ValueMapDecorator(Maps.newHashMap());
        pageProperties.put(ScoreType.UNAWARE.getPropertyName(), UNAWARE_SCORE);
        pageProperties.put(ScoreType.CURIOUS.getPropertyName(), CURIOUS_SCORE);
        pageProperties.put(ScoreType.FOLLOWER.getPropertyName(), FOLLOWER_SCORE);
        pageProperties.put(ScoreType.GUIDE.getPropertyName(), GUIDE_SCORE);

        ContentScore contentScore = updateService.createScore(pageProperties);

        assertThat(contentScore.getUnaware(), is(equalTo(Integer.parseInt(UNAWARE_SCORE))));
        assertThat(contentScore.getCurious(), is(equalTo(Integer.parseInt(CURIOUS_SCORE))));
        assertThat(contentScore.getFollower(), is(equalTo(Integer.parseInt(FOLLOWER_SCORE))));
        assertThat(contentScore.getGuide(), is(equalTo(Integer.parseInt(GUIDE_SCORE))));
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
}
