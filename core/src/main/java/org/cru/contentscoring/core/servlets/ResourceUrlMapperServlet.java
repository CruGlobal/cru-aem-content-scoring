package org.cru.contentscoring.core.servlets;

import com.day.cq.commons.Externalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * This resource url mapper servlet is used to determine the external URL(s) of a given resource
 * based on the publisher's sling mapping.
 */
@SlingServlet(
    methods = "GET",
    paths = "/bin/cru/url/mapper"
)
public class ResourceUrlMapperServlet extends SlingSafeMethodsServlet {
    @Override
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException {
        RequestParameter pathsParameter = request.getRequestParameter("paths");

        if (pathsParameter == null) {
            response.setStatus(400);
            response.getWriter().write("Paths parameter is missing.");
            return;
        }

        RequestParameter domainParameter = request.getRequestParameter("domain");
        if (domainParameter == null) {
            response.setStatus(400);
            response.getWriter().write("Domain parameter is missing.");
            return;
        }

        Set<String> urls = determineUrls(pathsParameter, domainParameter, request.getResourceResolver());

        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(urls);
        response.getWriter().write(json);
    }

    private Set<String> determineUrls(
        final RequestParameter pathsParameter,
        final RequestParameter domainParameter,
        final ResourceResolver resourceResolver) {

        String pathsString = pathsParameter.getString().replace("[", "").replace("]", "");
        String[] paths = pathsString.split(",");

        String domain = domainParameter.getString();

        Externalizer externalizer = resourceResolver.adaptTo(Externalizer.class);


        if (externalizer == null) {
            throw new RuntimeException("Externalizer is null!");
        }

        Set<String> urls = new HashSet<>();
        for (String path : paths) {
            if (resourceResolver.getResource(path) != null) {
                urls.add(externalizer.externalLink(resourceResolver, domain, path) + ".html");
            } else {
                urls.add(externalizer.externalLink(resourceResolver, domain, path));
            }
        }

        return urls;
    }
}
