package org.cru.contentscoring.core.servlets;

import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.replication.ReplicationActionType;
import com.day.cq.replication.ReplicationException;
import com.day.cq.replication.ReplicationOptions;
import com.day.cq.replication.Replicator;
import com.day.cq.search.Predicate;
import com.day.cq.search.SimpleSearch;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;
import com.day.cq.tagging.Tag;
import com.day.cq.tagging.TagManager;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.settings.SlingSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.cru.contentscoring.core.service.impl.SyncScoreServiceImpl.SCALE_OF_BELIEF_TAG_PREFIX;

@SlingServlet(
    paths = "/bin/cru/content-scoring/move-scores-to-tags",
    metatype = true,
    methods = {"PUT"}
)
public class CopyScoresToTagsServlet extends SlingAllMethodsServlet {
    private static final Logger LOG = LoggerFactory.getLogger(CopyScoresToTagsServlet.class);

    private static final String PRIMARY_XF_NAME = "primaryExperienceFragment";

    @Reference
    private SlingSettingsService slingSettingsService;

    @Reference
    private Replicator replicator;

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

        ResourceResolver resourceResolver = request.getResourceResolver();
        Resource root = resourceResolver.getResource(path);
        if (root == null) {
            response.sendError(400, "Invalid path");
            return;
        }

        TagManager tagManager = resourceResolver.adaptTo(TagManager.class);

        List<Hit> results = findPagesWithScoreProperty(root);

        try {
            moveScoresToTags(results, tagManager, resourceResolver);
        } catch (RepositoryException | ReplicationException e) {
            LOG.error(e.getMessage());
            response.sendError(500, e.getMessage());
        }
    }

    private List<Hit> findPagesWithScoreProperty(final Resource root) {
        SimpleSearch search = root.adaptTo(SimpleSearch.class);
        search.addPredicate(buildScorePredicate());
        search.addPredicate(buildTypePredicate());
        search.setSearchIn(root.getPath());
        search.setHitsPerPage(0L); // 0 means unlimited

        try {
            SearchResult searchResult = search.getResult();
            LOG.debug("Search took {} seconds", searchResult.getExecutionTime());
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

    private Predicate buildTypePredicate() {
        Predicate typePredicate = new Predicate("type", "type");
        typePredicate.set("type", "cq:Page");
        return typePredicate;
    }

    private void moveScoresToTags(
        final List<Hit> results,
        final TagManager tagManager,
        final ResourceResolver resourceResolver) throws RepositoryException, ReplicationException {

        List<Resource> pages = Lists.newArrayList();
        for (Hit result : results) {
            Resource page = result.getResource();
            copyScoreTagToPrimaryExperienceFragment(page, resourceResolver, tagManager);
            moveScoreToTag(page, tagManager);
            pages.add(page);
        }

        replicatePages(pages, resourceResolver);
    }

    private void moveScoreToTag(final Resource page, final TagManager tagManager) throws RepositoryException {
        Resource pageContent = getJcrContent(page);

        if (pageContent != null) {
            String score = pageContent.getValueMap().get("score", String.class);
            pageContent.adaptTo(Node.class).getProperty("score").remove();

            Set<Tag> tags = buildTagsWithScore(pageContent, tagManager, score);
            tagManager.setTags(pageContent, tags.toArray(new Tag[0]));
        }
    }

    private void copyScoreTagToPrimaryExperienceFragment(
        final Resource page,
        final ResourceResolver resourceResolver,
        final TagManager tagManager) {

        Resource pageContent = getJcrContent(page);

        if (pageContent != null) {
            String score = pageContent.getValueMap().get("score", String.class);
            String primaryExperienceFragmentPath = pageContent.getValueMap().get(PRIMARY_XF_NAME, String.class);

            if (!Strings.isNullOrEmpty(primaryExperienceFragmentPath)) {
                Resource experienceFragment = resourceResolver.getResource(primaryExperienceFragmentPath);

                if (experienceFragment != null) {
                    Resource experienceFragmentContent = getJcrContent(experienceFragment);
                    Set<Tag> tags = buildTagsWithScore(experienceFragmentContent, tagManager, score);
                    tagManager.setTags(experienceFragmentContent, tags.toArray(new Tag[0]));
                }
            }
        }
    }

    private Resource getJcrContent(final Resource resource) {
        if (resource != null && resource.getResourceType().equals("cq:Page")) {
            return resource.getChild(JcrConstants.JCR_CONTENT);
        }
        return null;
    }

    private Set<Tag> buildTagsWithScore(
        final Resource contentResource,
        final TagManager tagManager,
        final String score) {

        Tag[] existingTags = tagManager.getTags(contentResource);
        Set<Tag> newTags = Sets.newHashSet();

        for (Tag existingTag : existingTags) {
            // If there is already a score tag on this resource, prefer it over the property.
            if (existingTag.getTagID().startsWith(SCALE_OF_BELIEF_TAG_PREFIX)) {
                return Sets.newHashSet(Arrays.asList(existingTags));
            }
            newTags.add(existingTag);
        }

        Tag scoreTag = tagManager.resolve(SCALE_OF_BELIEF_TAG_PREFIX + score);
        if (scoreTag != null) {
            newTags.add(scoreTag);
        }

        return newTags;
    }

    private void replicatePages(final List<Resource> pages, final ResourceResolver resourceResolver)
        throws ReplicationException {

        if (slingSettingsService.getRunModes().contains("author")) {
            Session session = resourceResolver.adaptTo(Session.class);

            List<String> pathsToReplicate = Lists.newArrayList();
            for (Resource page : pages) {
                // Only replicate pages that have already been replicated
                if (replicator.getReplicationStatus(session, page.getPath()).isActivated()) {
                    pathsToReplicate.add(page.getPath());
                }
            }

            if (pathsToReplicate.isEmpty()) {
                return;
            }

            replicator.replicate(
                session,
                ReplicationActionType.ACTIVATE,
                pathsToReplicate.toArray(new String[]{}),
                new ReplicationOptions());
        }
    }
}
