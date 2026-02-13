package ai;

import models.Finding;
import models.AIStatus;

import java.time.Instant;

public class OllamaRequest {
    private final String requestId;
    private final Finding finding;
    private final String prompt;
    private final Instant createdAt;
    private Instant updatedAt;
    private AIStatus status;
    private String response;
    private String error;
    private int retryCount;
    private int lineNumber; // For table row mapping
    private volatile boolean cancelled; // For cancellation support
    
    public OllamaRequest(Finding finding, String prompt, int lineNumber) {
        this.requestId = generateRequestId(finding);
        this.finding = finding;
        this.prompt = prompt;
        this.lineNumber = lineNumber;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.status = AIStatus.PENDING;
        this.retryCount = 0;
        this.cancelled = false;
    }
    
    private String generateRequestId(Finding finding) {
        return "ai_" + finding.getId() + "_" + Instant.now().toEpochMilli();
    }
    
    // Getters
    public String getRequestId() { return requestId; }
    public Finding getFinding() { return finding; }
    public String getPrompt() { return prompt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public AIStatus getStatus() { return status; }
    public String getResponse() { return response; }
    public String getError() { return error; }
    public int getRetryCount() { return retryCount; }
    public int getLineNumber() { return lineNumber; }
    public boolean isCancelled() { return cancelled; }
    
    public void cancel() {
        this.cancelled = true;
        this.status = AIStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }
    
    // Setters with update timestamp
    public void setStatus(AIStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
    
    public void setResponse(String response) {
        this.response = response;
        this.updatedAt = Instant.now();
    }
    
    public void setError(String error) {
        this.error = error;
        this.updatedAt = Instant.now();
    }
    
    public void incrementRetryCount() {
        this.retryCount++;
        this.updatedAt = Instant.now();
    }
    
    public void resetForRetry() {
        this.status = AIStatus.PENDING;
        this.response = null;
        this.error = null;
        this.updatedAt = Instant.now();
    }
    
    public boolean shouldRetry(int maxRetries) {
        return status.isRetryable() && retryCount < maxRetries;
    }
    
    @Override
    public String toString() {
        return String.format("OllamaRequest[%s, %s, retries=%d]", 
            requestId, status, retryCount);
    }
}