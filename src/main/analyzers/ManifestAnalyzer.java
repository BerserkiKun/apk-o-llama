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

public class ManifestAnalyzer implements Analyzer {
    
    @Override
    public String getAnalyzerName() {
        return "ManifestAnalyzer";
    }
    
    @Override
    public List<Finding> analyze(File manifestFile) throws Exception {
        List<Finding> findings = new ArrayList<>();
        
        if (!manifestFile.getName().equals("AndroidManifest.xml")) {
            return findings;
        }
        
        System.out.println("Analyzing manifest: " + manifestFile.getName());
        
        Document doc = XmlParser.parseXml(manifestFile);
        Element root = doc.getDocumentElement();
        
        NodeList appNodes = root.getElementsByTagName("application");
        if (appNodes.getLength() > 0) {
            Element appElement = (Element) appNodes.item(0);
            
            findings.addAll(checkDebuggable(appElement, manifestFile.getAbsolutePath()));
            findings.addAll(checkBackupEnabled(appElement, manifestFile.getAbsolutePath()));
            findings.addAll(checkCleartextTraffic(appElement, manifestFile.getAbsolutePath()));
        }
        
        findings.addAll(checkExportedActivities(doc, manifestFile.getAbsolutePath()));
        findings.addAll(checkExportedServices(doc, manifestFile.getAbsolutePath()));
        findings.addAll(checkExportedReceivers(doc, manifestFile.getAbsolutePath()));
        findings.addAll(checkExportedProviders(doc, manifestFile.getAbsolutePath()));
        
        return findings;
    }
    
    private List<Finding> checkDebuggable(Element appElement, String filePath) {
        List<Finding> findings = new ArrayList<>();
        
        String debuggable = XmlParser.getAttributeValue(appElement, "debuggable");
        
        if ("true".equalsIgnoreCase(debuggable)) {
            findings.add(new Finding(
                UUID.randomUUID().toString(),
                "Debuggable Application",
                Severity.CRITICAL,
                "Manifest",
                filePath,
                -1,
                "Application has android:debuggable=\"true\" which allows debugging and " +
                "memory inspection in production builds.",
                "<application android:debuggable=\"true\">",
                0.98
            ));
        }
        
        return findings;
    }
    
    private List<Finding> checkBackupEnabled(Element appElement, String filePath) {
        List<Finding> findings = new ArrayList<>();
        
        String allowBackup = XmlParser.getAttributeValue(appElement, "allowBackup");
        
        if (allowBackup == null || "true".equalsIgnoreCase(allowBackup)) {
            findings.add(new Finding(
                UUID.randomUUID().toString(),
                "Backup Enabled",
                Severity.MEDIUM,
                "Manifest",
                filePath,
                -1,
                "Application allows backup which can expose sensitive data through ADB.",
                "<application android:allowBackup=\"true\">",
                0.85
            ));
        }
        
        return findings;
    }
    
    private List<Finding> checkCleartextTraffic(Element appElement, String filePath) {
        List<Finding> findings = new ArrayList<>();
        
        String cleartextTraffic = XmlParser.getAttributeValue(appElement, "usesCleartextTraffic");
        
        if ("true".equalsIgnoreCase(cleartextTraffic)) {
            findings.add(new Finding(
                UUID.randomUUID().toString(),
                "Cleartext Traffic Allowed",
                Severity.HIGH,
                "Manifest",
                filePath,
                -1,
                "Application allows cleartext HTTP traffic.",
                "<application android:usesCleartextTraffic=\"true\">",
                0.95
            ));
        }
        
        return findings;
    }
    
    private List<Finding> checkExportedActivities(Document doc, String filePath) {
        return checkExportedComponent(doc, "activity", filePath);
    }
    
    private List<Finding> checkExportedServices(Document doc, String filePath) {
        return checkExportedComponent(doc, "service", filePath);
    }
    
    private List<Finding> checkExportedReceivers(Document doc, String filePath) {
        return checkExportedComponent(doc, "receiver", filePath);
    }

    private List<Finding> checkExportedProviders(Document doc, String filePath) {
        return checkExportedComponent(doc, "provider", filePath);
    }
    
    private List<Finding> checkExportedComponent(Document doc, String componentType, String filePath) {
        List<Finding> findings = new ArrayList<>();
        
        NodeList components = doc.getElementsByTagName(componentType);
        
        for (int i = 0; i < components.getLength(); i++) {
            Element component = (Element) components.item(i);
            
            String exported = XmlParser.getAttributeValue(component, "exported");
            String permission = XmlParser.getAttributeValue(component, "permission");
            String name = XmlParser.getAttributeValue(component, "name");
            String grantUriPermissions = XmlParser.getAttributeValue(component, "grantUriPermissions");
            
            // Check for exported component without permission
            if ("true".equalsIgnoreCase(exported) && permission == null) {
                findings.add(new Finding(
                    UUID.randomUUID().toString(),
                    "Exported " + capitalize(componentType) + " Without Permission",
                    Severity.HIGH,
                    "Manifest",
                    filePath,
                    -1,
                    String.format("The %s '%s' is exported without permission protection.", componentType, name),
                    String.format("<%s android:name=\"%s\" android:exported=\"true\">", 
                        componentType, name),
                    0.90
                ));
            }
            
            // Special check for providers: grantUriPermissions without proper restrictions
            if ("provider".equals(componentType) && "true".equalsIgnoreCase(grantUriPermissions)) {
                String readPermission = XmlParser.getAttributeValue(component, "readPermission");
                String writePermission = XmlParser.getAttributeValue(component, "writePermission");
                
                if ((readPermission == null || writePermission == null) && permission == null) {
                    findings.add(new Finding(
                        UUID.randomUUID().toString(),
                        "Content Provider with Grant URI Permissions Without Protection",
                        Severity.HIGH,
                        "Manifest",
                        filePath,
                        -1,
                        String.format("Content provider '%s' has grantUriPermissions=\"true\" without proper read/write permission protection.", name),
                        String.format("<provider android:name=\"%s\" android:grantUriPermissions=\"true\">", name),
                        0.85
                    ));
                }
            }
            
            // Check for components with intent-filter but no explicit exported attribute
            NodeList intentFilters = component.getElementsByTagName("intent-filter");
            if (intentFilters.getLength() > 0 && exported == null && permission == null) {
                findings.add(new Finding(
                    UUID.randomUUID().toString(),
                    "Implicitly Exported " + capitalize(componentType),
                    Severity.MEDIUM,
                    "Manifest",
                    filePath,
                    -1,
                    String.format("The %s '%s' has intent-filter but no explicit exported attribute.", componentType, name),
                    String.format("<%s android:name=\"%s\"><intent-filter>", componentType, name),
                    0.75
                ));
            }
        }
        
        return findings;
    }
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
