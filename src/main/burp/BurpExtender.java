package burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.ui.MainTab;

public class BurpExtender implements BurpExtension {
    
    private MontoyaApi api;
    
    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        
        api.extension().setName("APK-o-Llama");
        
        api.logging().logToOutput("APK-o-Llama extension loaded");
        api.logging().logToOutput("AI-Powered Android Security Analysis");
        
        MainTab mainTab = new MainTab(api);
        api.userInterface().registerSuiteTab("APK-o-Llama", mainTab);
        
        api.logging().logToOutput("Extension initialized successfully");
    }
}
