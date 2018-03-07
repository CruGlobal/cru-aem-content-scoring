package org.cru.contentscoring.core.models;

public enum ScoreType {
    UNAWARE("scoreUnaware"),
    CURIOUS("scoreCurious"),
    FOLLOWER("scoreFollower"),
    GUIDE("scoreGuide"),
    CONFIDENCE("scoreConfidence");

    private String propertyName;

    ScoreType(final String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
