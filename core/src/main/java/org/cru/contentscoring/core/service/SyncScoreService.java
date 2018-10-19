package org.cru.contentscoring.core.service;

import org.apache.sling.api.resource.ResourceResolver;

import javax.jcr.RepositoryException;

public interface SyncScoreService {
    void syncScore(
        ResourceResolver resourceResolver,
        int score,
        String resourcePath,
        String resourceHost,
        String resourceProtocol) throws RepositoryException;
}
