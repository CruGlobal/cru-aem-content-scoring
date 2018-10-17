package org.cru.contentscoring.core.service.impl;

import com.day.cq.commons.PathInfo;
import com.day.cq.replication.ReplicationException;
import com.day.cq.search.Predicate;
import com.day.cq.search.SimpleSearch;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;
import com.google.common.collect.Iterables;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.settings.SlingSettingsService;
import org.cru.contentscoring.core.service.SyncScoreService;
import org.cru.contentscoring.core.util.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

@Component
@Service(SyncScoreService.class)
public class SyncScoreServiceImpl implements SyncScoreService {
    private static final Logger LOG = LoggerFactory.getLogger(SyncScoreServiceImpl.class);
    private static final String DEFAULT_PATH_SCOPE = "/content";

    Map<String, String> hostMap;

    @Reference
    private SlingSettingsService slingSettingsService;

    @Reference
    private SystemUtils systemUtils;

    @Activate
    public void activate(final Map<String, Object> config) {
        hostMap = PropertiesUtil.toMap(config.get("hostMap"), null);
    }

    @Override
    public void syncScore(
        final ResourceResolver resourceResolver,
        final int score,
        final String resourcePath,
        final String resourceHost) throws RepositoryException, ReplicationException, LoginException {

        if (resourcePath.startsWith("/content")) {
            // Absolute path, so just update score
            updateScoreForPath(resourcePath, resourceResolver, score);
            return;
        }

        RequestPathInfo mappedPathInfo = new PathInfo(resourceResolver, resourcePath);
        String pathScope = DEFAULT_PATH_SCOPE;

        if (hostMap != null) {
            for (Map.Entry<String, String> entry : hostMap.entrySet()) {
                String host = entry.getKey();
                String path = entry.getValue();

                if (host.contains(resourceHost)) {
                    pathScope = path;
                    break;
                }
            }
        }

        Resource parent = resourceResolver.getResource(pathScope);
        if (parent == null) {
            LOG.warn(
                "Path not found: {}. Please check to make sure the configuration for hostMap is correct.",
                pathScope);
            return;
        }

        String candidateVanity = mappedPathInfo.getResourcePath();

        LOG.debug("Candidate vanity URL to check: {}", candidateVanity);

        String vanityPath = getVanityPath(pathScope, candidateVanity, resourceResolver, parent);
        if (vanityPath != null && !StringUtils.equals(candidateVanity, vanityPath)) {
            updateScoreForPath(vanityPath, resourceResolver, score);
        } else {
            String actualPath = determineActualPath(resourcePath, parent);

            if (actualPath != null) {
                updateScoreForPath(actualPath, resourceResolver, score);
            }
        }
    }

    /**
     * Checks if the provided vanity path is a valid redirect
     * Modified from
     * https://github.com/Adobe-Consulting-Services/acs-aem-commons/blob/master/bundle/src/main/java/com/adobe/acs/commons/wcm/vanity/impl/VanityURLServiceImpl.java
     *
     * @param pathScope The content path to scope the vanity path too.
     * @param vanityPath Vanity path that needs to be validated.
     * @param resourceResolver ResourceResolver object used for performing query/lookup
     * @return return true if the vanityPath is a registered sling:vanityPath under /content
     */
    private String getVanityPath(
        final String pathScope,
        final String vanityPath,
        final ResourceResolver resourceResolver,
        final Resource parent) throws RepositoryException {

        Resource vanityResource = resourceResolver.getResource(vanityPath);
        String targetPath = null;

        if (vanityResource != null) {
            if (vanityResource.isResourceType("sling:redirect")) {
                targetPath = vanityResource.getValueMap().get("sling:target", String.class);
            } else if (!StringUtils.equals(vanityPath, vanityResource.getPath())) {
                targetPath = vanityResource.getPath();
            }

            if (targetPath != null &&
                StringUtils.startsWith(targetPath, StringUtils.defaultIfEmpty(pathScope, DEFAULT_PATH_SCOPE))) {

                LOG.debug("Found vanity resource at {} for sling:vanityPath {}", targetPath, vanityPath);
                return targetPath;
            }
        } else {
            Resource actualPage = searchForResourceWithVanityPath(vanityPath, parent);
            if (actualPage != null) {
                return actualPage.getPath();
            }
        }

        return null;
    }

    private Resource searchForResourceWithVanityPath(
        final String vanityPath,
        final Resource parent) throws RepositoryException {

        SimpleSearch search = parent.adaptTo(SimpleSearch.class);

        if (search != null) {
            Predicate vanityPathPredicate = new Predicate("property");
            vanityPathPredicate.set("property", "sling:vanityPath");
            vanityPathPredicate.set("value", "%" + vanityPath.substring(1));
            vanityPathPredicate.set("operation", "like");
            search.addPredicate(vanityPathPredicate);

            Predicate typePredicate = new Predicate("type");
            typePredicate.set("type", "cq:PageContent");
            search.addPredicate(typePredicate);

            SearchResult searchResult = search.getResult();
            List<Hit> hits = searchResult.getHits();

            if (hits.isEmpty()) {
                return null;
            }

            for (Hit hit : hits) {
                LOG.debug("Found path: {} for sling:vanityPath", hit.getPath());
            }

            if (hits.size() > 1) {
                LOG.warn("Found more than one page with vanity path {}, skipping score sync.", vanityPath);
                return null;
            }

            return Iterables.getOnlyElement(hits).getResource().getParent();
        }

        return null;
    }

    private String determineActualPath(
        final String resourcePath,
        final Resource parent) throws RepositoryException {

        SimpleSearch search = parent.adaptTo(SimpleSearch.class);

        if (search != null) {
            int lastIndexOfSlash = resourcePath.lastIndexOf("/");
            String nodeName = resourcePath.substring(lastIndexOfSlash + 1);

            Predicate nodeNamePredicate = new Predicate( "nodename");
            nodeNamePredicate.set("nodename", nodeName);

            search.addPredicate(nodeNamePredicate);

            SearchResult searchResult = search.getResult();
            List<Hit> hits = searchResult.getHits();

            if (hits.isEmpty()) {
                return null;
            }

            for (Hit hit : hits) {
                LOG.debug("Found path: {} for resource path {}", hit.getPath(), resourcePath);
            }

            if (hits.size() > 1) {
                LOG.warn("Found more than one page with path {}, skipping score sync.", resourcePath);
                return null;
            }

            return Iterables.getOnlyElement(hits).getPath();
        }

        return null;
    }

    private void updateScoreForPath(
        final String jcrPath,
        final ResourceResolver resourceResolver,
        final int score) throws RepositoryException, ReplicationException, LoginException {

        Resource resource = resourceResolver.getResource(jcrPath);

        if (resource != null) {
            Resource contentResource = resource.getChild("jcr:content");

            if (contentResource != null) {
                Node node = contentResource.adaptTo(Node.class);
                if (node != null) {
                    node.setProperty("score", Integer.toString(score));

                    Calendar now = Calendar.getInstance();
                    ZonedDateTime zonedNow = ZonedDateTime.ofInstant(now.toInstant(), now.getTimeZone().toZoneId());

                    node.setProperty(
                        ContentScoreUpdateServiceImpl.CONTENT_SCORE_UPDATED,
                        zonedNow.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                    node.setProperty("cq:lastModified", now);
                    node.setProperty("cq:lastModifiedBy", "scale-of-belief");
                    Session session = resourceResolver.adaptTo(Session.class);

                    if (session != null) {
                        session.save();
                    }

                    if (slingSettingsService.getRunModes().contains("author")) {
                        systemUtils.replicatePage(node.getPath());
                    }
                }
            }
        }
    }
}
