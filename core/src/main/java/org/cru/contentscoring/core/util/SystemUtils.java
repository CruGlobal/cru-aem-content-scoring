package org.cru.contentscoring.core.util;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;

public interface SystemUtils {
    /**
     * Retrieves the correct resource resolver for the subservice with according permissions.
     *
     * @param subservice the subservice the system user is registered with in User Mapper OSGI Config
     * @return the ResourceResolver instance with correct system user permissions
     * @throws LoginException if system user can't be retrieved correctly
     */
    ResourceResolver getResourceResolver(final String subservice) throws LoginException;
}
