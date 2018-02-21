package org.cru.contentscoring.core.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.text.MessageFormat;
import java.util.List;

public class RetryElement {
    private List<ContentScoreUpdateRequest> batch;
    private int retries;

    public RetryElement(List<ContentScoreUpdateRequest> batch, int retries) {
        this.batch = batch;
        this.retries = retries;
    }

    public List<ContentScoreUpdateRequest> getBatch() {
        return batch;
    }

    public void setBatch(final List<ContentScoreUpdateRequest> batch) {
        this.batch = batch;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(final int retries) {
        this.retries = retries;
    }

    public int incrementRetries() {
        return retries++;
    }

    @Override
    public String toString() {
        ObjectMapper objectMapper = new ObjectMapper();
        String batchJson;
        try {
            batchJson = objectMapper.writeValueAsString(batch);
        } catch (JsonProcessingException e) {
            batchJson = "Failed to process JSON";
        }

        return MessageFormat.format("RetryElement [batch = {0}, retries = {1}]", batchJson, retries);
    }
}
