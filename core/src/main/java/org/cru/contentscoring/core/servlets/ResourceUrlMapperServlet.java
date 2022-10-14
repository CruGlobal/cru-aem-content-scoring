package org.cru.contentscoring.core.servlets;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.NonExistingResource;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.cru.commons.util.EnvironmentTypeProvider;
import org.cru.contentscoring.core.provider.AbsolutePathUriProvider;
import org.cru.contentscoring.core.provider.VanityPathUriProvider;
import org.cru.contentscoring.core.util.SystemUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This resource url mapper servlet is used to determine the external URL(s) of a given resource
 * based on the publisher's sling mapping.
 */
@Component(service = Servlet.class, property = {
        "sling.servlet.methods=" + HttpConstants.METHOD_GET,
        "sling.servlet.paths=/bin/cru/url/mapper",
        "sling.servlet.extensions=txt"})
public class ResourceUrlMapperServlet extends SlingSafeMethodsServlet {
    private static final Logger LOG = LoggerFactory.getLogger(ResourceUrlMapperServlet.class);

    private static final String SUBSERVICE = "contentScoreSync";

    AbsolutePathUriProvider absolutePathUriProvider;
    VanityPathUriProvider vanityPathUriProvider;

    @Reference
    SystemUtils systemUtils;

    @Reference
    EnvironmentTypeProvider environmentTypeProvider;

    @Activate
    public void activate() {
        String environment = environmentTypeProvider.getEnvironmentType().getShortCode();

        if (absolutePathUriProvider == null) {
            absolutePathUriProvider = new AbsolutePathUriProvider(environment);
        }
        if (vanityPathUriProvider == null) {
            vanityPathUriProvider = new VanityPathUriProvider(environment);
        }
    }

    @Override
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException {
        RequestParameter[] pathParameters = request.getRequestParameters("path");

        if (pathParameters == null || pathParameters.length == 0) {
            response.setStatus(400);
            response.getWriter().write("Path parameter is missing.");
            return;
        }

        try (ResourceResolver resourceResolver = systemUtils.getResourceResolver(SUBSERVICE)) {
            Set<String> urls = determineUrls(pathParameters, resourceResolver);

            response.setHeader("Content-Type", "application/json");
            ObjectMapper objectMapper = new ObjectMapper();
            String json = objectMapper.writeValueAsString(urls);
            response.getWriter().write(json);
        } catch (LoginException e) {
            LOG.error("Failed to get resource resolver for {}", SUBSERVICE, e);
            response.sendError(500);
        }
    }

    private Set<String> determineUrls(
        final RequestParameter[] pathParameters,
        final ResourceResolver resourceResolver) {

        List<String> paths = Arrays.stream(pathParameters)
            .map(RequestParameter::getString)
            .collect(Collectors.toList());

        Set<String> urls = new HashSet<>();
        for (String path : paths) {
            Resource resource = resourceResolver.getResource(path);
            if (resource != null) {
                URI absoluteUri = absolutePathUriProvider.toURI(resource, resourceResolver);
                if (absoluteUri != null) {
                    urls.add(absoluteUri.toString());
                }
            } else {
                resource = resourceResolver.resolve(path);
                if (resource instanceof NonExistingResource) {
                    continue;
                }
                // This means that a resource exists that can be mapped by the given vanity URL
                URI vanityUri = vanityPathUriProvider.toURI(path, resourceResolver);
                if (vanityUri != null) {
                    urls.add(vanityUri.toString());
                }
            }
        }

        return urls;
    }
}
