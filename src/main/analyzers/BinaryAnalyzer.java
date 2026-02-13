package analyzers;

import models.Finding;
import models.Severity;
import utils.EntropyCalculator;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class BinaryAnalyzer implements Analyzer {
    
    private static final double ENTROPY_THRESHOLD = 4.0; // Lower for binary files
    private static final int MIN_SECRET_LENGTH = 12;
    
    // Comprehensive keyword list from requirement
    private static final List<String> SECRET_KEYWORDS = List.of(
        "api_key", "client_id", "aws_id", "aws_secret", "app_id", "storage_bucket",
        "passcode", "passwd", "passwords",
        "password\":", "clientid", "tenantid",
        "tenant", "syncfusion", "ckeditor", "ckeditorlicensekey", "license",
        "licensekey", "syncfusion_license", "viber", "calllink",
        "cryptosecret", "cryptosecretkey", "cryptokey",
        "amazonaws", "bucket\"", "private_key", "private_key\":", "pubkey",
        "credit_card", "rupay", "apiurl", "workspace", "shareid", "webspellcheckerbundlepath",
        "wscservice", "wscbundle", "npm_config_globalignorefile",
        "jenkins_url", "jenkins_links", "git+ssh", "npm_config_globalconfig", "ssh_connection",
        "syncfusionlicensekey", "const_url", "jirascript.src", "syncfusionlicense", "nreum",
        "reset_password_key", "last_reset_password_generated_on", "jazzhr_api_key_data",
        "agentid", "accountid", "xpid", "licensekey", "trustkey",
        "cspapiurl", "client_secret", "firebase_url", "firebase.auth.API_KEY", "keen", "Gmap", "api key"
        
        // Add for better cryptography and logging detection
        , "initializationvector", "keypair\":"

        // Add these OpenAI/ChatGPT related keywords
        , "openai", "chatgpt", "gpt", "openai_key", "openai_secret",
        "openai_api", "chatgpt_api", "llm_key", "llm_secret",
        "openai_key\":", "openai_secret\":", "openai.api"
    );
    
    @Override
    public String getAnalyzerName() {
        return "BinaryAnalyzer";
    }
    
    @Override
    public List<Finding> analyze(File file) throws Exception {
        List<Finding> findings = new ArrayList<>();
        
        System.out.println("Analyzing binary file: " + file.getName());
        
        // Read file as bytes for binary analysis
        byte[] fileBytes = Files.readAllBytes(file.toPath());
        
        // Convert to ASCII string (non-ASCII becomes dots)
        String asciiContent = new String(fileBytes, "ISO-8859-1");
        
        findings.addAll(scanForSecrets(asciiContent, file.getAbsolutePath()));
        
        // Check for specific binary patterns
        findings.addAll(checkBinaryPatterns(fileBytes, file.getAbsolutePath()));
        
        return findings;
    }
    
    private List<Finding> scanForSecrets(String content, String filePath) {
        List<Finding> findings = new ArrayList<>();
        
        // Pattern for quoted strings (single or double quotes)
        Pattern stringPattern = Pattern.compile("['\"]([^'\"]{6,})['\"]");
        Matcher matcher = stringPattern.matcher(content);
        
        while (matcher.find()) {
            String candidate = matcher.group(1);
            
            // Check if string is near any secret keyword (case-insensitive)
            int start = Math.max(0, matcher.start() - 100);
            int end = Math.min(content.length(), matcher.end() + 100);
            String context = content.substring(start, end).toLowerCase();
            
            boolean nearKeyword = false;
            for (String keyword : SECRET_KEYWORDS) {
                if (context.contains(keyword)) {
                    nearKeyword = true;
                    break;
                }
            }
            
            if (nearKeyword) {
                // Check entropy for suspicious strings
                /*if (EntropyCalculator.isHighEntropy(candidate, ENTROPY_THRESHOLD) &&
                    !isTestValue(candidate)) {
                    
                    findings.add(new Finding(
                        UUID.randomUUID().toString(),
                        "Potential Secret in Binary File",
                        Severity.MEDIUM,
                        "Binary Analysis",
                        filePath,
                        -1, // No line numbers in binary files
                        "Found high-entropy string near security keyword in binary file.",
                        truncate(candidate, 200),
                        0.65
                    ));
                }*/
            }
        }
        
        // Direct keyword search in binary
        /*String contentLower = content.toLowerCase();
        for (String keyword : SECRET_KEYWORDS) {
            int idx = contentLower.indexOf(keyword);
            if (idx >= 0) {
                // Extract context around keyword
                int start = Math.max(0, idx - 50);
                int end = Math.min(content.length(), idx + keyword.length() + 50);
                String evidence = content.substring(start, end);
                
                findings.add(new Finding(
                    UUID.randomUUID().toString(),
                    "Security Keyword in Binary File",
                    Severity.LOW,
                    "Binary Analysis",
                    filePath,
                    -1,
                    "Found security-related keyword in binary file content.",
                    truncate(evidence, 200),
                    0.50
                ));
            }
        }*/
        
        return findings;
    }
    
    private List<Finding> checkBinaryPatterns(byte[] bytes, String filePath) {
        List<Finding> findings = new ArrayList<>();
        
        // Check for common certificate/private key patterns
        String hex = bytesToHex(bytes);
        
        // RSA private key pattern
        if (hex.contains("2d2d2d2d2d424547494e205253412050524956415445204b45592d2d2d2d2d") ||  // -----BEGIN RSA PRIVATE KEY-----
            hex.contains("2d2d2d2d2d424547494e2050524956415445204b45592d2d2d2d2d")) {    // -----BEGIN PRIVATE KEY-----
            
            findings.add(new Finding(
                UUID.randomUUID().toString(),
                "Potential Private Key in Binary",
                Severity.CRITICAL,
                "Binary Analysis",
                filePath,
                -1,
                "Found pattern matching RSA private key in binary file.",
                "Contains -----BEGIN PRIVATE KEY----- pattern",
                0.85
            ));
        }
        
        // Certificate pattern
        if (hex.contains("2d2d2d2d2d424547494e2043455254494649434154452d2d2d2d2d")) {  // -----BEGIN CERTIFICATE-----
            findings.add(new Finding(
                UUID.randomUUID().toString(),
                "Certificate Embedded in Binary",
                Severity.LOW,
                "Binary Analysis",
                filePath,
                -1,
                "Found embedded certificate in binary file.",
                "Contains -----BEGIN CERTIFICATE----- pattern",
                0.80
            ));
        }
        
        return findings;
    }
    
    private boolean isTestValue(String value) {
        String lower = value.toLowerCase();
        return lower.contains("test") || lower.contains("example") || 
               lower.contains("demo") || lower.contains("placeholder") ||
               lower.contains("changeme") || lower.contains("your_") ||
               value.matches("^[a*0-9*]+$") || value.length() < 4;
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
    
    private String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...";
    }
}