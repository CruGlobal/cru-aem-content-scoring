package org.cru.contentscoring.core.service;

import com.day.cq.wcm.api.Page;

import javax.jcr.RepositoryException;

public interface ContentScoreUpdateService {
    /**
     * Triggers a request to update the content score for the given content page.
     */
    void updateContentScore(Page page) throws RepositoryException;
}
