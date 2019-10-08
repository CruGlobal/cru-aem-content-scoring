package org.cru.contentscoring.core.util;

import java.util.HashMap;
import java.util.Map;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = SystemUtils.class, immediate = true, property = {
        Constants.SERVICE_DESCRIPTION + "=SystemUtils for the Cru content scoring application" })
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
