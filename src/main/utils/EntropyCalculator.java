package utils;

import java.util.HashMap;
import java.util.Map;

public class EntropyCalculator {
    
    public static double calculateEntropy(String text) {
        if (text == null || text.isEmpty()) {
            return 0.0;
        }
        
        Map<Character, Integer> frequencies = new HashMap<>();
        for (char c : text.toCharArray()) {
            frequencies.put(c, frequencies.getOrDefault(c, 0) + 1);
        }
        
        double entropy = 0.0;
        int length = text.length();
        
        for (int count : frequencies.values()) {
            double probability = (double) count / length;
            entropy -= probability * (Math.log(probability) / Math.log(2));
        }
        
        return entropy;
    }
    
    public static boolean isHighEntropy(String text, double threshold) {
        return calculateEntropy(text) >= threshold;
    }
}
