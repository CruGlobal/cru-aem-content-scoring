package org.cru.contentscoring.core.util;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;

import java.util.HashMap;
import java.util.Map;

@Component(label = "Cru.content.scoring SystemUtils",
    description = "SystemUtils for the Cru content scoring application",
    metatype = true,
    immediate = true)
@Service
public class SystemUtilsImpl implements SystemUtils {
    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Override
    public ResourceResolver getResourceResolver(final String subservice) throws LoginException {
        Map<String, Object> authenticationInfo = new HashMap<>();
        authenticationInfo.put(ResourceResolverFactory.SUBSERVICE, subservice);
        return resourceResolverFactory.getServiceResourceResolver(authenticationInfo);
    }
}
