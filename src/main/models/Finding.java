package models;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Finding {
    
    private final String id;
    private final String title;
    private final Severity severity;
    private final String category;
    private final String filePath;
    private final int lineNumber;
    private final String description;
    private final String evidence;
    private final double confidence;
    private final Instant detectedAt;
    private final List<String> tags;
    // NEW FIELDS FOR AI ANALYSIS
    private String aiAnalysis;          // Stores the AI-generated analysis
    private AiAnalysisStatus aiStatus;  // Tracks AI analysis state
    
    public Finding(String id, String title, Severity severity, String category,
                   String filePath, int lineNumber, String description, 
                   String evidence, double confidence) {
        this.id = id;
        this.title = title;
        this.severity = severity;
        this.category = category;
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.description = description;
        this.evidence = evidence;
        this.confidence = Math.max(0.0, Math.min(1.0, confidence));
        this.detectedAt = Instant.now();
        this.tags = new ArrayList<>();
        // Initialize AI analysis fields
        this.aiAnalysis = null;
        this.aiStatus = AiAnalysisStatus.NOT_STARTED;
    }
    
    public String getId() { return id; }
    public String getTitle() { return title; }
    public Severity getSeverity() { return severity; }
    public String getCategory() { return category; }
    public String getFilePath() { return filePath; }
    public int getLineNumber() { return lineNumber; }
    public String getDescription() { return description; }
    public String getEvidence() { return evidence; }
    public double getConfidence() { return confidence; }
    public Instant getDetectedAt() { return detectedAt; }
    public List<String> getTags() { return new ArrayList<>(tags); }
    
    public void addTag(String tag) {
        if (!tags.contains(tag)) {
            tags.add(tag);
        }
    }
    
    @Override
    public String toString() {
        return String.format("[%s] %s - %s (confidence: %.2f)", 
            severity, title, filePath, confidence);
    }

    // NEW GETTERS AND SETTERS FOR AI ANALYSIS
    public String getAiAnalysis() { return aiAnalysis; }
    public AiAnalysisStatus getAiStatus() { return aiStatus; }
    
    public void setAiAnalysis(String analysis) { 
        this.aiAnalysis = analysis; 
    }
    
    public void setAiStatus(AiAnalysisStatus status) { 
        this.aiStatus = status; 
    }
    
    // NEW: Reset AI analysis state
    public void resetAiAnalysis() {
        this.aiAnalysis = null;
        this.aiStatus = AiAnalysisStatus.NOT_STARTED;
    }
    
    // NEW: Check if AI analysis is in progress
    public boolean isAiAnalysisInProgress() {
        return aiStatus == AiAnalysisStatus.IN_PROGRESS;
    }
    
    // NEW: Check if AI analysis is completed
    public boolean isAiAnalysisCompleted() {
        return aiStatus == AiAnalysisStatus.COMPLETED;
    }

    // NEW ENUM FOR AI ANALYSIS STATUS
    public enum AiAnalysisStatus {
        NOT_STARTED,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
}
