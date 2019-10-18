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

public class AbsolutePathUriProvider implements URIProvider {
    private static final Logger LOG = LoggerFactory.getLogger(AbsolutePathUriProvider.class);

    private String environment;

    public AbsolutePathUriProvider(final String environment) {
        this.environment = environment;
    }

    @Override
    public URI toURI(final Resource resource, final Scope scope, final Operation operation) {
        String path = resource.getPath();
        ResourceResolver resourceResolver = resource.getResourceResolver();

        UriProviderUtil util = UriProviderUtil.getInstance(environment);
        Resource slingMap = util.determineSlingMap(path, resourceResolver);

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
}
