package org.cru.contentscoring.core.service.impl;

import com.day.cq.tagging.Tag;
import com.day.cq.tagging.TagManager;
import com.google.common.collect.Lists;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.cru.contentscoring.core.service.SyncScoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.Calendar;
import java.util.List;

@Component
@Service(SyncScoreService.class)
public class SyncScoreServiceImpl implements SyncScoreService {
    private static final Logger LOG = LoggerFactory.getLogger(SyncScoreServiceImpl.class);

    public static final String SCALE_OF_BELIEF_TAG_PREFIX = "target-audience:scale-of-belief/";

    @Override
    public void syncScore(
        final ResourceResolver resourceResolver,
        final int score,
        final String resourcePath) throws RepositoryException {

        Resource resource = resourceResolver.getResource(resourcePath);

        if (resource == null) {
            return;
        }
        updateScore(resourceResolver, score, resource);
    }

    private void updateScore(
        final ResourceResolver resourceResolver,
        final int score,
        final Resource resource) throws RepositoryException {

        Resource contentResource = resource.getChild("jcr:content");

        if (contentResource != null) {
            Node node = contentResource.adaptTo(Node.class);
            if (node != null) {
                LOG.debug("Setting score on {} to {}", node.getPath(), score);
                node.setProperty("score", Integer.toString(score));

                Calendar now = Calendar.getInstance();

                node.setProperty(ContentScoreUpdateServiceImpl.CONTENT_SCORE_UPDATED, now);
                node.setProperty("cq:lastModified", now);
                node.setProperty("cq:lastModifiedBy", "scale-of-belief");

                TagManager tagManager = resourceResolver.adaptTo(TagManager.class);
                List<Tag> newTags = buildTagsWithScore(contentResource, tagManager, score);
                tagManager.setTags(contentResource, newTags.toArray(new Tag[0]));
            }
        }
    }

    private List<Tag> buildTagsWithScore(
        final Resource contentResource,
        final TagManager tagManager,
        final int score) {

        Tag[] existingTags = tagManager.getTags(contentResource);
        List<Tag> newTags = Lists.newArrayList();

        for (Tag existingTag : existingTags) {
            if (existingTag.getTagID().startsWith(SCALE_OF_BELIEF_TAG_PREFIX)) {
                continue;
            }
            newTags.add(existingTag);
        }

        Tag scoreTag = tagManager.resolve(SCALE_OF_BELIEF_TAG_PREFIX + score);
        if (scoreTag != null) {
            newTags.add(scoreTag);
        }

        return newTags;
    }
}
