package org.cru.contentscoring.core.models;

import java.math.BigDecimal;

public class ContentScore {
    private BigDecimal unaware;
    private BigDecimal curious;
    private BigDecimal follower;
    private BigDecimal guide;
    private BigDecimal confidence;

    public BigDecimal getUnaware() {
        return unaware;
    }

    public void setUnaware(final BigDecimal unaware) {
        this.unaware = unaware;
    }

    public BigDecimal getCurious() {
        return curious;
    }

    public void setCurious(final BigDecimal curious) {
        this.curious = curious;
    }

    public BigDecimal getFollower() {
        return follower;
    }

    public void setFollower(final BigDecimal follower) {
        this.follower = follower;
    }

    public BigDecimal getGuide() {
        return guide;
    }

    public void setGuide(final BigDecimal guide) {
        this.guide = guide;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(final BigDecimal confidence) {
        this.confidence = confidence;
    }
}
