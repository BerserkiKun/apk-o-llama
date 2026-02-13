package models;

import java.util.*;
import java.util.stream.Collectors;

public class ScanResult {
    
    private final String apkPath;
    private final List<Finding> findings;
    private final long scanDurationMs;
    private final int filesScanned;
    
    public ScanResult(String apkPath, List<Finding> findings, 
                     long scanDurationMs, int filesScanned) {
        this.apkPath = apkPath;
        this.findings = new ArrayList<>(findings);
        this.scanDurationMs = scanDurationMs;
        this.filesScanned = filesScanned;
    }
    
    public String getApkPath() { return apkPath; }
    public List<Finding> getFindings() { return new ArrayList<>(findings); }
    public long getScanDurationMs() { return scanDurationMs; }
    public int getFilesScanned() { return filesScanned; }
    
    public int getTotalFindings() {
        return findings.size();
    }
    
    public Map<Severity, Long> getFindingsBySeverity() {
        return findings.stream()
            .collect(Collectors.groupingBy(
                Finding::getSeverity, 
                Collectors.counting()
            ));
    }
    
    public List<Finding> getFindingsBySeverity(Severity severity) {
        return findings.stream()
            .filter(f -> f.getSeverity() == severity)
            .collect(Collectors.toList());
    }
}
