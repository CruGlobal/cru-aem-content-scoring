package org.cru.contentscoring.core.service;

import com.day.cq.replication.ReplicationException;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;

import javax.jcr.RepositoryException;

public interface SyncScoreService {
    void syncScore(ResourceResolver resourceResolver, int score, String resourcePath, String resourceHost)
        throws RepositoryException, ReplicationException, LoginException;
}
