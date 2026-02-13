package analyzers;

import models.Finding;
import models.Severity;
import utils.EntropyCalculator;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SecretScanner implements Analyzer {
    
    private static final double ENTROPY_THRESHOLD = 4.5;
    private static final int MIN_SECRET_LENGTH = 16;
    
    private static final List<SecretPattern> PATTERNS = List.of(
        new SecretPattern("Google API Key", Pattern.compile("AIza[0-9A-Za-z\\-_]{35}"), Severity.CRITICAL, 0.95),
        new SecretPattern("AWS Access Key", Pattern.compile("AKIA[0-9A-Z]{16}"), Severity.CRITICAL, 0.98),
        new SecretPattern("Stripe API Key", Pattern.compile("sk_live_[0-9a-zA-Z]{24,}"), Severity.CRITICAL, 0.95),
        new SecretPattern("ChatGPT/OpenAI API Key", Pattern.compile("sk-[0-9a-zA-Z]{48,51}"), Severity.CRITICAL, 0.96),
        new SecretPattern("GitHub Token", Pattern.compile("ghp_[0-9a-zA-Z]{36}"), Severity.HIGH, 0.90),
        new SecretPattern("Generic API Key", Pattern.compile("(?i)api[_-]?key[\\s:=]+['\"]([0-9a-zA-Z_\\-]{20,})['\"]"), Severity.HIGH, 0.70),
        new SecretPattern("Password", Pattern.compile("(?i)password[\\s:=]+['\"]([^'\"]{6,})['\"]"), Severity.CRITICAL, 0.80),
        new SecretPattern("OpenAI Organization ID", Pattern.compile("org-[0-9a-zA-Z]{24}"), Severity.MEDIUM, 0.80),
        new SecretPattern("OpenAI Session Token", Pattern.compile("sess-[0-9a-zA-Z]{64}"), Severity.HIGH, 0.85)
    );
    
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
        return "SecretScanner";
    }
    
    @Override
    public List<Finding> analyze(File file) throws Exception {
        List<Finding> findings = new ArrayList<>();
        
        String content = Files.readString(file.toPath());
        String[] lines = content.split("\n");
        
        System.out.println("Scanning for secrets: " + file.getName());
        
        for (SecretPattern pattern : PATTERNS) {
            Matcher matcher = pattern.pattern.matcher(content);
            
            while (matcher.find()) {
                String match = matcher.group();
                int lineNumber = getLineNumber(content, matcher.start());
                
                if (isTestValue(match)) {
                    continue;
                }
                
                findings.add(new Finding(
                    UUID.randomUUID().toString(),
                    "Hardcoded Secret: " + pattern.name,
                    pattern.severity,
                    "Secrets",
                    file.getAbsolutePath(),
                    lineNumber,
                    String.format("Found hardcoded %s.", pattern.name),
                    truncate(match, 50),
                    pattern.confidence
                ));
            }
        }
        
        findings.addAll(detectHighEntropyStrings(lines, file.getAbsolutePath()));
        
        return findings;
    }
    
    private List<Finding> detectHighEntropyStrings(String[] lines, String filePath) {
        List<Finding> findings = new ArrayList<>();
        
        Pattern stringPattern = Pattern.compile("['\"]([A-Za-z0-9+/=]{" + MIN_SECRET_LENGTH + ",})['\"]");
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            if (line.trim().startsWith("//") || line.trim().startsWith("#") || 
                line.trim().startsWith("*")) {
                continue;
            }
            
            Matcher matcher = stringPattern.matcher(line);
            
            while (matcher.find()) {
                String candidate = matcher.group(1);
                
                if (EntropyCalculator.isHighEntropy(candidate, ENTROPY_THRESHOLD)) {
                    
                    boolean nearKeyword = false;
                    String context = getContext(lines, i, 3).toLowerCase(); // Convert to lowercase
                    
                    for (String keyword : SECRET_KEYWORDS) {
                        // Case-insensitive matching for all keywords
                        if (context.contains(keyword.toLowerCase())) {
                            nearKeyword = true;
                            break;
                        }
                    }
                    
                    // Also check for pattern-based detection
                    /*if (nearKeyword && !isTestValue(candidate)) {
                        findings.add(new Finding(
                            UUID.randomUUID().toString(),
                            "High-Entropy String Near Security Keyword",
                            Severity.MEDIUM,
                            "Secrets",
                            filePath,
                            i + 1,
                            "Found high-entropy string near security keywords: " + candidate,
                            truncate(candidate, 100), // Increased from 50 to 100
                            0.75 // Increased confidence
                        ));
                    }*/
                }
            }
            
            // Direct keyword search in line (case-insensitive)
            String lineLower = line.toLowerCase();
            for (String keyword : SECRET_KEYWORDS) {
                if (lineLower.contains(keyword)) {
                    // Find the exact position for evidence
                    int idx = lineLower.indexOf(keyword);
                    int start = Math.max(0, idx - 30);
                    int end = Math.min(line.length(), idx + keyword.length() + 30);
                    String evidence = line.substring(start, end);
                    
                    findings.add(new Finding(
                        UUID.randomUUID().toString(),
                        "Security Keyword Found",
                        Severity.LOW,
                        "Secrets",
                        filePath,
                        i + 1,
                        "Found security-related keyword in code: " + keyword,
                        truncate(evidence, 100),
                        0.60
                    ));
                    break; // One finding per line is enough
                }
            }
            
            // Check for sensitive data logging patterns
            checkSensitiveLogging(line, filePath, i + 1, findings);
        }
        
        return findings;
    }
    
    private boolean containsKeywordWithWildcard(String text, String keyword) {
        String textLower = text.toLowerCase();
        String keywordLower = keyword.toLowerCase();
        
        // Simple wildcard support: * matches any sequence
        if (keywordLower.contains("*")) {
            String regex = keywordLower.replace("*", ".*");
            return textLower.matches(".*" + regex + ".*");
        }
        
        return textLower.contains(keywordLower);
    }

    private boolean isTestValue(String value) {
        String lower = value.toLowerCase();
        return lower.contains("test") || lower.contains("example") || lower.contains("demo") ||
               lower.contains("your_api_key") || lower.contains("placeholder") ||
               lower.equals("") || value.matches("^[a*]+$");
    }
    
    private String getContext(String[] lines, int lineIndex, int range) {
        StringBuilder context = new StringBuilder();
        int start = Math.max(0, lineIndex - range);
        int end = Math.min(lines.length, lineIndex + range + 1);
        
        for (int i = start; i < end; i++) {
            context.append(lines[i]).append(" ");
        }
        
        return context.toString();
    }
    
    private int getLineNumber(String content, int position) {
        return (int) content.substring(0, position).chars().filter(ch -> ch == '\n').count() + 1;
    }
    
    private String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }
    
    private static class SecretPattern {
        String name;
        Pattern pattern;
        Severity severity;
        double confidence;
        
        SecretPattern(String name, Pattern pattern, Severity severity, double confidence) {
            this.name = name;
            this.pattern = pattern;
            this.severity = severity;
            this.confidence = confidence;
        }
    }

    private void checkSensitiveLogging(String line, String filePath, int lineNumber, List<Finding> findings) {
        // Skip commented lines
        String trimmedLine = line.trim();
        if (trimmedLine.startsWith("//") || trimmedLine.startsWith("*") || trimmedLine.startsWith("/*")) {
            return;
        }
        
        // Common logging patterns that might contain sensitive data
        String lineLower = line.toLowerCase();
        
        // Pattern 1: Android Log calls with variable concatenation
        /*if (lineLower.contains("log.") && (lineLower.contains("password") || 
                                            lineLower.contains("token") || 
                                            lineLower.contains("key") || 
                                            lineLower.contains("secret"))) {
            
            // Extract a relevant portion of the line
            int logStart = lineLower.indexOf("log.");
            int end = Math.min(line.length(), logStart + 100);
            String evidence = line.substring(Math.max(0, logStart), end);
            
            findings.add(new Finding(
                UUID.randomUUID().toString(),
                "Potential Sensitive Data Logging",
                Severity.MEDIUM,
                "Logging",
                filePath,
                lineNumber,
                "Log statement found near sensitive data keywords. Review for potential data leakage.",
                truncate(evidence, 150),
                0.65
            ));
        }
        
        // Pattern 2: System.out.println with sensitive patterns
        if ((lineLower.contains("system.out.print") || lineLower.contains("println(")) &&
            (lineLower.contains("=\"") || lineLower.contains(":") || lineLower.contains("password"))) {
            
            findings.add(new Finding(
                UUID.randomUUID().toString(),
                "Console Output with Potential Sensitive Data",
                Severity.LOW,
                "Logging",
                filePath,
                lineNumber,
                "System.out.println found - may contain sensitive data in production builds.",
                truncate(line, 120),
                0.60
            ));
        }*/
        
        // Pattern 3: Toast messages with variable data
        if (lineLower.contains("toast.maketext") && 
            (lineLower.contains("+") || lineLower.contains("format(") || lineLower.contains("concat("))) {
            
            findings.add(new Finding(
                UUID.randomUUID().toString(),
                "Dynamic Toast Message",
                Severity.LOW,
                "Logging",
                filePath,
                lineNumber,
                "Toast message constructed dynamically - may leak sensitive information via UI.",
                truncate(line, 120),
                0.55
            ));
        }
    }
}
