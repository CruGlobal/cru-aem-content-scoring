package org.cru.contentscoring.core.provider;

import org.apache.http.client.utils.URIBuilder;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.external.URIProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

public class VanityPathUriProvider implements URIProvider {
    private static final Logger LOG = LoggerFactory.getLogger(VanityPathUriProvider.class);

    @Override
    public URI toURI(final Resource resource, final Scope scope, final Operation operation) {
        return toURI(resource.getPath(), resource.getResourceResolver());
    }

    public URI toURI(final String path, final ResourceResolver resourceResolver) {
        Resource slingMap = UriProviderUtil.determineSlingMap(path, resourceResolver);

        if (slingMap != null) {
            String protocol = Objects.requireNonNull(slingMap.getParent()).getName();
            String domain = slingMap.getName();

            try {
                return new URIBuilder()
                    .setScheme(protocol)
                    .setHost(domain)
                    .setPath(path)
                    .build();
            } catch (URISyntaxException e) {
                LOG.error("Bad URI", e);
            }
        }

        return null;
    }
}
