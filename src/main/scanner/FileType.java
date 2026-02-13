package scanner;

public enum FileType {
    MANIFEST,
    JAVA_SOURCE,
    KOTLIN_SOURCE,
    SMALI,
    XML_RESOURCE,
    NATIVE_LIB,
    CONFIG,
    DEX,           // NEW: For .dex files
    CERTIFICATE,   // NEW: For certificate/signature files
    ASSET,         // NEW: For assets/ directory files
    BINARY_RESOURCE, // NEW: For binary resources
    UNKNOWN
}