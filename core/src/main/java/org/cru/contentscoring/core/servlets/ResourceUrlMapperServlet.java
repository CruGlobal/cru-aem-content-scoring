package org.cru.contentscoring.core.servlets;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.NonExistingResource;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.external.URIProvider;
import org.apache.sling.api.resource.external.URIProvider.Scope;
import org.apache.sling.api.resource.external.URIProvider.Operation;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.cru.contentscoring.core.provider.AbsolutePathUriProvider;
import org.cru.contentscoring.core.provider.VanityPathUriProvider;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import com.day.cq.commons.Externalizer;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * This resource url mapper servlet is used to determine the external URL(s) of a given resource
 * based on the publisher's sling mapping.
 */
@Component(service = Servlet.class, property = {
        "sling.servlet.methods=" + HttpConstants.METHOD_GET,
        "sling.servlet.paths=/bin/cru/url/mapper" })
public class ResourceUrlMapperServlet extends SlingSafeMethodsServlet {
    URIProvider absolutePathUriProvider;
    VanityPathUriProvider vanityPathUriProvider;

    @Activate
    public void activate() {
        if (absolutePathUriProvider == null) {
            absolutePathUriProvider = new AbsolutePathUriProvider();
        }
        if (vanityPathUriProvider == null) {
            vanityPathUriProvider = new VanityPathUriProvider();
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

        Set<String> urls;
        RequestParameter domainParameter = request.getRequestParameter("domain");
        if (domainParameter == null) {
            urls = determineUrls(pathParameters, request.getResourceResolver());
        } else {
            urls = determineUrls(pathParameters, domainParameter, request.getResourceResolver());
        }

        response.setHeader("Content-Type", "application/json");
        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(urls);
        response.getWriter().write(json);
    }

    private Set<String> determineUrls(
        final RequestParameter[] pathParameters,
        final RequestParameter domainParameter,
        final ResourceResolver resourceResolver) {

        List<String> paths = Arrays.stream(pathParameters)
            .map(RequestParameter::getString)
            .collect(Collectors.toList());

        String domain = domainParameter.getString();

        Externalizer externalizer = resourceResolver.adaptTo(Externalizer.class);


        if (externalizer == null) {
            throw new RuntimeException("Externalizer is null!");
        }

        Set<String> urls = new HashSet<>();
        for (String path : paths) {
            final String url = externalizer.externalLink(resourceResolver, domain, path);
            if (resourceResolver.getResource(path) != null) {
                urls.add(url + ".html");
            } else {
                urls.add(url);
            }
        }

        return urls;
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
                urls.add(absolutePathUriProvider.toURI(resource, Scope.EXTERNAL, Operation.READ).toString());
            } else {
                resource = resourceResolver.resolve(path);
                if (resource instanceof NonExistingResource) {
                    continue;
                }
                // This means that a resource exists that can be mapped by the given vanity URL
                urls.add(vanityPathUriProvider.toURI(path, resourceResolver).toString());
            }
        }

        return urls;
    }
}
