package scanner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class FileScanner {
    
    private static final Set<String> SKIP_EXTENSIONS = Set.of(
        // Only skip truly unanalyzable binary formats
        ".mp3", ".mp4", ".avi", ".mpg", ".mpeg", ".wav", ".flac", // Media files
        ".ttf", ".otf", ".woff", ".woff2", // Font files
        ".dex" // Already .dex compiled folder exist in apk decompiled folder
        // Note: Images are now analyzed for embedded text/metadata
    );
    
    public Map<FileType, List<File>> scan(String directoryPath) throws IOException {
        File dir = new File(directoryPath);
        
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("Invalid directory: " + directoryPath);
        }
        
        System.out.println("Scanning directory: " + directoryPath);
        
        Map<FileType, List<File>> categorized = new HashMap<>();
        for (FileType type : FileType.values()) {
            categorized.put(type, new ArrayList<>());
        }
        
        try (Stream<Path> paths = Files.walk(dir.toPath())) {
            paths.filter(Files::isRegularFile)
                 .filter(this::shouldScan)
                 .forEach(path -> {
                     File file = path.toFile();
                     FileType type = classifyFile(file);
                     categorized.get(type).add(file);
                 });
        }
        
        int total = categorized.values().stream()
            .mapToInt(List::size)
            .sum();
        
        System.out.println("Files found: " + total);
        for (FileType type : FileType.values()) {
            int count = categorized.get(type).size();
            if (count > 0) {
                System.out.printf("  %s: %d\n", type, count);
            }
        }
        
        return categorized;
    }
    
    private boolean shouldScan(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        
        for (String ext : SKIP_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return false;
            }
        }
        
        return true;
    }
    
    private FileType classifyFile(File file) {
        String name = file.getName().toLowerCase();
        String path = file.getAbsolutePath().toLowerCase();
        
        // Check for certificates and signature files
        if (name.endsWith(".rsa") || name.endsWith(".sf") || name.endsWith(".mf") ||
            name.endsWith(".dsa") || name.endsWith(".ec") || name.equals("cert.sf") ||
            name.equals("cert.rsa")) {
            return FileType.CERTIFICATE;
        }
        
        // Check for DEX files
        //if (name.endsWith(".dex") || name.equals("classes.dex")) {
        //    return FileType.DEX;
        //}
        
        // Check for asset files
        if (path.contains("/assets/") || path.contains("\\assets\\")) {
            return FileType.ASSET;
        }
        
        // Check for resource files (non-XML)
        if (path.contains("/res/") || path.contains("\\res\\")) {
            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".bmp")) {
                return FileType.BINARY_RESOURCE;
            }
        }
        
        // Check for native libraries with more extensions
        if (name.endsWith(".so") || name.endsWith(".dll") || name.endsWith(".dylib")) {
            return FileType.NATIVE_LIB;
        }
        
        // Existing classification logic...
        if (name.equals("androidmanifest.xml")) {
            return FileType.MANIFEST;
        }
        
        if (name.endsWith(".java")) {
            return FileType.JAVA_SOURCE;
        }
        if (name.endsWith(".kt")) {
            return FileType.KOTLIN_SOURCE;
        }
        if (name.endsWith(".smali")) {
            return FileType.SMALI;
        }
        
        if (name.endsWith(".xml") && path.contains("/res/")) {
            return FileType.XML_RESOURCE;
        }
        
        if (name.endsWith(".json") || name.endsWith(".properties") || 
            name.endsWith(".config") || name.endsWith(".ini") ||
            name.endsWith(".yml") || name.endsWith(".yaml")) {
            return FileType.CONFIG;
        }
        
        // Check for other common APK files
        if (name.endsWith(".arsc") || name.equals("resources.arsc")) {
            return FileType.BINARY_RESOURCE;
        }
        
        // Default to UNKNOWN for everything else (will be analyzed as binary)
        return FileType.UNKNOWN;
    }
}
