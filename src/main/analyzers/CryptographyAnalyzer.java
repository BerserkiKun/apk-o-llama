package analyzers;

import models.Finding;
import models.Severity;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class CryptographyAnalyzer implements Analyzer {
    
    private static final List<CryptoPattern> INSECURE_CRYPTO_PATTERNS = List.of(
        // Weak algorithms
        new CryptoPattern("DES Algorithm", 
            Pattern.compile("Cipher\\.getInstance\\([\"']DES/"),
            Severity.HIGH, 0.95, "DES is a weak encryption algorithm"),
            
        new CryptoPattern("RC4 Algorithm", 
            Pattern.compile("Cipher\\.getInstance\\([\"']RC4"),
            Severity.HIGH, 0.95, "RC4 is a weak stream cipher"),
            
        new CryptoPattern("MD5 Hash", 
            Pattern.compile("MessageDigest\\.getInstance\\([\"']MD5[\"']\\)"),
            Severity.MEDIUM, 0.90, "MD5 is cryptographically broken"),
            
        new CryptoPattern("SHA1 Hash", 
            Pattern.compile("MessageDigest\\.getInstance\\([\"']SHA-?1[\"']\\)"),
            Severity.MEDIUM, 0.85, "SHA-1 is deprecated for security purposes"),
        
        // Insecure modes
        new CryptoPattern("ECB Mode", 
            Pattern.compile("Cipher\\.getInstance\\([\"'](?:AES|DES)/ECB/"),
            Severity.HIGH, 0.98, "ECB mode is insecure for most uses"),
        
        // Weak random number generators
        new CryptoPattern("Insecure Random", 
            Pattern.compile("new\\s+Random\\s*\\(\\s*\\)"),
            Severity.MEDIUM, 0.80, "java.util.Random is not cryptographically secure"),
        
        // Hardcoded keys/IVs patterns
        new CryptoPattern("Hardcoded Crypto Key", 
            Pattern.compile("(?:SecretKeySpec|Key|key|KEY)\\s*[\\[=\\]]\\s*new\\s+byte\\[\\]\\s*\\{.*?\\}"),
            Severity.HIGH, 0.75, "Hardcoded cryptographic keys can be extracted from the app"),
        
        new CryptoPattern("Static IV", 
            Pattern.compile("IvParameterSpec\\s*\\(\\s*new\\s+byte\\[\\]\\s*\\{.*?\\}\\s*\\)"),
            Severity.MEDIUM, 0.70, "Static Initialization Vectors weaken encryption")
    );
    
    @Override
    public String getAnalyzerName() {
        return "CryptographyAnalyzer";
    }
    
    @Override
    public List<Finding> analyze(File file) throws Exception {
        List<Finding> findings = new ArrayList<>();
        
        String content = Files.readString(file.toPath());
        String[] lines = content.split("\n");
        
        for (CryptoPattern pattern : INSECURE_CRYPTO_PATTERNS) {
            Matcher matcher = pattern.pattern.matcher(content);
            
            while (matcher.find()) {
                String match = matcher.group();
                int lineNumber = getLineNumber(content, matcher.start());
                
                // Skip if it's in a comment
                String line = lines[lineNumber - 1].trim();
                if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) {
                    continue;
                }
                
                findings.add(new Finding(
                    UUID.randomUUID().toString(),
                    "Insecure Cryptography: " + pattern.name,
                    pattern.severity,
                    "Cryptography",
                    file.getAbsolutePath(),
                    lineNumber,
                    pattern.description + ". Found: " + truncate(match, 100),
                    truncate(match, 200),
                    pattern.confidence
                ));
            }
        }
        
        return findings;
    }
    
    private int getLineNumber(String content, int position) {
        return (int) content.substring(0, position).chars().filter(ch -> ch == '\n').count() + 1;
    }
    
    private String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...";
    }
    
    private static class CryptoPattern {
        String name;
        Pattern pattern;
        Severity severity;
        double confidence;
        String description;
        
        CryptoPattern(String name, Pattern pattern, Severity severity, double confidence, String description) {
            this.name = name;
            this.pattern = pattern;
            this.severity = severity;
            this.confidence = confidence;
            this.description = description;
        }
    }
}