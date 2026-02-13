package rules;

import analyzers.Analyzer;
import scanner.FileType;

import java.util.*;

public class RuleRegistry {
    
    private final Map<FileType, List<Analyzer>> analyzerMap;
    
    public RuleRegistry() {
        this.analyzerMap = new HashMap<>();
        for (FileType type : FileType.values()) {
            analyzerMap.put(type, new ArrayList<>());
        }
    }
    
    public void registerAnalyzer(FileType fileType, Analyzer analyzer) {
        analyzerMap.get(fileType).add(analyzer);
        System.out.println("Registered " + analyzer.getAnalyzerName() + " for " + fileType);
    }
    
    public List<Analyzer> getAnalyzers(FileType fileType) {
        return new ArrayList<>(analyzerMap.get(fileType));
    }
    
    public int getAnalyzerCount() {
        return analyzerMap.values().stream()
            .mapToInt(List::size)
            .sum();
    }
}
