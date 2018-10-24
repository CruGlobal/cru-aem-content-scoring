package org.cru.contentscoring.core.service;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import javax.jcr.RepositoryException;

public interface SyncScoreService {
    /**
     * Saves the given {@param score} to the page that was requested. This will then reverse replicate
     * the change to the author environment, where we actually care about the value.
     *
     * @param resourceResolver the subsystem resource resolver (not the request resource resolver)
     * @param resource the resource on which to save the score
     */
    void syncScore(
        ResourceResolver resourceResolver,
        int score,
        Resource resource) throws RepositoryException;
}
