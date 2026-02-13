package core;

import models.Finding;
import models.Severity;

import java.util.*;
import java.util.stream.Collectors;

public class FindingCollector {
    
    private final List<Finding> findings;
    
    public FindingCollector() {
        this.findings = new ArrayList<>();
    }
    
    public void addFinding(Finding finding) {
        findings.add(finding);
    }
    
    public void addFindings(List<Finding> findings) {
        this.findings.addAll(findings);
    }
    
    public List<Finding> getAllFindings() {
        return new ArrayList<>(findings);
    }
    
    public void printConsole() {
        System.out.println("\n========================================");
        System.out.println("        SECURITY FINDINGS REPORT        ");
        System.out.println("========================================\n");
        
        Map<Severity, List<Finding>> bySeverity = findings.stream()
            .collect(Collectors.groupingBy(Finding::getSeverity));
        
        System.out.println("SUMMARY:");
        System.out.println("  Total findings: " + findings.size());

        // NEW: Count AI-analyzed findings
        long aiAnalyzed = findings.stream()
            .filter(Finding::isAiAnalysisCompleted)
            .count();
        System.out.println("  AI-analyzed findings: " + aiAnalyzed);

        for (Severity sev : Severity.values()) {
            long count = bySeverity.getOrDefault(sev, new ArrayList<>()).size();
            if (count > 0) {
                System.out.printf("  %s: %d\n", sev, count);
            }
        }
        
        System.out.println("\n========================================\n");
        
        List<Severity> orderedSeverities = Arrays.asList(
            Severity.CRITICAL,
            Severity.HIGH,
            Severity.MEDIUM,
            Severity.LOW,
            Severity.INFO
        );
        
        for (Severity severity : orderedSeverities) {
            List<Finding> severityFindings = bySeverity.getOrDefault(severity, new ArrayList<>());
            
            if (severityFindings.isEmpty()) continue;
            
            System.out.println("=== " + severity + " FINDINGS ===\n");
            
            for (Finding f : severityFindings) {
                System.out.println("• " + f.getTitle());
                System.out.println("  Category: " + f.getCategory());
                System.out.println("  File: " + f.getFilePath());
                if (f.getLineNumber() > 0) {
                    System.out.println("  Line: " + f.getLineNumber());
                }
                System.out.println("  Confidence: " + String.format("%.0f%%", f.getConfidence() * 100));
                System.out.println("  Description: " + f.getDescription());
                System.out.println("  Evidence: " + f.getEvidence());
                System.out.println();
            }
        }
    }
    
    // NEW METHOD: Get findings with AI analysis
    public List<Finding> getFindingsWithAiAnalysis() {
        return findings.stream()
            .filter(Finding::isAiAnalysisCompleted)
            .collect(Collectors.toList());
    }
    
    // NEW METHOD: Get findings in progress
    public List<Finding> getFindingsWithAiInProgress() {
        return findings.stream()
            .filter(Finding::isAiAnalysisInProgress)
            .collect(Collectors.toList());
    }

    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"findings\": [\n");
        
        for (int i = 0; i < findings.size(); i++) {
            Finding f = findings.get(i);
            json.append("    {\n");
            json.append("      \"id\": \"").append(f.getId()).append("\",\n");
            json.append("      \"title\": \"").append(escapeJson(f.getTitle())).append("\",\n");
            json.append("      \"severity\": \"").append(f.getSeverity()).append("\",\n");
            json.append("      \"category\": \"").append(f.getCategory()).append("\",\n");
            json.append("      \"file\": \"").append(escapeJson(f.getFilePath())).append("\",\n");
            json.append("      \"line\": ").append(f.getLineNumber()).append(",\n");
            json.append("      \"confidence\": ").append(f.getConfidence()).append(",\n");
            json.append("      \"description\": \"").append(escapeJson(f.getDescription())).append("\",\n");
            json.append("      \"evidence\": \"").append(escapeJson(f.getEvidence())).append("\"\n");
            json.append("    }");
            
            if (i < findings.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        
        json.append("  ],\n");
        json.append("  \"total\": ").append(findings.size()).append("\n");
        json.append("}\n");
        
        return json.toString();
    }
    
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
