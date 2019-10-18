package org.cru.contentscoring.core.provider;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;

public class UriProviderUtil {
    private UriProviderUtil() {}

    public static Resource determineSlingMap(final String path, final ResourceResolver resourceResolver) {
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

    private static Resource loopThroughSlingMaps(final Resource parent, final String path) {
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
