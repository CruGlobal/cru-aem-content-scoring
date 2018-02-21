package org.cru.contentscoring.core.models;

import java.math.BigDecimal;

public class ContentScoreUpdateRequest {
    private String contentId;
    private BigDecimal contentScore;

    public String getContentId() {
        return contentId;
    }

    public void setContentId(final String contentId) {
        this.contentId = contentId;
    }

    public BigDecimal getContentScore() {
        return contentScore;
    }

    public void setContentScore(final BigDecimal contentScore) {
        this.contentScore = contentScore;
    }
}
