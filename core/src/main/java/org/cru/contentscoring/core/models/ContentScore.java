package org.cru.contentscoring.core.models;

public class ContentScore {
    private int unaware;
    private int curious;
    private int follower;
    private int guide;
    private int confidence;

    public int getUnaware() {
        return unaware;
    }

    public void setUnaware(final int unaware) {
        this.unaware = unaware;
    }

    public int getCurious() {
        return curious;
    }

    public void setCurious(final int curious) {
        this.curious = curious;
    }

    public int getFollower() {
        return follower;
    }

    public void setFollower(final int follower) {
        this.follower = follower;
    }

    public int getGuide() {
        return guide;
    }

    public void setGuide(final int guide) {
        this.guide = guide;
    }

    public int getConfidence() {
        return confidence;
    }

    public void setConfidence(final int confidence) {
        this.confidence = confidence;
    }
}
