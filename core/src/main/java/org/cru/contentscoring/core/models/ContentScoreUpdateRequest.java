package org.cru.contentscoring.core.models;

public class ContentScoreUpdateRequest {
    private String uri;
    private int score;

    public String getUri() {
        return uri;
    }

    public void setUri(final String uri) {
        this.uri = uri;
    }

    public int getScore() {
        return score;
    }

    public void setScore(final int score) {
        this.score = score;
    }
}
