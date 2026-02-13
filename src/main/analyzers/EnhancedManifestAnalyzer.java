package analyzers;

import models.Finding;
import models.Severity;
import utils.XmlParser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

public class EnhancedManifestAnalyzer implements Analyzer {
    
    // Dangerous permissions from Android
    private static final Set<String> DANGEROUS_PERMISSIONS = Set.of(
        "android.permission.READ_CALENDAR",
        "android.permission.WRITE_CALENDAR",
        "android.permission.CAMERA",
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.GET_ACCOUNTS",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.RECORD_AUDIO",
        "android.permission.READ_PHONE_STATE",
        "android.permission.CALL_PHONE",
        "android.permission.READ_CALL_LOG",
        "android.permission.WRITE_CALL_LOG",
        "android.permission.ADD_VOICEMAIL",
        "android.permission.USE_SIP",
        "android.permission.PROCESS_OUTGOING_CALLS",
        "android.permission.BODY_SENSORS",
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_SMS",
        "android.permission.RECEIVE_WAP_PUSH",
        "android.permission.RECEIVE_MMS",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE"
    );
    
    // Deprecated/insecure permissions
    private static final Set<String> DEPRECATED_PERMISSIONS = Set.of(
        "android.permission.WRITE_EXTERNAL_STORAGE", // Deprecated in API 29+
        "android.permission.READ_EXTERNAL_STORAGE"   // Deprecated in API 33+
    );
    
    @Override
    public String getAnalyzerName() {
        return "EnhancedManifestAnalyzer";
    }
    
    @Override
    public List<Finding> analyze(File manifestFile) throws Exception {
        List<Finding> findings = new ArrayList<>();
        
        if (!manifestFile.getName().equals("AndroidManifest.xml")) {
            return findings;
        }
        
        System.out.println("Enhanced analysis of manifest: " + manifestFile.getName());
        
        Document doc = XmlParser.parseXml(manifestFile);
        Element root = doc.getDocumentElement();
        
        // Run all checks
        findings.addAll(checkDangerousPermissions(doc, manifestFile.getAbsolutePath()));
        findings.addAll(checkTaskAffinity(root, manifestFile.getAbsolutePath()));
        findings.addAll(checkNetworkSecurityConfig(root, manifestFile.getAbsolutePath()));
        findings.addAll(checkCustomPermissions(doc, manifestFile.getAbsolutePath()));
        findings.addAll(checkContentProviderSecurity(doc, manifestFile.getAbsolutePath()));
        findings.addAll(checkWebViewSecurity(doc, manifestFile.getAbsolutePath()));
        
        return findings;
    }
    
    private List<Finding> checkDangerousPermissions(Document doc, String filePath) {
        List<Finding> findings = new ArrayList<>();
        
        NodeList permissionNodes = doc.getElementsByTagName("uses-permission");
        Set<String> foundPermissions = new HashSet<>();
        
        for (int i = 0; i < permissionNodes.getLength(); i++) {
            Element permElement = (Element) permissionNodes.item(i);
            String permName = XmlParser.getAttributeValue(permElement, "name");
            
            if (permName != null) {
                foundPermissions.add(permName);
                
                // Check for dangerous permissions
                if (DANGEROUS_PERMISSIONS.contains(permName)) {
                    findings.add(new Finding(
                        UUID.randomUUID().toString(),
                        "Dangerous Permission Requested",
                        Severity.HIGH,
                        "Manifest",
                        filePath,
                        -1,
                        "Application requests dangerous permission: " + permName,
                        "<uses-permission android:name=\"" + permName + "\" />",
                        0.90
                    ));
                }
                
                // Check for deprecated permissions
                if (DEPRECATED_PERMISSIONS.contains(permName)) {
                    findings.add(new Finding(
                        UUID.randomUUID().toString(),
                        "Deprecated Permission Used",
                        Severity.MEDIUM,
                        "Manifest",
                        filePath,
                        -1,
                        "Application uses deprecated permission: " + permName + ". Consider using Scoped Storage.",
                        "<uses-permission android:name=\"" + permName + "\" />",
                        0.80
                    ));
                }
            }
        }
        
        // Check for permission overuse
        if (foundPermissions.size() > 15) { // Arbitrary threshold
            findings.add(new Finding(
                UUID.randomUUID().toString(),
                "Excessive Permission Usage",
                Severity.MEDIUM,
                "Manifest",
                filePath,
                -1,
                "Application requests " + foundPermissions.size() + " permissions. May be over-privileged.",
                "Total permissions: " + foundPermissions.size(),
                0.70
            ));
        }
        
        return findings;
    }
    
    private List<Finding> checkTaskAffinity(Element root, String filePath) {
        List<Finding> findings = new ArrayList<>();
        
        NodeList activityNodes = root.getElementsByTagName("activity");
        for (int i = 0; i < activityNodes.getLength(); i++) {
            Element activity = (Element) activityNodes.item(i);
            String taskAffinity = XmlParser.getAttributeValue(activity, "taskAffinity");
            String exported = XmlParser.getAttributeValue(activity, "exported");
            
            if (taskAffinity != null && taskAffinity.isEmpty() && 
                "true".equalsIgnoreCase(exported)) {
                
                String activityName = XmlParser.getAttributeValue(activity, "name");
                findings.add(new Finding(
                    UUID.randomUUID().toString(),
                    "Potential Task Hijacking Risk",
                    Severity.MEDIUM,
                    "Manifest",
                    filePath,
                    -1,
                    "Exported activity '" + activityName + "' has empty taskAffinity, which may allow task hijacking.",
                    "<activity android:name=\"" + activityName + "\" android:exported=\"true\" android:taskAffinity=\"\" />",
                    0.75
                ));
            }
        }
        
        return findings;
    }
    
    private List<Finding> checkNetworkSecurityConfig(Element root, String filePath) {
        List<Finding> findings = new ArrayList<>();
        
        Element appElement = getApplicationElement(root);
        if (appElement != null) {
            String networkSecurityConfig = XmlParser.getAttributeValue(appElement, "networkSecurityConfig");
            
            if (networkSecurityConfig == null) {
                findings.add(new Finding(
                    UUID.randomUUID().toString(),
                    "Missing Network Security Configuration",
                    Severity.LOW,
                    "Manifest",
                    filePath,
                    -1,
                    "Application does not define networkSecurityConfig. Consider adding one for TLS configuration.",
                    "<application ... > (no networkSecurityConfig attribute)",
                    0.60
                ));
            }
        }
        
        return findings;
    }
    
    private List<Finding> checkCustomPermissions(Document doc, String filePath) {
        List<Finding> findings = new ArrayList<>();
        
        NodeList permissionNodes = doc.getElementsByTagName("permission");
        for (int i = 0; i < permissionNodes.getLength(); i++) {
            Element permElement = (Element) permissionNodes.item(i);
            String protectionLevel = XmlParser.getAttributeValue(permElement, "protectionLevel");
            String permName = XmlParser.getAttributeValue(permElement, "name");
            
            if (protectionLevel != null && 
                (protectionLevel.contains("dangerous") || protectionLevel.equals("0"))) {
                
                findings.add(new Finding(
                    UUID.randomUUID().toString(),
                    "Custom Permission with Weak Protection",
                    Severity.MEDIUM,
                    "Manifest",
                    filePath,
                    -1,
                    "Custom permission '" + permName + "' has weak protection level: " + protectionLevel,
                    "<permission android:name=\"" + permName + "\" android:protectionLevel=\"" + protectionLevel + "\" />",
                    0.75
                ));
            }
        }
        
        return findings;
    }
    
    private Element getApplicationElement(Element root) {
        NodeList appNodes = root.getElementsByTagName("application");
        if (appNodes.getLength() > 0) {
            return (Element) appNodes.item(0);
        }
        return null;
    }

    private List<Finding> checkContentProviderSecurity(Document doc, String filePath) {
        List<Finding> findings = new ArrayList<>();
        
        NodeList providerNodes = doc.getElementsByTagName("provider");
        
        for (int i = 0; i < providerNodes.getLength(); i++) {
            Element provider = (Element) providerNodes.item(i);
            String providerName = XmlParser.getAttributeValue(provider, "name");
            
            // Check for different read/write permission levels (potential permission bypass)
            String readPermission = XmlParser.getAttributeValue(provider, "readPermission");
            String writePermission = XmlParser.getAttributeValue(provider, "writePermission");
            
            if (readPermission != null && writePermission != null && 
                !readPermission.equals(writePermission)) {
                findings.add(new Finding(
                    UUID.randomUUID().toString(),
                    "Content Provider with Different Read/Write Permissions",
                    Severity.MEDIUM,
                    "Manifest",
                    filePath,
                    -1,
                    String.format("Content provider '%s' has different read and write permissions, which may lead to permission bypass.", providerName),
                    String.format("<provider android:name=\"%s\" android:readPermission=\"%s\" android:writePermission=\"%s\" />", 
                        providerName, readPermission, writePermission),
                    0.70
                ));
            }
            
            // Check for exported=true with weak or no permissions
            String exported = XmlParser.getAttributeValue(provider, "exported");
            String permission = XmlParser.getAttributeValue(provider, "permission");
            
            if ("true".equalsIgnoreCase(exported) && 
                (permission == null || permission.isEmpty()) &&
                (readPermission == null || readPermission.isEmpty()) &&
                (writePermission == null || writePermission.isEmpty())) {
                
                findings.add(new Finding(
                    UUID.randomUUID().toString(),
                    "Fully Exported Content Provider Without Any Permissions",
                    Severity.CRITICAL,
                    "Manifest",
                    filePath,
                    -1,
                    String.format("Content provider '%s' is fully exported without any permission protection, allowing any app to access its data.", providerName),
                    String.format("<provider android:name=\"%s\" android:exported=\"true\" />", providerName),
                    0.95
                ));
            }
            
            // Check for path-permission elements which can be complex and error-prone
            NodeList pathPermissions = provider.getElementsByTagName("path-permission");
            if (pathPermissions.getLength() > 0) {
                findings.add(new Finding(
                    UUID.randomUUID().toString(),
                    "Content Provider with Path Permissions",
                    Severity.LOW,
                    "Manifest",
                    filePath,
                    -1,
                    String.format("Content provider '%s' uses path-permission elements. Review for potential path traversal vulnerabilities.", providerName),
                    "<provider> with <path-permission> child elements",
                    0.60
                ));
            }
        }
        
        return findings;
    }

    private List<Finding> checkWebViewSecurity(Document doc, String filePath) {
        List<Finding> findings = new ArrayList<>();
        
        // Check if WebView is used in the app
        boolean usesWebView = false;
        
        // Check for WebView class usage in the manifest (indirect indicator)
        NodeList activityNodes = doc.getElementsByTagName("activity");
        for (int i = 0; i < activityNodes.getLength(); i++) {
            Element activity = (Element) activityNodes.item(i);
            String activityName = XmlParser.getAttributeValue(activity, "name");
            
            // Common WebView activity names or patterns
            if (activityName != null && (
                activityName.contains("WebView") || 
                activityName.contains("Browser") ||
                activityName.contains("WebActivity"))) {
                usesWebView = true;
                break;
            }
        }
        
        // If WebView is likely used, check for security misconfigurations
        if (usesWebView) {
            Element appElement = getApplicationElement(doc.getDocumentElement());
            if (appElement != null) {
                String usesCleartextTraffic = XmlParser.getAttributeValue(appElement, "usesCleartextTraffic");
                String networkSecurityConfig = XmlParser.getAttributeValue(appElement, "networkSecurityConfig");
                
                // Check 1: Cleartext traffic allowed (critical for WebView)
                if ("true".equalsIgnoreCase(usesCleartextTraffic)) {
                    findings.add(new Finding(
                        UUID.randomUUID().toString(),
                        "WebView with Cleartext Traffic Allowed",
                        Severity.CRITICAL,
                        "Manifest",
                        filePath,
                        -1,
                        "Application likely uses WebView and allows cleartext HTTP traffic, " +
                        "which can lead to MITM attacks and data leakage.",
                        "<application android:usesCleartextTraffic=\"true\">",
                        0.90
                    ));
                }
                
                // Check 2: Missing network security config with WebView
                if (networkSecurityConfig == null || networkSecurityConfig.isEmpty()) {
                    findings.add(new Finding(
                        UUID.randomUUID().toString(),
                        "WebView without Network Security Configuration",
                        Severity.HIGH,
                        "Manifest",
                        filePath,
                        -1,
                        "Application likely uses WebView but doesn't define networkSecurityConfig. " +
                        "Consider implementing certificate pinning and TLS configuration.",
                        "<application> (no networkSecurityConfig attribute)",
                        0.80
                    ));
                }
            }
        }
        
        return findings;
    }
}