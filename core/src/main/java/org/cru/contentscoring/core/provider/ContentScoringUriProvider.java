package org.cru.contentscoring.core.provider;

import org.apache.http.client.utils.URIBuilder;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.resource.external.URIProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

public class ContentScoringUriProvider implements URIProvider {
    private static final Logger LOG = LoggerFactory.getLogger(ContentScoringUriProvider.class);

    @Override
    public URI toURI(final Resource resource, final Scope scope, final Operation operation) {
        String path = resource.getPath();
        ResourceResolver resourceResolver = resource.getResourceResolver();

        Resource slingMap = determineSlingMap(path, resourceResolver);

        if (slingMap != null) {
            String protocol = Objects.requireNonNull(slingMap.getParent()).getName();
            String domain = slingMap.getName();

            try {
                return new URIBuilder()
                    .setScheme(protocol)
                    .setHost(domain)
                    .setPath(path + ".html")
                    .build();
            } catch (URISyntaxException e) {
                LOG.error("Bad URI", e);
            }
        }

        return null;
    }

    private Resource determineSlingMap(final String path, final ResourceResolver resourceResolver) {
        String httpsPath = "/etc/map.publish.prod/https";
        Resource httpsRoot = resourceResolver.getResource(httpsPath);

        Resource slingMap = null;
        if (httpsRoot != null) {
            slingMap = loopThroughSlingMaps(httpsRoot, path);
        }

        String httpPath = "/etc/map.publish.prod/http";
        Resource httpRoot = resourceResolver.getResource(httpPath);
        if (httpRoot != null && slingMap == null) {
            slingMap = loopThroughSlingMaps(httpRoot, path);
        }

        return slingMap;
    }

    private Resource loopThroughSlingMaps(final Resource parent, final String path) {
        if (parent.hasChildren()) {
            for (Resource child : parent.getChildren()) {
                if (child.getName().contains("_")) {
                    continue;
                }

                ValueMap properties = child.getValueMap();
                String internalRedirect = properties.get("sling:internalRedirect", String.class);
                if (internalRedirect != null && path.startsWith(internalRedirect)) {
                    return child;
                }
            }
        }
        return null;
    }
}
