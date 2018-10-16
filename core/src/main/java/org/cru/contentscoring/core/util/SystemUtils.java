package org.cru.contentscoring.core.util;

import com.day.cq.replication.ReplicationException;
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

    /**
     * Gets the correct system user with permissions to replicate designation pages and replicates
     * the page specified by pagePath.
     *
     * @param pagePath the path to the page that gets activated
     * @throws LoginException if system user can't be retrieved correctly
     * @throws ReplicationException if replication fails
     */
    void replicatePage(final String pagePath) throws LoginException, ReplicationException;
}
