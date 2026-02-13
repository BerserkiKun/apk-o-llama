package models;

public enum AIStatus {
    NOT_REQUESTED("Not Requested"),
    PENDING("Pending"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    FAILED("Failed - Click to Retry"),
    TIMEOUT("Timeout - Click to Retry"),
    RATE_LIMITED("Rate Limited - Click to Retry");
    
    private final String displayName;
    
    AIStatus(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public boolean isRetryable() {
        return this == FAILED || this == TIMEOUT || this == RATE_LIMITED;
    }
    
    public boolean isFinal() {
        return this == COMPLETED || this == FAILED || this == TIMEOUT || this == RATE_LIMITED || this == CANCELLED;
    }
    
    public boolean isCancellable() {
        return this == PENDING || this == IN_PROGRESS;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}