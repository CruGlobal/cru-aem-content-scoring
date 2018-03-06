package org.cru.contentscoring.core.models;

public class ContentScoreUpdateRequest {
    private String uri;
    private ContentScore score;

    public String getUri() {
        return uri;
    }

    public void setUri(final String uri) {
        this.uri = uri;
    }

    public ContentScore getScore() {
        return score;
    }

    public void setScore(final ContentScore score) {
        this.score = score;
    }
}
