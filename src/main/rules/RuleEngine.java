package rules;

import analyzers.Analyzer;
import analyzers.ManifestAnalyzer;
import analyzers.SecretScanner;
import models.Finding;
import scanner.FileType;
import analyzers.BinaryAnalyzer;           // NEW IMPORT
import analyzers.EnhancedManifestAnalyzer; // NEW IMPORT
import analyzers.CryptographyAnalyzer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RuleEngine {
    
    private final RuleRegistry registry;
    
    public RuleEngine() {
        this.registry = new RuleRegistry();
        initializeDefaultAnalyzers();
    }
    
    private void initializeDefaultAnalyzers() {
        // Register EnhancedManifestAnalyzer for manifests
        registry.registerAnalyzer(FileType.MANIFEST, new ManifestAnalyzer());
        registry.registerAnalyzer(FileType.MANIFEST, new EnhancedManifestAnalyzer());
        
        // Register SecretScanner for source/config files
        SecretScanner secretScanner = new SecretScanner();
        registry.registerAnalyzer(FileType.JAVA_SOURCE, secretScanner);
        registry.registerAnalyzer(FileType.KOTLIN_SOURCE, secretScanner);
        registry.registerAnalyzer(FileType.XML_RESOURCE, secretScanner);
        registry.registerAnalyzer(FileType.CONFIG, secretScanner);
        registry.registerAnalyzer(FileType.SMALI, secretScanner); // NEW: Analyze smali files
        
        // Register BinaryAnalyzer for binary/unknown files
        BinaryAnalyzer binaryAnalyzer = new BinaryAnalyzer();
        registry.registerAnalyzer(FileType.NATIVE_LIB, binaryAnalyzer);
        //registry.registerAnalyzer(FileType.DEX, binaryAnalyzer); // decompiled folder already exist
        registry.registerAnalyzer(FileType.CERTIFICATE, binaryAnalyzer);
        registry.registerAnalyzer(FileType.ASSET, binaryAnalyzer);
        registry.registerAnalyzer(FileType.BINARY_RESOURCE, binaryAnalyzer);
        registry.registerAnalyzer(FileType.UNKNOWN, binaryAnalyzer); // Catch-all for unknown files
        
        // Register CryptographyAnalyzer for source files
        CryptographyAnalyzer cryptoAnalyzer = new CryptographyAnalyzer();
        registry.registerAnalyzer(FileType.JAVA_SOURCE, cryptoAnalyzer);
        registry.registerAnalyzer(FileType.KOTLIN_SOURCE, cryptoAnalyzer);
        registry.registerAnalyzer(FileType.SMALI, cryptoAnalyzer);
        
        System.out.println("Initialized " + registry.getAnalyzerCount() + " analyzers");
    }
    
    public List<Finding> analyzeFiles(Map<FileType, List<File>> categorizedFiles) {
        List<Finding> allFindings = new ArrayList<>();
        
        for (FileType fileType : categorizedFiles.keySet()) {
            List<File> files = categorizedFiles.get(fileType);
            List<Analyzer> analyzers = registry.getAnalyzers(fileType);
            
            if (analyzers.isEmpty() || files.isEmpty()) {
                continue;
            }
            
            System.out.println("\nProcessing " + files.size() + " " + fileType + " files...");
            
            for (File file : files) {
                for (Analyzer analyzer : analyzers) {
                    try {
                        List<Finding> findings = analyzer.analyze(file);
                        allFindings.addAll(findings);
                        
                        if (!findings.isEmpty()) {
                            System.out.println("  " + file.getName() + ": " + findings.size() + " findings");
                        }
                    } catch (Exception e) {
                        System.err.println("Error analyzing " + file.getName() + 
                                         " with " + analyzer.getAnalyzerName() + 
                                         ": " + e.getMessage());
                    }
                }
            }
        }
        
        return allFindings;
    }
    
    public void registerCustomAnalyzer(FileType fileType, Analyzer analyzer) {
        registry.registerAnalyzer(fileType, analyzer);
    }
}
