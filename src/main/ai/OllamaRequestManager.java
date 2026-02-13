package ai;

import models.Finding;
import models.AIStatus;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Instant;
import ai.OllamaClient.OllamaTimeoutException;
import ai.OllamaClient.OllamaRateLimitException;
import ai.OllamaClient.OllamaServiceUnavailableException;
import java.util.concurrent.TimeUnit;

public class OllamaRequestManager {
    private static final int MAX_CONCURRENT_REQUESTS = 1;
    private static final int MAX_RETRIES = 3;
    private static final int REQUEST_TIMEOUT_SECONDS = 53;
    private static final int RETRY_DELAY_MS = 3500;
    
    private final OllamaClient ollamaClient;
    private final Map<String, OllamaRequest> requests;
    private final PriorityBlockingQueue<OllamaRequest> requestQueue;
    private final ExecutorService executorService;
    private final ScheduledExecutorService retryExecutor;
    private final AtomicInteger activeRequests;
    
    private SwingWorker<Void, RequestUpdate> statusWorker;
    private List<StatusUpdateListener> listeners;
    
    public interface StatusUpdateListener {
        void onStatusUpdate(OllamaRequest request);
        void onBatchComplete(List<OllamaRequest> completedRequests);
    }
    
    public static class RequestUpdate {
        public final OllamaRequest request;
        public final boolean isBatchComplete;
        
        public RequestUpdate(OllamaRequest request, boolean isBatchComplete) {
            this.request = request;
            this.isBatchComplete = isBatchComplete;
        }
    }
    
    public OllamaRequestManager(OllamaClient ollamaClient) {
        this.ollamaClient = ollamaClient;
        this.requests = new ConcurrentHashMap<>();
        this.requestQueue = new PriorityBlockingQueue<>(100, 
            Comparator.comparing(OllamaRequest::getCreatedAt));
        this.executorService = Executors.newFixedThreadPool(MAX_CONCURRENT_REQUESTS);
        this.retryExecutor = Executors.newScheduledThreadPool(1);
        this.activeRequests = new AtomicInteger(0);
        this.listeners = new CopyOnWriteArrayList<>();
        
        startProcessingThread();
        startRetryMonitor();
    }
    
    public OllamaRequest submitRequest(Finding finding, String prompt, int lineNumber) {
        OllamaRequest request = new OllamaRequest(finding, prompt, lineNumber);
        requests.put(request.getRequestId(), request);
        requestQueue.offer(request);
        
        // Notify listeners
        notifyStatusUpdate(request);
        
        return request;
    }
    
    public List<OllamaRequest> submitBatch(List<Finding> findings, String promptBase, Map<Finding, Integer> lineMap) {
        List<OllamaRequest> batchRequests = new ArrayList<>();
        
        for (Finding finding : findings) {
            String prompt = String.format(promptBase, 
                finding.getTitle(), finding.getSeverity(), 
                finding.getCategory(), finding.getFilePath(), 
                finding.getEvidence());
            
            Integer lineNumber = lineMap.get(finding);
            if (lineNumber == null) lineNumber = -1;
            
            OllamaRequest request = submitRequest(finding, prompt, lineNumber);
            batchRequests.add(request);
        }
        
        return batchRequests;
    }
    
    private void startProcessingThread() {
        statusWorker = new SwingWorker<Void, RequestUpdate>() {
            @Override
            protected Void doInBackground() throws Exception {
                while (!isCancelled()) {
                    try {
                        // Check if we can process more requests
                        if (activeRequests.get() < MAX_CONCURRENT_REQUESTS) {
                            OllamaRequest request = requestQueue.poll(100, TimeUnit.MILLISECONDS);
                            if (request != null) {
                                // Skip cancelled requests
                                if (!request.isCancelled()) {
                                    processRequest(request);
                                } else {
                                    publishUpdate(request, false);
                                }
                            }
                        } else {
                            Thread.sleep(50);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        System.err.println("Error in request processing thread: " + e.getMessage());
                    }
                }
                return null;
            }
            
            @Override
            protected void process(List<RequestUpdate> updates) {
                for (RequestUpdate update : updates) {
                    notifyStatusUpdate(update.request);
                    if (update.isBatchComplete) {
                        notifyBatchComplete();
                    }
                }
            }
        };
        
        statusWorker.execute();
    }
    
    private void processRequest(OllamaRequest request) {
        // Early check for cancellation
        if (request.isCancelled()) {
            return;
        }
        
        activeRequests.incrementAndGet();
        request.setStatus(AIStatus.IN_PROGRESS);
        publishUpdate(request, false);
        
        executorService.submit(() -> {
            try {
                // Check cancellation before processing
                if (request.isCancelled()) {
                    return;
                }
                
                // Simulate exponential backoff for retries
                if (request.getRetryCount() > 0) {
                    long delay = RETRY_DELAY_MS * (long) Math.pow(2, request.getRetryCount() - 1);
                    Thread.sleep(Math.min(delay, 10000)); // Max 10 seconds
                }
                
                // Check cancellation after delay
                if (request.isCancelled()) {
                    return;
                }
                
                // Execute the request directly
                try {
                    String response = ollamaClient.generate(request.getPrompt());
                    
                    // Check cancellation before processing response
                    if (request.isCancelled()) {
                        return;
                    }
                    
                    if (response == null || response.trim().isEmpty()) {
                        handleRequestFailure(request, "Empty response from Ollama");
                    } else {
                        request.setResponse(response);
                        request.setStatus(AIStatus.COMPLETED);
                        publishUpdate(request, false);
                    }
                } catch (OllamaClient.OllamaTimeoutException e) {
                    if (!request.isCancelled()) {
                        handleRequestFailure(request, e.getMessage());
                    }
                } catch (OllamaClient.OllamaRateLimitException e) {
                    if (!request.isCancelled()) {
                        request.setError(e.getMessage());
                        request.incrementRetryCount();
                        if (request.shouldRetry(MAX_RETRIES)) {
                            request.setStatus(AIStatus.PENDING);
                            // Add delay before requeue for rate limits
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                            requestQueue.offer(request);
                        } else {
                            request.setStatus(AIStatus.RATE_LIMITED);
                        }
                        publishUpdate(request, false);
                    }
                } catch (OllamaClient.OllamaServiceUnavailableException e) {
                    if (!request.isCancelled()) {
                        handleRequestFailure(request, e.getMessage());
                    }
                } catch (Exception e) {
                    if (!request.isCancelled()) {
                        handleRequestFailure(request, e.getMessage());
                    }
                }
                
            } catch (Exception e) {
                if (!request.isCancelled()) {
                    handleRequestFailure(request, e.getMessage());
                }
            } finally {
                activeRequests.decrementAndGet();
                publishUpdate(request, true);
            }
        });
    }
    
    private void handleRequestFailure(OllamaRequest request, String error) {
        request.setError(error);
        request.incrementRetryCount();
        
        if (request.shouldRetry(MAX_RETRIES)) {
            // Schedule retry with exponential backoff
            request.setStatus(AIStatus.PENDING);
            long delay = RETRY_DELAY_MS * (long) Math.pow(2, request.getRetryCount() - 1);
            retryExecutor.schedule(() -> {
                if (!request.isCancelled()) {
                    requestQueue.offer(request);
                }
            }, Math.min(delay, 30000), TimeUnit.MILLISECONDS);
        } else {
            // Final failure - determine status from error type
            if (error != null) {
                // error is a String message, not an Exception instance
                if (error.contains("Read timed out") || 
                    error.contains("connect timed out") ||
                    error.contains("timeout")) {
                    request.setStatus(AIStatus.TIMEOUT);
                } else if (error.contains("rate limit")) {
                    request.setStatus(AIStatus.RATE_LIMITED);
                } else {
                    request.setStatus(AIStatus.FAILED);
                }
            } else {
                request.setStatus(AIStatus.FAILED);
            }
        }
        
        publishUpdate(request, false);
    }
    
    public boolean retryRequest(String requestId) {
        OllamaRequest request = requests.get(requestId);
        if (request != null && request.getStatus().isRetryable()) {
            request.resetForRetry();
            requestQueue.offer(request);
            publishUpdate(request, false);
            return true;
        }
        return false;
    }
    
    public boolean cancelRequest(String requestId) {
        OllamaRequest request = requests.get(requestId);
        if (request != null && request.getStatus().isCancellable()) {
            request.cancel();
            publishUpdate(request, false);
            return true;
        }
        return false;
    }
    
    public boolean retryAllFailed() {
        boolean anyRetried = false;
        for (OllamaRequest request : requests.values()) {
            if (request.getStatus().isRetryable()) {
                request.resetForRetry();
                requestQueue.offer(request);
                anyRetried = true;
            }
        }
        if (anyRetried) {
            notifyBatchComplete();
        }
        return anyRetried;
    }
    
    private void startRetryMonitor() {
        retryExecutor.scheduleAtFixedRate(() -> {
            try {
                // Check for stale IN_PROGRESS requests
                Instant cutoff = Instant.now().minusSeconds(REQUEST_TIMEOUT_SECONDS + 10);
                for (OllamaRequest request : requests.values()) {
                    if (request.getStatus() == AIStatus.IN_PROGRESS && 
                        request.getUpdatedAt().isBefore(cutoff)) {
                        handleRequestFailure(request, "Stuck request timeout");
                    }
                }
            } catch (Exception e) {
                System.err.println("Error in retry monitor: " + e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    private void publishUpdate(OllamaRequest request, boolean isBatchComplete) {
        if (statusWorker != null) {
            SwingUtilities.invokeLater(() -> notifyStatusUpdate(request));
        }
    }
    
    private void notifyStatusUpdate(OllamaRequest request) {
        for (StatusUpdateListener listener : listeners) {
            try {
                listener.onStatusUpdate(request);
            } catch (Exception e) {
                System.err.println("Error notifying listener: " + e.getMessage());
            }
        }
    }
    
    private void notifyBatchComplete() {
        List<OllamaRequest> completed = new ArrayList<>();
        for (OllamaRequest request : requests.values()) {
            if (request.getStatus().isFinal()) {
                completed.add(request);
            }
        }
        
        for (StatusUpdateListener listener : listeners) {
            try {
                listener.onBatchComplete(completed);
            } catch (Exception e) {
                System.err.println("Error notifying batch complete: " + e.getMessage());
            }
        }
    }
    
    public void addStatusUpdateListener(StatusUpdateListener listener) {
        listeners.add(listener);
    }
    
    public void removeStatusUpdateListener(StatusUpdateListener listener) {
        listeners.remove(listener);
    }
    
    public OllamaRequest getRequest(String requestId) {
        return requests.get(requestId);
    }
    
    public List<OllamaRequest> getRequestsForFinding(String findingId) {
        List<OllamaRequest> result = new ArrayList<>();
        for (OllamaRequest request : requests.values()) {
            if (request.getFinding().getId().equals(findingId)) {
                result.add(request);
            }
        }
        return result;
    }
    
    public void shutdown() {
        statusWorker.cancel(true);
        executorService.shutdownNow();
        retryExecutor.shutdownNow();
        
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!retryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                retryExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            retryExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}