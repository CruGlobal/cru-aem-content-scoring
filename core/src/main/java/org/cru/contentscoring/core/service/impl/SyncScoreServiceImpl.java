package org.cru.contentscoring.core.service.impl;

import com.day.cq.commons.PathInfo;
import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;
import com.day.crx.JcrConstants;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.settings.SlingSettingsService;
import org.cru.contentscoring.core.service.SyncScoreService;
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

    @Reference
    private SlingSettingsService slingSettingsService;

    @Reference
    private QueryBuilder queryBuilder;

    @Override
    public void syncScore(
        final ResourceResolver resourceResolver,
        final int score,
        final String resourcePath,
        final String resourceHost,
        final String resourceProtocol) throws RepositoryException {

        String resourcePathWithoutExtension = removeExtension(resourcePath);

        if (resourcePathWithoutExtension.startsWith("/content")) {
            // Absolute path, so just update score
            updateScoreForPath(resourcePathWithoutExtension, resourceResolver, score);
            return;
        }

        if (resourcePathWithoutExtension.equals("/")) {
            String actualPath = determinePathFromSlingMap(resourceHost, resourceProtocol, resourceResolver, true);
            updateScoreForPath(actualPath, resourceResolver, score);
            return;
        }

        RequestPathInfo mappedPathInfo = new PathInfo(resourceResolver, resourcePathWithoutExtension);
        String pathScope = StringUtils.defaultIfEmpty(
            determinePathFromSlingMap(resourceHost, resourceProtocol, resourceResolver, false),
            DEFAULT_PATH_SCOPE);

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
            String actualPath = determineActualPath(
                resourcePathWithoutExtension,
                parent,
                resourceResolver,
                resourceHost,
                resourceProtocol);

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
            Resource actualPage = searchForResourceWithVanityPath(vanityPath, parent, resourceResolver);
            if (actualPage != null) {
                return actualPage.getPath();
            }
        }

        return null;
    }

    private Resource searchForResourceWithVanityPath(
        final String vanityPath,
        final Resource parent,
        final ResourceResolver resourceResolver) throws RepositoryException {


        Map<String, String> searchPredicates = Maps.newHashMap();
        searchPredicates.put("property", "sling:vanityPath");
        searchPredicates.put("property.value", "%" + vanityPath.substring(1));
        searchPredicates.put("property.operation", "like");

        searchPredicates.put("type", "cq:PageContent");
        searchPredicates.put("path", parent.getPath());

        Session session = resourceResolver.adaptTo(Session.class);
        Query query = queryBuilder.createQuery(PredicateGroup.create(searchPredicates), session);
        SearchResult searchResult = query.getResult();
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

    private String determineActualPath(
        final String resourcePath,
        final Resource parent,
        final ResourceResolver resourceResolver,
        final String host,
        final String protocol) throws RepositoryException {

        Map<String, String> searchPredicates = Maps.newHashMap();

        int lastIndexOfSlash = resourcePath.lastIndexOf("/");
        String nodeName = resourcePath.substring(lastIndexOfSlash + 1);

        searchPredicates.put("nodename", nodeName);
        searchPredicates.put("type", "cq:Page");
        searchPredicates.put("path", parent.getPath());

        Session session = resourceResolver.adaptTo(Session.class);
        Query query = queryBuilder.createQuery(PredicateGroup.create(searchPredicates), session);

        SearchResult searchResult = query.getResult();
        List<Hit> hits = searchResult.getHits();

        if (hits.isEmpty()) {
            return null;
        }

        List<Hit> filteredHits = Lists.newArrayList();
        for (Hit hit : hits) {
            if (hit.getPath().startsWith(parent.getPath()) && hit.getPath().endsWith(resourcePath)) {
                LOG.debug("Found path: {} for resource path {}", hit.getPath(), resourcePath);
                filteredHits.add(hit);
            }
        }

        if (filteredHits.size() > 1) {
            MutablePair<String, String> countryLanguage = determineCountryLanguage(
                resourcePath,
                host,
                protocol,
                parent,
                resourceResolver);

            if (countryLanguage != null) {
                for (Hit hit : filteredHits) {
                    if (hit.getPath().contains(countryLanguage.getKey()) 
                        && hit.getPath().contains(countryLanguage.getValue())) {
                        
                        return hit.getPath();
                    }
                }
            }
            LOG.warn("Found more than one page with path {}, skipping score sync.", resourcePath);
            return null;
        }

        return Iterables.getOnlyElement(filteredHits).getPath();
    }

    private MutablePair<String, String> determineCountryLanguage(
        final String resourcePath,
        final String host,
        final String protocol,
        final Resource parent,
        final ResourceResolver resourceResolver) {
        List<MutablePair<String, String>> countryLanguageList = buildCountryLanguageList(parent);

        String homePagePath = determinePathFromSlingMap(host, protocol, resourceResolver, true);
        MutablePair<String, String> homeCountryLanguage = null;

        for (MutablePair<String, String> countryLanguage : countryLanguageList) {
            if (resourcePath.contains(countryLanguage.getKey()) && resourcePath.contains(countryLanguage.getValue())) {
                return countryLanguage;
            }
            if (homePagePath != null &&
                homePagePath.contains(countryLanguage.getKey()) && homePagePath.contains(countryLanguage.getValue())) {

                homeCountryLanguage = countryLanguage;
            }
        }

        return homeCountryLanguage;
    }

    private List<MutablePair<String, String>> buildCountryLanguageList(final Resource parent) {
        List<String> knownNamesToSkip = ImmutableList.of(
            JcrConstants.JCR_CONTENT,
            "rep:policy",
            "errors"
        );
        List<MutablePair<String, String>> countryLanguageList = Lists.newArrayList();
        for (Resource child : parent.getChildren()) {
            // These aren't necessarily all countries (e.g. gsw has "languages" here)
            String country = child.getName();

            if (knownNamesToSkip.contains(country)) {
                continue;
            }

            for (Resource grandChild : child.getChildren()) {
                // They aren't all necessarily languages, but I don't know a great way to ensure it is a language.
                String language = grandChild.getName();

                if (knownNamesToSkip.contains(language)) {
                    continue;
                }

                countryLanguageList.add(new MutablePair<>(country, language));
            }
        }
        return countryLanguageList;
    }

    private String determinePathFromSlingMap(
        final String host,
        final String protocol,
        final ResourceResolver resourceResolver,
        final boolean homePage) {

        String pathToSlingMapping = determineSlingMappingPath();

        if (pathToSlingMapping == null) {
            LOG.debug("Path to sling mapping was not found, skipping home page sync");
            return null;
        }

        Resource parentMapResource = resourceResolver.getResource(pathToSlingMapping);

        if (parentMapResource != null) {

            Resource protocolResource = parentMapResource.getChild(protocol);

            if (protocolResource != null) {
                String hostName = host;
                if (homePage) {
                    int lastIndexOfPeriod = host.lastIndexOf(".");
                    hostName = host.substring(0, lastIndexOfPeriod) + "_" + host.substring(lastIndexOfPeriod + 1);
                }
                Resource slingMapResource = protocolResource.getChild(hostName);

                if (slingMapResource != null) {
                    String[] internalRedirect = (String[]) slingMapResource.getValueMap().get("sling:internalRedirect");
                    return removeExtension(internalRedirect[0]);
                }
            }
        }

        return null;
    }

    private String determineSlingMappingPath() {
        Map<String, String> environmentMap = ImmutableMap.of(
            "local", "/etc/map.publish.local",
            "dev", "/etc/map.publish.dev",
            "uat", "/etc/map.publish.uat",
            "prod", "/etc/map.publish.prod"
        );

        String pathToSlingMapping = null;
        for (String runMode : slingSettingsService.getRunModes()) {
            if (environmentMap.containsKey(runMode)) {
                pathToSlingMapping = environmentMap.get(runMode);
                break;
            }
        }

        return pathToSlingMapping;
    }

    private void updateScoreForPath(
        final String jcrPath,
        final ResourceResolver resourceResolver,
        final int score) throws RepositoryException {

        Resource resource = resourceResolver.getResource(jcrPath);

        if (resource != null) {
            Resource contentResource = resource.getChild("jcr:content");

            if (contentResource != null) {
                Node node = contentResource.adaptTo(Node.class);
                if (node != null) {
                    LOG.debug("Setting score on {} to {}", node.getPath(), score);
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
                }
            }
        }
    }

    @VisibleForTesting
    String removeExtension(final String resourcePath) {
        int lastIndexOfPeriod = resourcePath.lastIndexOf(".");

        if (lastIndexOfPeriod > -1) {
            return resourcePath.substring(0, lastIndexOfPeriod);
        }
        return resourcePath;
    }
}
