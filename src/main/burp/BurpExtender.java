package burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.ui.MainTab;
import models.VersionManager;

public class BurpExtender implements BurpExtension {
    
    private MontoyaApi api;
    
    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        
        api.extension().setName(VersionManager.getExtensionName());
        
        api.logging().logToOutput("APK-o-llama extension loaded");
        api.logging().logToOutput("AI-Powered Android Security Analysis");
        api.logging().logToOutput("Current version: " + VersionManager.getCurrentVersion());
        
        MainTab mainTab = new MainTab(api);
        api.userInterface().registerSuiteTab("APK-o-llama", mainTab);
        
        api.logging().logToOutput("Extension initialized successfully");
    }
}