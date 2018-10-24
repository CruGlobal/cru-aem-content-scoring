package org.cru.contentscoring.core.service.impl;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.cru.contentscoring.core.service.SyncScoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.Calendar;

@Component
@Service(SyncScoreService.class)
public class SyncScoreServiceImpl implements SyncScoreService {
    private static final Logger LOG = LoggerFactory.getLogger(SyncScoreServiceImpl.class);

    @Override
    public void syncScore(
        final ResourceResolver resourceResolver,
        final int score,
        final Resource resource) throws RepositoryException {

        updateScore(resourceResolver, score, resource);
    }

    private void updateScore(
        final ResourceResolver resourceResolver,
        final int score,
        final Resource resource) throws RepositoryException {
        // I can't use resource.getChild() here because the resource resolver inside of resource is already closed.
        Resource contentResource = resourceResolver.getResource(resource.getPath()).getChild("jcr:content");

        if (contentResource != null) {
            Node node = contentResource.adaptTo(Node.class);
            if (node != null) {
                LOG.debug("Setting score on {} to {}", node.getPath(), score);
                node.setProperty("score", Integer.toString(score));

                Calendar now = Calendar.getInstance();

                node.setProperty(ContentScoreUpdateServiceImpl.CONTENT_SCORE_UPDATED, now);
                node.setProperty("cq:lastModified", now);
                node.setProperty("cq:lastModifiedBy", "scale-of-belief");
                Session session = resourceResolver.adaptTo(Session.class);

                if (session != null) {
                    session.save();
                }
            }
        }
    }
}
