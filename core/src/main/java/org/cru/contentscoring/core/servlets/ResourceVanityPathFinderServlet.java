package org.cru.contentscoring.core.servlets;

import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.NonExistingResource;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

/**
 * This servlet is good for finding resource paths for home pages and vanity URLs that don't end with an extension.
 */
@SlingServlet(
    paths = "/bin/cru/path/finder",
    metatype = true
)
public class ResourceVanityPathFinderServlet extends SlingSafeMethodsServlet {
    private static final Logger LOG = LoggerFactory.getLogger(ResourceVanityPathFinderServlet.class);

    @Reference
    private QueryBuilder queryBuilder;

    @Override
    protected void doGet(
        final SlingHttpServletRequest request,
        final SlingHttpServletResponse response) throws IOException {

        String incomingPath = request.getParameter("path");
        LOG.debug("Incoming path: {}", incomingPath);
        ResourceResolver resourceResolver = request.getResourceResolver();
        try {
            ResolverRequest resolverRequest = new ResolverRequest(request, incomingPath);
            Resource resource = resourceResolver.resolve(resolverRequest, resolverRequest.getPathInfo());

            // This will be the case if resourceResolver found a vanity path (e.g. ministry designation pages)
            if (resource instanceof NonExistingResource) {
                LOG.debug("Resource is non-existing, looking at vanity paths.");
                Resource parent = resourceResolver.resolve("/content");

                try {
                    resource = searchForResourceWithVanityPath(resource.getPath(), parent, resourceResolver);

                    if (resource == null) {
                        return;
                    }
                } catch (RepositoryException e) {
                    response.sendError(500);
                    return;
                }
            }
            LOG.debug("Returning {}", resource.getPath());
            response.getWriter().write(resource.getPath());
        } catch (URISyntaxException e) {
            response.sendError(400, "Invalid URI");
        }
    }

    private Resource searchForResourceWithVanityPath(
        final String vanityPath,
        final Resource parent,
        final ResourceResolver resourceResolver) throws RepositoryException {


        Map<String, String> searchPredicates = Maps.newHashMap();
        searchPredicates.put("property", "sling:vanityPath");
        searchPredicates.put("property.value", vanityPath);

        searchPredicates.put("type", "cq:PageContent");
        searchPredicates.put("path", parent.getPath());

        Session session = resourceResolver.adaptTo(Session.class);
        Query query = queryBuilder.createQuery(PredicateGroup.create(searchPredicates), session);
        SearchResult searchResult = query.getResult();
        List<Hit> hits = searchResult.getHits();

        if (hits.isEmpty()) {
            LOG.debug("No resource found for {}", vanityPath);
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
}
