package org.cru.contentscoring.core.provider;

import org.apache.http.client.utils.URIBuilder;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.external.URIProvider;
import org.cru.contentscoring.core.util.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

public class VanityPathUriProvider implements URIProvider {
    private static final Logger LOG = LoggerFactory.getLogger(VanityPathUriProvider.class);

    private static final String SUBSERVICE = "contentScoreSync";

    private String environment;
    private final SystemUtils systemUtils;

    public VanityPathUriProvider(final String environment, final SystemUtils systemUtils) {
        this.environment = environment;
        this.systemUtils = systemUtils;
    }

    @Override
    public URI toURI(final Resource resource, final Scope scope, final Operation operation) {
        try (ResourceResolver resourceResolver = systemUtils.getResourceResolver(SUBSERVICE)) {
            return toURI(resource.getPath(), resourceResolver);
        } catch (LoginException e) {
            LOG.error("Failed to get resource resolver for {}", SUBSERVICE);
        }
        return null;
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
