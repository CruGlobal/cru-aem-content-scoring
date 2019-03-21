package org.cru.contentscoring.core.servlets;

import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.search.Predicate;
import com.day.cq.search.SimpleSearch;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;
import com.day.cq.tagging.Tag;
import com.day.cq.tagging.TagManager;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;

import static org.cru.contentscoring.core.service.impl.SyncScoreServiceImpl.SCALE_OF_BELIEF_TAG_PREFIX;

@SlingServlet(
    paths = "/bin/cru/content-scoring/move-scores-to-tags",
    metatype = true,
    methods = {"PUT"}
)
public class CopyScoresToTagsServlet extends SlingAllMethodsServlet {
    private static final Logger LOG = LoggerFactory.getLogger(CopyScoresToTagsServlet.class);

    @Override
    protected void doPut(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
        throws IOException {

        String path = request.getParameter("path");
        if (Strings.isNullOrEmpty(path)) {
            response.sendError(400, "Path is required");
            return;
        }

        Principal principal = request.getUserPrincipal();
        if (principal == null || !principal.getName().equals("admin")) {
            LOG.error(
                "Unauthorized attempt to move scores to tags by {}",
                principal == null ? "Anonymous" : principal.getName());
            response.sendError(401, "You are not authorized to perform this command.");
            return;
        }

        Resource root = request.getResourceResolver().getResource(path);
        if (root == null) {
            response.sendError(400, "Invalid path");
            return;
        }

        TagManager tagManager = request.getResourceResolver().adaptTo(TagManager.class);

        List<Hit> results = findPagesWithScoreProperty(root);

        try {
            copyScoresToTags(results, tagManager);
        } catch (RepositoryException e) {
            LOG.error(e.getMessage());
            response.sendError(500, e.getMessage());
        }
    }

    private List<Hit> findPagesWithScoreProperty(final Resource root) {
        SimpleSearch search = root.adaptTo(SimpleSearch.class);
        search.addPredicate(buildScorePredicate());
        search.addPredicate(buildRootPathPredicate(root.getPath()));

        try {
            SearchResult searchResult = search.getResult();
            return searchResult.getHits();
        } catch (RepositoryException e) {
            LOG.error("Failed to search for pages with a score property under path {}", root.getPath(), e);
        }
        return Lists.newArrayList();
    }

    private Predicate buildScorePredicate() {
        Predicate scorePredicate = new Predicate("score", "property");
        scorePredicate.set("property", JcrConstants.JCR_CONTENT + "/" + "score");
        scorePredicate.set("operation", "exists");
        return scorePredicate;
    }

    private Predicate buildRootPathPredicate(final String rootPath) {
        Predicate rootPathPredicate = new Predicate("rootPath", "path");
        rootPathPredicate.set("path", rootPath);
        rootPathPredicate.set("self", "true");
        return rootPathPredicate;
    }

    private void copyScoresToTags(final List<Hit> results, final TagManager tagManager) throws RepositoryException {
        for (Hit result : results) {
            copyScoreToTag(result.getResource(), tagManager);
        }
    }

    private void copyScoreToTag(final Resource page, final TagManager tagManager) {
        Resource pageContent = getJcrContent(page);

        if (pageContent != null) {
            String score = pageContent.getValueMap().get("score", String.class);
            List<Tag> tags = buildTagsWithScore(pageContent, tagManager, score);
            tagManager.setTags(pageContent, tags.toArray(new Tag[0]));
        }
    }

    private Resource getJcrContent(final Resource resource) {
        if (resource != null && resource.getResourceType().equals("cq:Page")) {
            return resource.getChild(JcrConstants.JCR_CONTENT);
        }
        return null;
    }

    private List<Tag> buildTagsWithScore(
        final Resource contentResource,
        final TagManager tagManager,
        final String score) {

        Tag[] existingTags = tagManager.getTags(contentResource);
        List<Tag> newTags = Lists.newArrayList();

        for (Tag existingTag : existingTags) {
            // If there is already a score tag on this resource, prefer it over the property.
            if (existingTag.getTagID().startsWith(SCALE_OF_BELIEF_TAG_PREFIX)) {
                return Arrays.asList(existingTags);
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
