package ai;

import java.util.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

public class ConversationHistory {
    private static final int MAX_HISTORY_SIZE = 20;
    private final List<Message> messages;
    private final DateTimeFormatter timeFormatter;
    
    public ConversationHistory() {
        this.messages = new ArrayList<>();
        this.timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    }
    
    public void addUserMessage(String content) {
        messages.add(new Message("User", content, Instant.now()));
        trimHistory();
    }
    
    public void addAssistantMessage(String content) {
        messages.add(new Message("Assistant", content, Instant.now()));
        trimHistory();
    }
    
    public String getFormattedHistory() {
        StringBuilder history = new StringBuilder();
        for (Message msg : messages) {
            history.append(String.format("[%s] %s: %s\n\n", 
                msg.getFormattedTime(), 
                msg.getSender(), 
                msg.getContent()));
        }
        return history.toString();
    }
    
    public String getConversationContext() {
        // Build context for LLM (last 20 exchanges)
        StringBuilder context = new StringBuilder();
        context.append("Previous conversation:\n");
        
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            context.append(String.format("%s: %s\n", 
                msg.getSender().toUpperCase(), 
                msg.getContent()));
        }
        context.append("\nCurrent prompt: ");
        return context.toString();
    }
    
    public void clear() {
        messages.clear();
    }
    
    public boolean isEmpty() {
        return messages.isEmpty();
    }
    
    public int size() {
        return messages.size();
    }
    
    private void trimHistory() {
        while (messages.size() > MAX_HISTORY_SIZE * 2) { // Store full exchanges
            messages.remove(0);
        }
    }
    
    // Inner class for individual messages
    private static class Message {
        private final String sender;
        private final String content;
        private final Instant timestamp;
        
        Message(String sender, String content, Instant timestamp) {
            this.sender = sender;
            this.content = content;
            this.timestamp = timestamp;
        }
        
        String getSender() { return sender; }
        String getContent() { return content; }
        String getFormattedTime() {
            return DateTimeFormatter.ofPattern("HH:mm:ss").format(timestamp);
        }
    }
}