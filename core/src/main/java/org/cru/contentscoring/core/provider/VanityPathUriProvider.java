package org.cru.contentscoring.core.provider;

import org.apache.http.client.utils.URIBuilder;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

public class VanityPathUriProvider {
    private static final Logger LOG = LoggerFactory.getLogger(VanityPathUriProvider.class);

    private String environment;

    public VanityPathUriProvider(final String environment) {
        this.environment = environment;
    }

    public URI toURI(final String path, final ResourceResolver resourceResolver) {
        UriProviderUtil util = UriProviderUtil.getInstance(environment);
        Resource slingMap = util.determineSlingMap(path, resourceResolver);

        if (slingMap != null) {
            String protocol = Objects.requireNonNull(slingMap.getParent()).getName();
            String domain = slingMap.getName();
            String pathPartToRemove = Objects.requireNonNull(
                slingMap.getValueMap().get("sling:internalRedirect", String.class));

            String externalPath = path;
            if (path.startsWith(pathPartToRemove)) {
                externalPath = path.substring(pathPartToRemove.length());
            }

            try {
                return new URIBuilder()
                    .setScheme(protocol)
                    .setHost(domain)
                    .setPath(externalPath)
                    .build();
            } catch (URISyntaxException e) {
                LOG.error("Bad URI", e);
            }
        }

        return null;
    }
}
