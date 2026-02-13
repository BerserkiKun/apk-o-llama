package ai;

import models.Finding;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.net.URI;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

public class OllamaClient {
    
    private final String endpoint;
    private final String model;
    private final int connectTimeout;
    private final int readTimeout;
    private final int maxTokens;
    public static final int DEFAULT_CONNECT_TIMEOUT = 17500;
    public static final int DEFAULT_READ_TIMEOUT = 52500;
    public static final int MODEL_LOAD_EXTENDED_TIMEOUT = 10000; // 10s for cold start

    public OllamaClient() {
        this("http://localhost:11434", "qwen2.5-coder:7b");
    }
    
    public OllamaClient(String endpoint, String model) {
        this(endpoint, model, 17500, 52500, 2000);
    }
    
    public OllamaClient(String endpoint, String model, int connectTimeout, int readTimeout, int maxTokens) {
        this.endpoint = endpoint;
        this.model = model;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.maxTokens = maxTokens;
    }
    
    public boolean isAvailable() {
        try {
            URL url = new URI(endpoint + "/api/tags").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            int responseCode = conn.getResponseCode();
            return responseCode == 200;
        } catch (Exception e) {
            return false;
        }
    }
    
    public Callable<String> createRequestCallable(String prompt) {
        return () -> {
            try {
                return generateWithRetry(prompt, 0);
            } catch (Exception e) {
                throw e;
            }
        };
    }
    
    private String generateWithRetry(String prompt, int attempt) throws Exception {
        try {
            return generateInternal(prompt);
        } catch (Exception e) {
            if (attempt < 2 && shouldRetry(e)) {
                Thread.sleep(1000 * (attempt + 1)); // Exponential backoff
                return generateWithRetry(prompt, attempt + 1);
            }
            throw e;
        }
    }
    
    private boolean shouldRetry(Exception e) {
        if (e instanceof OllamaRateLimitException || 
            e instanceof OllamaServiceUnavailableException ||
            e instanceof SocketTimeoutException ||
            e instanceof ConnectException) {
            return true;
        }
        
        String message = e.getMessage();
        if (message != null) {
            message = message.toLowerCase();
            return message.contains("timeout") || 
                message.contains("connection") || 
                message.contains("busy");
        }
        return false;
    }
    
    public String generate(String prompt) throws Exception {
        return generateInternal(prompt);
    }
    
    private String generateInternal(String prompt) throws Exception {
        validatePrompt(prompt);
        
        URL url = new URI(endpoint + "/api/generate").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(readTimeout);
        
        String jsonRequest = String.format("""
            {
                "model": "%s",
                "prompt": %s,
                "stream": false,
                "options": {
                    "temperature": 0.7,
                    "num_predict": %d
                }
            }
            """,
            model,
            escapeJson(prompt),
            maxTokens
        );
        
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonRequest.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        int responseCode = conn.getResponseCode();
        
        if (responseCode == 429) {
            throw new Exception("Rate limit exceeded. Please try again later.");
        } else if (responseCode == 503) {
            throw new Exception("Service unavailable. Ollama might be busy.");
        } else if (responseCode != 200) {
            throw new Exception("Ollama returned status " + responseCode);
        }
        
        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
        }
        
        String result = extractResponse(response.toString());
        
        if (result == null || result.trim().isEmpty()) {
            throw new Exception("Empty response from Ollama");
        }
        
        return result;
    }
    
    private void validatePrompt(String prompt) throws Exception {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new Exception("Prompt cannot be empty");
        }
        
        if (prompt.length() > 10000) {
            throw new Exception("Prompt too long. Maximum 10000 characters.");
        }
    }
    
    private String extractResponse(String json) {
        try {
            int start = json.indexOf("\"response\":\"") + 12;
            if (start < 12) return json;
            
            int end = json.indexOf("\",\"", start);
            if (end < 0) end = json.indexOf("\"}", start);
            if (end < 0) return json;
            
            String response = json.substring(start, end);
            
            return response.replace("\\n", "\n")
                          .replace("\\\"", "\"")
                          .replace("\\\\", "\\");
        } catch (Exception e) {
            return json;
        }
    }
    
    private String escapeJson(String str) {
        return "\"" + str.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t") + "\"";
    }
    
    // Getters for configuration
    public String getEndpoint() { return endpoint; }
    public String getModel() { return model; }
    public int getConnectTimeout() { return connectTimeout; }
    public int getReadTimeout() { return readTimeout; }
    public int getMaxTokens() { return maxTokens; }

    // Custom exception classes for Ollama errors
    public static class OllamaConnectionException extends Exception {
        public OllamaConnectionException(String message) {
            super(message);
        }
        public OllamaConnectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class OllamaRateLimitException extends OllamaConnectionException {
        public OllamaRateLimitException(String message) {
            super(message);
        }
    }

    public static class OllamaServiceUnavailableException extends OllamaConnectionException {
        public OllamaServiceUnavailableException(String message) {
            super(message);
        }
    }

    public static class OllamaTimeoutException extends OllamaConnectionException {
        public OllamaTimeoutException(String message) {
            super(message);
        }
        public OllamaTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}