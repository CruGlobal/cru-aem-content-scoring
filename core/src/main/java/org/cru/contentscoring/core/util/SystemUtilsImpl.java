package org.cru.contentscoring.core.util;

import com.day.cq.replication.ReplicationActionType;
import com.day.cq.replication.ReplicationException;
import com.day.cq.replication.Replicator;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;

import javax.jcr.Session;
import java.util.HashMap;
import java.util.Map;

@Component(label = "Cru.content.scoring SystemUtils",
    description = "SystemUtils for the Cru content scoring application",
    metatype = true,
    immediate = true)
@Service
public class SystemUtilsImpl implements SystemUtils {
    private static final String REPLICATION_SUBSERVICE = "contentScoreReplication";

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Reference
    private Replicator replicator;

    @Override
    public ResourceResolver getResourceResolver(final String subservice) throws LoginException {
        Map<String, Object> authenticationInfo = new HashMap<>();
        authenticationInfo.put(ResourceResolverFactory.SUBSERVICE, subservice);
        return resourceResolverFactory.getServiceResourceResolver(authenticationInfo);
    }

    @Override
    public void replicatePage(final String pagePath) throws LoginException, ReplicationException {
        try (ResourceResolver resolver = this.getResourceResolver(REPLICATION_SUBSERVICE)) {
            replicator.replicate(resolver.adaptTo(Session.class), ReplicationActionType.ACTIVATE, pagePath);
        }
    }
}
