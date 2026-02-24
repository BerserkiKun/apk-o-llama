package burp.ui;

import burp.api.montoya.MontoyaApi;
import ai.OllamaClient;
import ai.OllamaRequestManager;
import ai.OllamaRequest;
import core.FindingCollector;
import models.Finding;
import models.ScanResult;
import models.AIStatus;
import rules.RuleEngine;
import scanner.FileScanner;
import scanner.FileType;
import java.util.concurrent.ExecutionException;
import models.Configuration;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.*;
import java.util.List;
import ai.ConversationHistory;
import javax.swing.table.TableRowSorter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;

import javax.swing.filechooser.FileNameExtensionFilter;
import models.Severity;  
import java.util.concurrent.CancellationException;
import java.net.URL;
import java.net.URI;
import java.net.HttpURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import models.VersionManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import java.util.regex.Matcher;


public class MainTab extends JPanel implements OllamaRequestManager.StatusUpdateListener {
    
    private final MontoyaApi api;
    private JTextField directoryField;
    private JButton analyzeButton;
    private JProgressBar progressBar;
    private final JTable findingsTable;
    private final DefaultTableModel tableModel;
    private JTextArea detailsArea;
    private JTextArea rightDetailsArea;  // New: right panel text area
    private JTextArea promptArea;
    private JTextArea responseArea;
    private JButton askAIButton;
    private JButton cancelButton;
    private JButton clearChatButton;  // New button
    private JButton consoleAskButton;
    private JButton consoleCancelButton;
    // NEW: Conversation history
    private ConversationHistory conversationHistory;
    
    // NEW: Reference to releases button for color updates
    private JButton releasesButton;
    
    // Status panel reference for updates
    private JTextArea statusPlaceholder;

    private FindingCollector currentFindings;
    private OllamaClient ollamaClient;
    private OllamaRequestManager requestManager;
    private Properties configProperties;
    private File configFile;

    // AI Console request tracking
    private OllamaRequest currentConsoleRequest;
    private SwingWorker<String, Void> currentAIWorker;

    // Maps for tracking requests
    private final Map<String, Integer> findingIdToRowMap;
    private final Map<Integer, List<OllamaRequest>> rowToRequestsMap;
    
    // Column indices
    private static final int COL_SEVERITY = 0;
    private static final int COL_TITLE = 1;
    private static final int COL_CATEGORY = 2;
    private static final int COL_FILE = 3;
    private static final int COL_CONFIDENCE = 4;
    private static final int COL_AI_STATUS = 5;  // New column
    
    // Model listing components
    private JList<String> modelList;
    private DefaultListModel<String> modelListModel;
    private JPanel modelPanel;

    public MainTab(MontoyaApi api) {
        this.api = api;
        this.currentFindings = new FindingCollector();
        this.ollamaClient = new OllamaClient();
        this.requestManager = new OllamaRequestManager(ollamaClient);
        this.requestManager.addStatusUpdateListener(this);
        
        this.findingIdToRowMap = new HashMap<>();
        this.rowToRequestsMap = new HashMap<>();
        this.conversationHistory = new ConversationHistory();
        
        setLayout(new BorderLayout());
        
        JPanel topPanel = createTopPanel();
        add(topPanel, BorderLayout.NORTH);
        
        JTabbedPane tabbedPane = new JTabbedPane();
        
        JPanel findingsPanel = new JPanel(new BorderLayout());
        
        // Updated column names
        String[] columns = {"Severity", "Title", "Category", "File", "Confidence", "AI Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
            
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                switch (columnIndex) {
                    case COL_SEVERITY:
                        return Severity.class;  // Change from Object.class to Severity.class
                    case COL_CONFIDENCE:
                        return Double.class;
                    case COL_AI_STATUS:
                        return String.class;
                    case COL_TITLE:
                    case COL_CATEGORY:
                    case COL_FILE:
                        return String.class;    // Change from Object.class to String.class
                    default:
                        return Object.class;
                }
            }
        };
        
        // Perform background version check on startup
        performBackgroundVersionCheck();
        
        // Load saved version check state and update button color
        Configuration config = Configuration.getInstance();
        updateReleasesButtonColor(config.isUpdateAvailable());

        findingsTable = new JTable(tableModel);
        findingsTable.setDefaultRenderer(Double.class, new ConfidenceRenderer());
        findingsTable.setDefaultRenderer(Object.class, new AIStatusRenderer());
        findingsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        findingsTable.setRowHeight(25);
        
        // Enable table sorting with custom comparators
        findingsTable.setAutoCreateRowSorter(true);
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
        findingsTable.setRowSorter(sorter);
        
        // Configure custom comparators for each column
        sorter.setComparator(COL_SEVERITY, new SeverityComparator());
        sorter.setComparator(COL_CONFIDENCE, new ConfidenceComparator());

        // Add custom renderer for AI Status column
        findingsTable.getColumnModel().getColumn(COL_AI_STATUS).setCellRenderer(new AIStatusRenderer());
        
        // Add mouse listener for retry clicks
        findingsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int column = findingsTable.columnAtPoint(e.getPoint());
                int viewRow = findingsTable.rowAtPoint(e.getPoint());
                
                if (viewRow >= 0 && column == COL_AI_STATUS) {
                    // Convert view index to model index for sorting compatibility
                    int modelRow = findingsTable.convertRowIndexToModel(viewRow);
                    
                    String statusStr = (String) tableModel.getValueAt(modelRow, COL_AI_STATUS);
                    AIStatus aiStatus = getAIStatusFromDisplay(statusStr);
                    
                    if (aiStatus != null && aiStatus.isRetryable()) {
                        retryRowAI(viewRow); // Pass view row for UI operations
                    }
                }
            }
        });
        
        findingsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                displaySelectedFinding();
                updateButtonStates();
            }
        });
        
        JScrollPane tableScroll = new JScrollPane(findingsTable);
        
        // Left details area (existing functionality)
        detailsArea = new JTextArea();
        detailsArea.setEditable(false);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);
        JScrollPane leftDetailsScroll = new JScrollPane(detailsArea);

        // Right details area (reserved for future features)
        rightDetailsArea = new JTextArea();
        rightDetailsArea.setEditable(false);
        rightDetailsArea.setLineWrap(true);
        rightDetailsArea.setWrapStyleWord(true);
        rightDetailsArea.setText("");  // Empty placeholder
        JScrollPane rightDetailsScroll = new JScrollPane(rightDetailsArea);

        // Create inner split pane for left/right details areas
        JSplitPane detailsSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                                    leftDetailsScroll, rightDetailsScroll);
        detailsSplitPane.setDividerLocation(0.25);  // Split 25% to left
        detailsSplitPane.setResizeWeight(0.25);     // Maintain 25% proportions
        detailsSplitPane.setOneTouchExpandable(false); // Keep simple

        // Main split pane (table above, details split below)
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                                                tableScroll, detailsSplitPane);
        mainSplitPane.setDividerLocation(200);
        
        findingsPanel.add(mainSplitPane, BorderLayout.CENTER);
        
        // Add button panel below findings
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        askAIButton = new JButton("Ask Ollama");
        askAIButton.addActionListener(e -> askAIForSelected());
        buttonPanel.add(askAIButton);
        
        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> cancelSelectedAI());
        cancelButton.setEnabled(false);
        buttonPanel.add(cancelButton);
        
        // NEW: Export button - always enabled
        JButton exportButton = new JButton("Export");
        exportButton.addActionListener(e -> exportFindings());
        exportButton.setEnabled(true); // Always enabled as per requirement
        buttonPanel.add(exportButton);

        JPanel findingsContainer = new JPanel(new BorderLayout());
        findingsContainer.add(findingsPanel, BorderLayout.CENTER);
        findingsContainer.add(buttonPanel, BorderLayout.SOUTH);
        
        tabbedPane.addTab("Findings", findingsContainer);
        
        JPanel promptPanel = createPromptPanel();
        tabbedPane.addTab("AI Console", promptPanel);
        
        // Adding Configuration tab
        JPanel configPanel = createConfigurationPanel();
        tabbedPane.addTab("Configuration", configPanel);

        add(tabbedPane, BorderLayout.CENTER);
        
        // Set column widths
        TableColumnModel columnModel = findingsTable.getColumnModel();
        columnModel.getColumn(COL_SEVERITY).setPreferredWidth(80);
        columnModel.getColumn(COL_TITLE).setPreferredWidth(250);
        columnModel.getColumn(COL_CATEGORY).setPreferredWidth(100);
        columnModel.getColumn(COL_FILE).setPreferredWidth(150);
        columnModel.getColumn(COL_CONFIDENCE).setPreferredWidth(80);
        columnModel.getColumn(COL_AI_STATUS).setPreferredWidth(120);
    }
    
    private class ConfidenceRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            
            // Convert table row index to model row index for sorting
            int modelRow = table.convertRowIndexToModel(row);
            
            Component c = super.getTableCellRendererComponent(table, value, 
                isSelected, hasFocus, modelRow, column);
            
            // Always set confidence text to white
            c.setForeground(Color.WHITE);
            
            // Format as percentage
            if (value instanceof Double) {
                double confidence = (Double) value;
                setText(String.format("%.0f%%", confidence * 100));
            }
            
            return c;
        }
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Left panel with all existing components
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        leftPanel.add(new JLabel("APK Directory:"));
        
        directoryField = new JTextField(40);
        leftPanel.add(directoryField);
        
        JButton browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> browseForDirectory());
        leftPanel.add(browseButton);
        
        analyzeButton = new JButton("Analyze");
        analyzeButton.addActionListener(e -> startAnalysis());
        leftPanel.add(analyzeButton);
        
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(200, 25));
        leftPanel.add(progressBar);
        
        // Right panel with Support Development button
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        // Add New Releases button first (will appear left of Support Development due to FlowLayout.RIGHT)
        releasesButton = new JButton("New releases");
        releasesButton.addActionListener(e -> handleNewReleasesClick());
        rightPanel.add(releasesButton);

        JButton supportButton = new JButton("Support Development");
        supportButton.addActionListener(e -> {
            // Open GitHub support page in default browser
            try {
                String url = "https://github.com/BerserkiKun/apk-o-llama?tab=readme-ov-file#support-development";
                java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                
                if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                    desktop.browse(new java.net.URI(url));
                } else {
                    // Fallback for systems where Desktop.browse is not supported
                    showBrowserError(MainTab.this, "Desktop browse action not supported on this system.");
                }
            } catch (java.net.URISyntaxException ex) {
                // This should never happen with the hardcoded URL, but handle gracefully
                showBrowserError(MainTab.this, "Invalid URL format.");
            } catch (java.io.IOException ex) {
                // Browser couldn't be opened
                showBrowserError(MainTab.this, "Browser couldn't be opened. Please check your system settings.");
            }
        });
        rightPanel.add(supportButton);
        
        // Add both panels to main panel
        panel.add(leftPanel, BorderLayout.CENTER);
        panel.add(rightPanel, BorderLayout.EAST);
        
        return panel;
    }
    
    private void showBrowserError(Component parent, String message) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(
                parent != null ? parent : MainTab.this,
                message,
                "Browser Error",
                JOptionPane.WARNING_MESSAGE
            );
        });
    }

    /**
     * Handles click on New Releases button
     */
    private void handleNewReleasesClick() {
        // Switch to Configuration tab
        for (java.awt.Component comp : this.getComponents()) {
            if (comp instanceof JTabbedPane) {
                JTabbedPane tabbedPane = (JTabbedPane) comp;
                // Find Configuration tab (index 2)
                tabbedPane.setSelectedIndex(2);
                break;
            }
        }
        
        // Get configuration
        Configuration config = Configuration.getInstance();
        
        // Update status panel with checking message
        if (statusPlaceholder != null) {
            statusPlaceholder.setText("System Status Information\n\n" +
                                    "• Checking for new version on GitHub...\n" +
                                    "• Please wait...\n\nAuthor: BerserkiKun | GitHub");
        }
        
        // Perform version check in background
        SwingWorker<VersionCheckResult, Void> worker = new SwingWorker<>() {
            @Override
            protected VersionCheckResult doInBackground() {
                return checkForNewVersion();
            }
            
            @Override
            protected void done() {
                try {
                    VersionCheckResult result = get();
                    Configuration config = Configuration.getInstance();
                    
                    if (result.error != null) {
                        // Error occurred
                        if (statusPlaceholder != null) {
                            statusPlaceholder.setText("System Status Information\n\n" +
                                                    "• Version Check: FAILED\n" +
                                                    "• Error: " + result.error + "\n" +
                                                    "• Time: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\n\nAuthor: BerserkiKun | GitHub");
                        }
                        return;
                    }
                    
                    if (result.updateAvailable) {
                        // Update available
                        config.setUpdateAvailable(true);
                        config.setLatestVersion(result.latestVersion);
                        config.setLastVersionCheckTime(System.currentTimeMillis());
                        config.setVersionCheckError(null);
                        config.saveToFile();
                        
                        // Update button color
                        updateReleasesButtonColor(true);
                        
                        // Show in status panel
                        if (statusPlaceholder != null) {
                            statusPlaceholder.setText("System Status Information\n\n" +
                                                    "• New version available: " + result.latestVersion + "\n" +
                                                    "• Opening GitHub release page...\n" +
                                                    "• Time: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\n\nAuthor: BerserkiKun | GitHub");
                        }
                        
                        // Open GitHub releases page in browser
                        openGitHubReleasesPage();
                        
                    } else {
                        // No update available
                        config.setUpdateAvailable(false);
                        config.setLatestVersion(result.latestVersion);
                        config.setLastVersionCheckTime(System.currentTimeMillis());
                        config.setVersionCheckError(null);
                        config.saveToFile();
                        
                        // Update button color
                        updateReleasesButtonColor(false);
                        
                        // Show in status panel
                        if (statusPlaceholder != null) {
                            statusPlaceholder.setText("System Status Information\n\n" +
                                                    "• No new releases found.\n" +
                                                    "• You are already using the latest version: " + 
                                                    VersionManager.getCurrentVersion() + "\n" +
                                                    "• Latest on GitHub: " + result.latestVersion + "\n" +
                                                    "• Time: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\n\nAuthor: BerserkiKun | GitHub");
                        }
                    }
                    
                } catch (Exception e) {
                    if (statusPlaceholder != null) {
                        statusPlaceholder.setText("System Status Information\n\n" +
                                                "• Version Check: FAILED\n" +
                                                "• Error: " + e.getMessage() + "\n" +
                                                "• Time: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\n\nAuthor: BerserkiKun | GitHub");
                    }
                }
            }
        };
        worker.execute();
    }

    /**
    * Opens GitHub releases page in default browser
    */
    private void openGitHubReleasesPage() {
        try {
            String url = "https://github.com/BerserkiKun/apk-o-llama/releases";
            java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
            
            if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                desktop.browse(new java.net.URI(url));
            } else {
                showBrowserError(MainTab.this, "Desktop browse action not supported on this system.");
            }
        } catch (Exception ex) {
            showBrowserError(MainTab.this, "Could not open browser: " + ex.getMessage());
        }
    }

    /**
    * Updates the New Releases button color based on update availability
    */
    private void updateReleasesButtonColor(boolean updateAvailable) {
        SwingUtilities.invokeLater(() -> {
            if (releasesButton != null) {
                if (updateAvailable) {
                    releasesButton.setBackground(Color.YELLOW); // Yellow
                    releasesButton.setOpaque(true);
                    releasesButton.setBorderPainted(false);
                } else {
                    releasesButton.setBackground(null);
                    releasesButton.setOpaque(false);
                    releasesButton.setBorderPainted(true);
                }
            }
        });
    }

    /**
    * Simple class to hold version check results
    */
    private static class VersionCheckResult {
        String latestVersion;
        boolean updateAvailable;
        String error;
        
        VersionCheckResult(String latestVersion, boolean updateAvailable, String error) {
            this.latestVersion = latestVersion;
            this.updateAvailable = updateAvailable;
            this.error = error;
        }
    }

    /**
    * Checks GitHub for the latest release version
    */
    private VersionCheckResult checkForNewVersion() {
        String currentVersion = VersionManager.getCurrentVersion();
        
        try {
            // GitHub API URL for releases
            URL url = new URI("https://api.github.com/repos/BerserkiKun/apk-o-llama/releases/latest").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000); // 10 second timeout
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                // Read response
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                }
                
                // Parse JSON to get tag_name
                String json = response.toString();
                String tagName = extractTagName(json);
                
                if (tagName != null) {
                    // Remove 'v' prefix if present
                    String latestVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;
                    
                    // Compare versions
                    boolean updateAvailable = compareVersions(latestVersion, currentVersion) > 0;
                    
                    return new VersionCheckResult(latestVersion, updateAvailable, null);
                } else {
                    return new VersionCheckResult(null, false, "Could not parse version from GitHub response");
                }
                
            } else if (responseCode == 403) {
                // Rate limited
                return new VersionCheckResult(null, false, "GitHub API rate limit exceeded. Try again later.");
            } else {
                return new VersionCheckResult(null, false, "GitHub returned error code: " + responseCode);
            }
            
        } catch (java.net.UnknownHostException e) {
            return new VersionCheckResult(null, false, "No internet connection. GitHub unreachable.");
        } catch (java.net.SocketTimeoutException e) {
            return new VersionCheckResult(null, false, "Connection timeout. GitHub is slow or unreachable.");
        } catch (Exception e) {
            return new VersionCheckResult(null, false, "Error checking for updates: " + e.getMessage());
        }
    }

    /**
    * Extracts tag_name from GitHub API JSON response
    */
    private String extractTagName(String json) {
        try {
            Pattern pattern = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(json);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            // Fallback to simple approach
        }
        return null;
    }

    /**
     * Compares two version strings
     * Returns: 
     *   positive if v1 > v2
     *   zero if v1 == v2
     *   negative if v1 < v2
     */
    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int p1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int p2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            
            if (p1 != p2) {
                return p1 - p2;
            }
        }
        return 0;
    }

    private JPanel createPromptPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JLabel label = new JLabel("Ask AI about the findings:");
        panel.add(label, BorderLayout.NORTH);
        
        promptArea = new JTextArea(3, 40);
        promptArea.setLineWrap(true);
        JScrollPane promptScroll = new JScrollPane(promptArea);
        panel.add(promptScroll, BorderLayout.CENTER);
        
        // CHANGE: Store references to class fields instead of local variables
        consoleAskButton = new JButton("Ask AI");
        consoleAskButton.addActionListener(e -> askAI());
        
        consoleCancelButton = new JButton("Cancel");
        consoleCancelButton.addActionListener(e -> cancelAIConsoleRequest());
        consoleCancelButton.setEnabled(false); // Initially disabled

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(consoleAskButton);
        buttonPanel.add(consoleCancelButton);

        // Add Clear button - always enabled
        JButton consoleClearButton = new JButton("Clear");
        consoleClearButton.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(
                MainTab.this,
                "LLM response will be cleared. The prompt input will be preserved.\nDo you want to continue?",
                "Clear Response",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            );
            
            if (choice == JOptionPane.YES_OPTION) {
                clearAIResponse();
            }
        });
        consoleClearButton.setEnabled(true); // Always enabled as per requirement
        buttonPanel.add(consoleClearButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        responseArea = new JTextArea();  // Keep as responseArea
        responseArea.setEditable(false);
        responseArea.setLineWrap(true);
        responseArea.setWrapStyleWord(true);
        responseArea.setText("Click \"Ask AI\" to start analysis with Ollama\n");
        JScrollPane responseScroll = new JScrollPane(responseArea);
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, 
                                            panel, responseScroll);
        splitPane.setDividerLocation(250);
        
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(splitPane, BorderLayout.CENTER);
        
        return wrapper;
    }
    
    private JPanel createConfigurationPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Create main config panel with GridBagLayout for organized layout
        JPanel configContent = new JPanel(new GridBagLayout());  // THIS panel uses GridBagLayout
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        
        int row = 0;
        
        // ========== OLLAMA CONFIGURATION SECTION ==========
        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        JLabel ollamaLabel = new JLabel("Ollama Configuration");
        ollamaLabel.setFont(ollamaLabel.getFont().deriveFont(Font.BOLD, 14f));
        configContent.add(ollamaLabel, gbc);  // ← FIXED: add to configContent, not panel
        
        // Endpoint
        gbc.gridwidth = 1;
        gbc.gridy = row++;
        gbc.gridx = 0;
        configContent.add(new JLabel("Endpoint URL:"), gbc);  // ← FIXED
        gbc.gridx = 1;
        JTextField endpointField = new JTextField(ollamaClient.getEndpoint(), 30);
        configContent.add(endpointField, gbc);  // ← FIXED
        
        // Model
        gbc.gridy = row++;
        gbc.gridx = 0;
        configContent.add(new JLabel("Model:"), gbc);  // ← FIXED
        gbc.gridx = 1;
        JTextField modelField = new JTextField(ollamaClient.getModel(), 30);
        configContent.add(modelField, gbc);  // ← FIXED
        
        // Timeouts
        gbc.gridy = row++;
        gbc.gridx = 0;
        configContent.add(new JLabel("Connect Timeout (ms):"), gbc);  // ← FIXED
        gbc.gridx = 1;
        JTextField connectTimeoutField = new JTextField(String.valueOf(ollamaClient.getConnectTimeout()), 10);
        configContent.add(connectTimeoutField, gbc);  // ← FIXED
        
        gbc.gridy = row++;
        gbc.gridx = 0;
        configContent.add(new JLabel("Read Timeout (ms):"), gbc);  // ← FIXED
        gbc.gridx = 1;
        JTextField readTimeoutField = new JTextField(String.valueOf(ollamaClient.getReadTimeout()), 10);
        configContent.add(readTimeoutField, gbc);  // ← FIXED
        
        gbc.gridy = row++;
        gbc.gridx = 0;
        configContent.add(new JLabel("Max Tokens:"), gbc);  // ← FIXED
        gbc.gridx = 1;
        JTextField maxTokensField = new JTextField(String.valueOf(ollamaClient.getMaxTokens()), 10);
        configContent.add(maxTokensField, gbc);  // ← FIXED
        
        // Test Connection Button
        gbc.gridy = row++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        JButton testButton = new JButton("Test Connection");
        JLabel testResultLabel = new JLabel(" ");
        testResultLabel.setForeground(new Color(0, 150, 0));
        configContent.add(testButton, gbc);  // ← FIXED
        
        gbc.gridy = row++;
        configContent.add(testResultLabel, gbc);  // ← FIXED
        
        // ========== AVAILABLE MODELS & STATUS SECTION (SIDE BY SIDE) ==========
        gbc.gridy = row++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(15, 5, 5, 5);
        JLabel modelsStatusLabel = new JLabel("System Status");
        modelsStatusLabel.setFont(modelsStatusLabel.getFont().deriveFont(Font.BOLD, 14f));
        configContent.add(modelsStatusLabel, gbc);

        // Create a panel to hold both sections side by side
        gbc.gridy = row++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 0.3;
        gbc.insets = new Insets(5, 5, 5, 5);

        JPanel dualPanel = new JPanel(new GridLayout(1, 2, 10, 0)); // 1 row, 2 columns, 10px horizontal gap

        // LEFT PANEL - Available Models
        JPanel modelsPanel = new JPanel(new BorderLayout());
        modelsPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), 
            "Available Models",
            javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
            javax.swing.border.TitledBorder.DEFAULT_POSITION,
            new Font("SansSerif", Font.BOLD, 12)
        ));

        modelListModel = new DefaultListModel<>();
        modelList = new JList<>(modelListModel);
        modelList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        modelList.setVisibleRowCount(5);
        modelList.setEnabled(false); // Initially disabled

        JScrollPane modelScrollPane = new JScrollPane(modelList);
        modelsPanel.add(modelScrollPane, BorderLayout.CENTER);

        // Add note inside models panel
        JLabel modelNoteLabel = new JLabel("Models appear here after successful connection test");
        modelNoteLabel.setFont(modelNoteLabel.getFont().deriveFont(Font.ITALIC, 11f));
        modelNoteLabel.setForeground(Color.GRAY);
        modelNoteLabel.setHorizontalAlignment(JLabel.CENTER);
        modelsPanel.add(modelNoteLabel, BorderLayout.SOUTH);

        // RIGHT PANEL - Status
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), 
            "Status",
            javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
            javax.swing.border.TitledBorder.DEFAULT_POSITION,
            new Font("SansSerif", Font.BOLD, 12)
        ));

        // Placeholder content for Status panel
        statusPlaceholder = new JTextArea();
        statusPlaceholder.setEditable(false);
        //statusPlaceholder.setBackground(new Color(245, 245, 245));
        statusPlaceholder.setText("System Status Information\n\n" +
                                "• Ollama Connection: Not Tested\n" +
                                "• Model Status: Not Loaded\n" +
                                "• Last Check: Never\n\n" +
                                "Click 'Test Connection' to update status.\n\nAuthor: BerserkiKun | GitHub");
        statusPlaceholder.setMargin(new Insets(10, 10, 10, 10));
        statusPlaceholder.setFont(new Font("Monospaced", Font.PLAIN, 11));

        JScrollPane statusScrollPane = new JScrollPane(statusPlaceholder);
        statusPanel.add(statusScrollPane, BorderLayout.CENTER);

        // Add both panels to the dual panel
        dualPanel.add(modelsPanel);
        dualPanel.add(statusPanel);

        // Add the dual panel to the main config content
        configContent.add(dualPanel, gbc);

        // Reset gridbag constraints for remaining components
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0;
        gbc.weighty = 0;

        // Reset gridbag constraints for remaining components
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // ========== SCAN CONFIGURATION SECTION ==========
        gbc.gridy = row++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        JLabel scanLabel = new JLabel("Scan Configuration");
        scanLabel.setFont(scanLabel.getFont().deriveFont(Font.BOLD, 14f));
        configContent.add(scanLabel, gbc);  // ← FIXED
        
        // Entropy threshold
        gbc.gridwidth = 1;
        gbc.gridy = row++;
        gbc.gridx = 0;
        configContent.add(new JLabel("Entropy Threshold:"), gbc);  // ← FIXED
        gbc.gridx = 1;
        JTextField entropyField = new JTextField("4.5", 10);
        configContent.add(entropyField, gbc);  // ← FIXED
        
        // Max file size to scan
        gbc.gridy = row++;
        gbc.gridx = 0;
        configContent.add(new JLabel("Max File Size (MB):"), gbc);  // ← FIXED
        gbc.gridx = 1;
        JTextField maxSizeField = new JTextField("10", 10);
        configContent.add(maxSizeField, gbc);  // ← FIXED
        
        // Checkboxes for scan options
        gbc.gridy = row++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        JCheckBox scanBinaryCheck = new JCheckBox("Scan binary files", true);
        configContent.add(scanBinaryCheck, gbc);  // ← FIXED
        
        gbc.gridy = row++;
        JCheckBox entropyCheck = new JCheckBox("Enable entropy-based detection", true);
        configContent.add(entropyCheck, gbc);  // ← FIXED
        
        gbc.gridy = row++;
        JCheckBox debugModeCheck = new JCheckBox("Debug mode (verbose output)", false);
        configContent.add(debugModeCheck, gbc);  // ← FIXED
        
        // ========== LOAD CONFIGURATION VALUES ==========
        Configuration config = Configuration.getInstance();

        endpointField.setText(config.getOllamaEndpoint());
        modelField.setText(config.getOllamaModel());
        connectTimeoutField.setText(String.valueOf(config.getConnectTimeout()));
        readTimeoutField.setText(String.valueOf(config.getReadTimeout()));
        maxTokensField.setText(String.valueOf(config.getMaxTokens()));
        entropyField.setText(String.valueOf(config.getEntropyThreshold()));
        maxSizeField.setText(String.valueOf(config.getMaxFileSizeMB()));
        scanBinaryCheck.setSelected(config.isScanBinaryFiles());
        entropyCheck.setSelected(config.isEntropyDetectionEnabled());
        debugModeCheck.setSelected(config.isDebugMode());

        // ========== SAVE BUTTON ==========
        gbc.gridy = row++;
        gbc.insets = new Insets(20, 5, 5, 5);
        JButton saveButton = new JButton("Save Configuration");
        saveButton.setFont(saveButton.getFont().deriveFont(Font.BOLD));
        configContent.add(saveButton, gbc);  // ← FIXED
        
        // Wrap in scroll pane and add to main panel
        JScrollPane scrollPane = new JScrollPane(configContent);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        panel.add(scrollPane, BorderLayout.CENTER);  // ← This is correct - panel uses BorderLayout
        
        // Add action listeners (these remain the same)
        testButton.addActionListener(e -> {
            testResultLabel.setText("Testing...");
            testResultLabel.setForeground(Color.BLACK);
            
            // Clear previous model list
            modelListModel.clear();
            modelList.setEnabled(false);
            
            // Update status panel - Testing in progress
            if (statusPlaceholder != null) {
                statusPlaceholder.setText("System Status Information\n\n" +
                                        "• Testing connection to Ollama...\n" +
                                        "• Please wait...\n" +
                                        "• Time: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\n\nAuthor: BerserkiKun | GitHub");
            }
            
            SwingWorker<ModelsResult, Void> worker = new SwingWorker<>() {
                @Override
                protected ModelsResult doInBackground() {
                    ModelsResult result = new ModelsResult();
                    try {
                        // Use current field values
                        OllamaClient testClient = new OllamaClient(
                            endpointField.getText().trim(),
                            modelField.getText().trim()
                        );
                        
                        // Check connection
                        result.connected = testClient.isAvailable();
                        
                        if (result.connected) {
                            // Fetch models using a direct HTTP call to /api/tags
                            result.models = fetchModels(endpointField.getText().trim());
                        }
                    } catch (Exception ex) {
                        result.error = ex.getMessage();
                    }
                    return result;
                }
                
                @Override
                protected void done() {
                    try {
                        ModelsResult result = get();
                        String currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                        
                        if (result.error != null) {
                            testResultLabel.setText("✗ Error: " + result.error);
                            testResultLabel.setForeground(Color.RED);
                            modelNoteLabel.setText("Failed to fetch models");
                            
                            // Update status panel with error
                            if (statusPlaceholder != null) {
                                statusPlaceholder.setText("System Status Information\n\n" +
                                                        "• Ollama Connection: ✗ Failed\n" +
                                                        "• Error: " + result.error + "\n" +
                                                        "• Last Check: " + currentTime + "\n\n" +
                                                        "Click 'Test Connection' to update status." + "\n\nAuthor: BerserkiKun | GitHub");
                            }
                            
                        } else if (result.connected) {
                            testResultLabel.setText("✓ Connection successful!");
                            testResultLabel.setForeground(new Color(0, 150, 0));
                            
                            if (result.models != null && !result.models.isEmpty()) {
                                // Populate model list
                                for (String model : result.models) {
                                    modelListModel.addElement(model);
                                }
                                modelList.setEnabled(true);
                                modelNoteLabel.setText(result.models.size() + " model(s) available");
                                
                                // Update status panel with success and models
                                if (statusPlaceholder != null) {
                                    statusPlaceholder.setText("System Status Information\n\n" +
                                                            "• Ollama Connection: ✓ Connected\n" +
                                                            "• Model Status: " + result.models.size() + " model(s) loaded\n" +
                                                            "• Last Check: " + currentTime + "\n\n" +
                                                            "Click 'Test Connection' to update status." + "\n\nAuthor: BerserkiKun | GitHub");
                                }
                            } else {
                                modelNoteLabel.setText("No models installed. Run 'ollama pull <model>'");
                                modelNoteLabel.setForeground(new Color(200, 100, 0)); // Orange warning
                                
                                // Update status panel with success but no models
                                if (statusPlaceholder != null) {
                                    statusPlaceholder.setText("System Status Information\n\n" +
                                                            "• Ollama Connection: ✓ Connected\n" +
                                                            "• Model Status: No models installed\n" +
                                                            "• Last Check: " + currentTime + "\n\n" +
                                                            "Click 'Test Connection' to update status." + "\n\nAuthor: BerserkiKun | GitHub");
                                }
                            }
                        } else {
                            testResultLabel.setText("✗ Connection failed - Check if Ollama is running");
                            testResultLabel.setForeground(Color.RED);
                            modelNoteLabel.setText("Connect to see available models");
                            
                            // Update status panel with failure
                            if (statusPlaceholder != null) {
                                statusPlaceholder.setText("System Status Information\n\n" +
                                                        "• Ollama Connection: ✗ Failed\n" +
                                                        "• Model Status: Not Loaded\n" +
                                                        "• Last Check: " + currentTime + "\n\n" +
                                                        "Click 'Test Connection' to update status." + "\n\nAuthor: BerserkiKun | GitHub");
                            }
                        }
                    } catch (Exception ex) {
                        testResultLabel.setText("✗ Error: " + ex.getMessage());
                        testResultLabel.setForeground(Color.RED);
                        modelNoteLabel.setText("Failed to load models");
                        
                        // Update status panel with exception
                        if (statusPlaceholder != null) {
                            statusPlaceholder.setText("System Status Information\n\n" +
                                                    "• Ollama Connection: ✗ Error\n" +
                                                    "• Error: " + ex.getMessage() + "\n" +
                                                    "• Last Check: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\n\n" +
                                                    "Click 'Test Connection' to update status." + "\n\nAuthor: BerserkiKun | GitHub");
                        }
                    }
                }
            };
            worker.execute();
        });

        saveButton.addActionListener(e -> {
            // Validate inputs
            try {
                String newEndpoint = endpointField.getText().trim();
                String newModel = modelField.getText().trim();
                int newConnectTimeout = Integer.parseInt(connectTimeoutField.getText().trim());
                int newReadTimeout = Integer.parseInt(readTimeoutField.getText().trim());
                int newMaxTokens = Integer.parseInt(maxTokensField.getText().trim());
                double newEntropyThreshold = Double.parseDouble(entropyField.getText().trim());
                int newMaxFileSize = Integer.parseInt(maxSizeField.getText().trim());
                boolean newScanBinary = scanBinaryCheck.isSelected();
                boolean newEntropyEnabled = entropyCheck.isSelected();
                boolean newDebugMode = debugModeCheck.isSelected();
                
                // Update configuration model
                config.setOllamaEndpoint(newEndpoint);
                config.setOllamaModel(newModel);
                config.setConnectTimeout(newConnectTimeout);
                config.setReadTimeout(newReadTimeout);
                config.setMaxTokens(newMaxTokens);
                config.setEntropyThreshold(newEntropyThreshold);
                config.setMaxFileSizeMB(newMaxFileSize);
                config.setScanBinaryFiles(newScanBinary);
                config.setEntropyDetectionEnabled(newEntropyEnabled);
                config.setDebugMode(newDebugMode);
                
                // Save to file
                config.saveToFile();
                
                // Create new client with updated settings
                ollamaClient = new OllamaClient(
                    newEndpoint, newModel, 
                    newConnectTimeout, newReadTimeout, newMaxTokens
                );
                
                // Update request manager
                requestManager = new OllamaRequestManager(ollamaClient);
                requestManager.addStatusUpdateListener(this);
                
                JOptionPane.showMessageDialog(panel, 
                    "Configuration saved successfully!\nSettings will apply to next scan and AI requests.", 
                    "Success", 
                    JOptionPane.INFORMATION_MESSAGE);
                    
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel, 
                    "Invalid number format in fields", 
                    "Error", 
                    JOptionPane.ERROR_MESSAGE);
            }
        });
        
        return panel;
    }

    private List<String> fetchModels(String endpoint) throws Exception {
        List<String> models = new ArrayList<>();
        
        URL url = new URI(endpoint + "/api/tags").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        try {
            if (conn.getResponseCode() == 200) {
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                }
                
                // Simple JSON parsing - extract model names
                String json = response.toString();
                // Look for "name":"modelname" patterns
                java.util.regex.Pattern pattern = 
                    java.util.regex.Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
                java.util.regex.Matcher matcher = pattern.matcher(json);
                
                while (matcher.find()) {
                    models.add(matcher.group(1));
                }
            }
        } finally {
            conn.disconnect();
        }
        
        return models;
    }

    private static class ModelsResult {
        boolean connected = false;
        List<String> models = null;
        String error = null;
    }

    private void browseForDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            directoryField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }
    
    private void startAnalysis() {
        String directory = directoryField.getText().trim();
        
        if (directory.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Please select an APK directory", 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // Reset all state
        tableModel.setRowCount(0);
        detailsArea.setText("");
        rightDetailsArea.setText("");
        currentFindings = new FindingCollector();
        findingIdToRowMap.clear();
        rowToRequestsMap.clear();
        askAIButton.setEnabled(false);
        cancelButton.setEnabled(false);
        
        analyzeButton.setEnabled(false);
        progressBar.setValue(0);
        progressBar.setString("Analyzing...");
        
        SwingWorker<ScanResult, Void> worker = new SwingWorker<>() {
            @Override
            protected ScanResult doInBackground() throws Exception {
                long startTime = System.currentTimeMillis();
                
                FileScanner scanner = new FileScanner();
                Map<FileType, List<File>> files = scanner.scan(directory);
                
                RuleEngine engine = new RuleEngine();
                List<Finding> findings = engine.analyzeFiles(files);
                
                long duration = System.currentTimeMillis() - startTime;
                int totalFiles = files.values().stream()
                    .mapToInt(List::size)
                    .sum();
                
                return new ScanResult(directory, findings, duration, totalFiles);
            }
            
            @Override
            protected void done() {
                try {
                    ScanResult result = get();
                    displayResults(result);
                    
                    api.logging().logToOutput("Analysis complete: " + 
                        result.getTotalFindings() + " findings");
                    
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(MainTab.this,
                        "Analysis failed: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                    
                    api.logging().logToError("Analysis error: " + e.getMessage());
                    
                } finally {
                    analyzeButton.setEnabled(true);
                    progressBar.setValue(100);
                    progressBar.setString("Complete");
                }
            }
        };
        
        worker.execute();
    }
    
    private void displayResults(ScanResult result) {
        currentFindings.addFindings(result.getFindings());
        
        int row = 0;
        for (Finding f : result.getFindings()) {
            tableModel.addRow(new Object[]{
                f.getSeverity(),
                f.getTitle(),
                f.getCategory(),
                new File(f.getFilePath()).getName(),
                f.getConfidence(),  // Raw double value for proper sorting
                AIStatus.NOT_REQUESTED.getDisplayName()
            });
            
            // Store mapping
            findingIdToRowMap.put(f.getId(), row);
            rowToRequestsMap.put(row, new ArrayList<>());
            row++;
        }
        
        // Update button states based on current selection
        updateButtonStates();
        
        detailsArea.setText(String.format("""
            Rule Based Analysis has Completed!
            
            Files Scanned: %d
            Scan Duration: %d ms
            Total Findings: %d
            
            Select findings and click "Ask Ollama" for AI analysis.
            """,
            result.getFilesScanned(),
            result.getScanDurationMs(),
            result.getTotalFindings()
        ));
    }
    
    private void displaySelectedFinding() {
        int row = findingsTable.getSelectedRow();
        if (row < 0) return;
        
        // Convert view index to model index for sorting compatibility
        int modelRow = findingsTable.convertRowIndexToModel(row);

        Finding finding = currentFindings.getAllFindings().get(modelRow);
        
        // Get AI response if available
        String aiAnalysis = "";
        List<OllamaRequest> requests = rowToRequestsMap.get(modelRow);
        if (requests != null && !requests.isEmpty()) {
            for (OllamaRequest request : requests) {
                if (request.getStatus() == AIStatus.COMPLETED && request.getResponse() != null) {
                    aiAnalysis = request.getResponse();
                    break;
                }
            }
        }
        
        // Get severity emoji and color marker
        String severityEmoji;
        String severityMarker;
        switch (finding.getSeverity()) {
            case CRITICAL:
                severityEmoji = "🔴";
                severityMarker = "█▓▒░ CRITICAL ░▒▓█";
                break;
            case HIGH:
                severityEmoji = "🟠"; 
                severityMarker = "▓▒░ HIGH ░▒▓";
                break;
            case MEDIUM:
                severityEmoji = "🟡";
                severityMarker = "▒░ MEDIUM ░▒";
                break;
            case LOW:
                severityEmoji = "🟢";
                severityMarker = "░ LOW ░";
                break;
            default:
                severityEmoji = "⚪";
                severityMarker = finding.getSeverity().toString();
        }

        // Truncate filename if too long
        String fileName = new File(finding.getFilePath()).getName();
        if (fileName.length() > 25) {
            fileName = "..." + fileName.substring(fileName.length() - 22);
        }
        
        // Create confidence bar visualization
        int confidenceBars = (int) (finding.getConfidence() * 10);
        String confidenceBar = "█".repeat(confidenceBars) + 
                            "░".repeat(10 - confidenceBars);

        // Display rule-based findings in LEFT panel
        detailsArea.setText(String.format("""
                ════════════════════════════════════════════════════
                |                        🔍 SECURITY FINDING                                 |
                ════════════════════════════════════════════════════
            
                ════════════════════════════════════════════════════
                |    🐞 ISSUE                                                                         |
                ════════════════════════════════════════════════════
                    %s %s

                ════════════════════════════════════════════════════
                |    📊 METADATA                                                                 |
                ════════════════════════════════════════════════════
                |    • %s Severity:      %s
                |
                |    • 🏷️  Category:    %s
                |
                |    • 📈 Confidence:  %s %.0f%%
                |
                |    • 📁 File:        %s
                |
                |    • 📄 Line:        %d
                ════════════════════════════════════════════════════

                ════════════════════════════════════════════════════
                |    📝 DESCRIPTION                                                             |
                ════════════════════════════════════════════════════
                    %s

                ════════════════════════════════════════════════════
                |    🔬 EVIDENCE                                                                  |
                ════════════════════════════════════════════════════
                    %s
            """,
            severityEmoji,
            finding.getTitle(),
            severityEmoji,
            severityMarker,
            finding.getCategory(),
            confidenceBar,
            finding.getConfidence() * 100,
            fileName,
            finding.getLineNumber(),
            wrapText(finding.getDescription(), 55),
            wrapText(finding.getEvidence(), 55)
        ));
        
        // Display AI analysis in RIGHT panel
        if (!aiAnalysis.isEmpty()) {
            rightDetailsArea.setText(String.format("""
                ══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
                |                                                                                                      🤖 AI ANALYSIS                                                                                                     |
                ══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
                AI analysis is for reference only, DYOR before coming to a conclusion.
                ----------------------------------------------------------------------------------

                %s
                """,
                wrapText(aiAnalysis, 120)
            ));
        } else {
            rightDetailsArea.setText("""
                ══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
                |                                                                                                      🤖 AI ANALYSIS                                                                                                     |
                ══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
                
                Click "Ask Ollama" to generate AI analysis.
                """);
        }
    }
    
    private String wrapText(String text, int width) {
        if (text == null || text.isEmpty()) return "";
        if (text.length() <= width) return text;
        
        StringBuilder wrapped = new StringBuilder();
        int pos = 0;
        while (pos < text.length()) {
            int end = Math.min(pos + width, text.length());
            if (end < text.length()) {
                // Try to break at word boundary
                int breakPos = text.lastIndexOf(' ', end);
                if (breakPos > pos) {
                    end = breakPos;
                }
            }
            wrapped.append(text.substring(pos, end).trim());
            if (end < text.length()) {
                wrapped.append("\n");
            }
            pos = end;
        }
        return wrapped.toString();
    }

    private void askAIForSelected() {
        int[] selectedRows = findingsTable.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this,
                "Please select at least one finding to analyze.",
                "No Selection",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Check Ollama availability first
        if (!ollamaClient.isAvailable()) {
            int response = JOptionPane.showConfirmDialog(this,
                "Ollama is not available. Would you like to check connection settings?",
                "Connection Error",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE);
            
            if (response == JOptionPane.YES_OPTION) {
                // Find the APK-o-llama's internal tabbed pane
                for (java.awt.Component comp : this.getComponents()) {
                    if (comp instanceof JTabbedPane) {
                        JTabbedPane internalTabbedPane = (JTabbedPane) comp;
                        // Switch to Configuration tab (index 2)
                        internalTabbedPane.setSelectedIndex(2);
                        break;
                    }
                }
            }
            return;
        }

        // Build list of selected findings
        List<Finding> selectedFindings = new ArrayList<>();
        Map<Finding, Integer> findingToRowMap = new HashMap<>();
        
        for (int viewRow : selectedRows) {
            int modelRow = findingsTable.convertRowIndexToModel(viewRow);
            Finding finding = currentFindings.getAllFindings().get(modelRow);
            
            // Check if this finding already has an active request
            List<OllamaRequest> existingRequests = requestManager.getRequestsForFinding(finding.getId());
            boolean hasActiveRequest = existingRequests.stream()
                .anyMatch(r -> r.getStatus() == AIStatus.PENDING || 
                            r.getStatus() == AIStatus.IN_PROGRESS);
            
            if (hasActiveRequest) {
                String status = existingRequests.stream()
                    .map(r -> r.getStatus().getDisplayName())
                    .findFirst().orElse("Unknown");
                
                int choice = JOptionPane.showConfirmDialog(this,
                    "This finding already has a " + status + " request. Create new one?",
                    "Request Already Exists",
                    JOptionPane.YES_NO_OPTION);
                
                if (choice != JOptionPane.YES_OPTION) {
                    continue;
                }
            }
            
            selectedFindings.add(finding);
            findingToRowMap.put(finding, modelRow);
        }
        
        if (selectedFindings.isEmpty()) {
            return;
        }
        
        // Update button states
        updateButtonStates();
        
        // Create base prompt
        String promptBase = """
            You are a security researcher with 10 years of experience, writing a bug bounty–style vulnerability report. 
            Based on the details below, Determine whether this finding represents a valid security vulnerability.
            1. If it is not a valid security issue, respond only with: "invalid bug" and provide a short explanation.
            2. If it is a valid issue, generate a clear, professional write-up suitable for submission to a bug bounty program.

            Vulnerability Details-
            * Title: %s
            * Severity: %s
            * Category: %s
            * Affected File / Location: %s
            * Evidence: %s
            
            Write the report using this structure:
            1. Summary
            * Briefly explain what the vulnerability is and where it was found.

            2. Description / Technical Details
            * Explain the issue clearly and technically.

            3. Impact
            * Explain what an attacker could achieve by exploiting this issue.

            4. Steps to Reproduce (if applicable)
            * Provide clear, logical steps that demonstrate how the issue can be observed or verified.

            5. Mitigation
            * Provide various mitigation strategies.

            Guidelines
            * Keep the writing **concise, clear, and professional**.
            * Use language and tone appropriate for **bug bounty platforms (HackerOne / Bugcrowd style reports)**.
            * Avoid unnecessary verbosity, but ensure the explanation is complete and understandable.
            * If something is missing fill the gaps, only if necessary.
        """;
    
        // Submit batch requests and store them in rowToRequestsMap
        List<OllamaRequest> batchRequests = requestManager.submitBatch(selectedFindings, promptBase, findingToRowMap);
        
        // Clear old requests for these findings and store new ones
        for (OllamaRequest request : batchRequests) {
            Integer modelRow = findingIdToRowMap.get(request.getFinding().getId());
            if (modelRow != null) {
                // Clear old requests list and add new one
                List<OllamaRequest> requests = new ArrayList<>();
                requests.add(request);
                rowToRequestsMap.put(modelRow, requests);
            }
        }
        
        // Show progress
        progressBar.setString("AI Analysis: 0/" + selectedFindings.size());
        
        api.logging().logToOutput("Started AI analysis for " + selectedFindings.size() + " findings");
    }

    private void askAI() {
        String prompt = promptArea.getText().trim();
        
        if (prompt.isEmpty()) {
            return;
        }
        
        if (!ollamaClient.isAvailable()) {
            String errorMsg = "\n============================================================\n" +
                            "✗ ERROR: Ollama is not available\n" +
                            "Make sure Ollama is running (ollama serve)\n" +
                            "============================================================\n\n";
            responseArea.append(errorMsg);
            return;
        }
        
        // Cancel any existing request first
        if (currentConsoleRequest != null && !currentConsoleRequest.isCancelled() && 
            (currentConsoleRequest.getStatus() == AIStatus.PENDING || 
            currentConsoleRequest.getStatus() == AIStatus.IN_PROGRESS)) {
            cancelAIConsoleRequest();
            // Small delay to ensure cleanup
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }
        
        // Add user message to history (for context, NOT displayed)
        conversationHistory.addUserMessage(prompt);
        
        // Clear input
        //promptArea.setText("");
        
        // Get model name for display
        String modelName = ollamaClient.getModel();
        
        // Capture start time
        long startTime = System.currentTimeMillis();
        
        // Append analysis started block (NO timestamp, NO "You:")
        String startedBlock = "============================================================\n" +
                            "Analyzing with " + modelName + "...\n" +
                            "============================================================\n";
        responseArea.append(startedBlock);
        
        // Build prompt with conversation context (for AI memory)
        String contextualPrompt = conversationHistory.getConversationContext() + prompt;
        
        // Create a dummy Finding for console requests (needed for OllamaRequest)
        Finding consoleFinding = new Finding(
            "console-" + System.currentTimeMillis(),
            "AI Console Request",
            Severity.INFO,
            "Console",
            "",
            -1,
            "",
            "",
            0.0
        );
        
        // Create and track the request
        currentConsoleRequest = new OllamaRequest(consoleFinding, contextualPrompt, -1);
        currentConsoleRequest.setStatus(AIStatus.IN_PROGRESS);
        
        // Auto-scroll
        SwingUtilities.invokeLater(() -> {
            JScrollPane scrollPane = (JScrollPane) responseArea.getParent().getParent();
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
        
        // Update button states
        JButton askButton = findAskButton();
        JButton cancelButton = findCancelButton();
        if (consoleAskButton != null) consoleAskButton.setEnabled(false);
        if (consoleCancelButton != null) consoleCancelButton.setEnabled(true);
        
        currentAIWorker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                // Pass the request to enable cancellation
                return ollamaClient.generate(contextualPrompt, currentConsoleRequest);
            }
            
            @Override
            protected void done() {
                try {
                    // Check if cancelled
                    if (isCancelled() || currentConsoleRequest.isCancelled()) {
                        String cancelledBlock = "✗ REQUEST CANCELLED\n" +
                                            "============================================================\n\n";
                        responseArea.append(cancelledBlock);
                        return;
                    }
                    
                    String response = get();
                    long duration = System.currentTimeMillis() - startTime;
                    
                    // Add assistant response to history (for future context)
                    conversationHistory.addAssistantMessage(response);
                    
                    // Format duration nicely
                    String durationStr;
                    if (duration < 1000) {
                        durationStr = duration + "ms";
                    } else {
                        durationStr = String.format("%.2fs", duration / 1000.0);
                    }
                    
                    // Estimate token usage
                    int promptTokens = OllamaClient.estimateTokenCount(prompt);
                    int responseTokens = OllamaClient.estimateTokenCount(response);
                    int totalTokens = promptTokens + responseTokens;
                    
                    // Append completion block
                    String completeBlock ="✓ ANALYSIS COMPLETE\n" +
                                        "Model: " + modelName + " | Time: " + durationStr + 
                                        " | Tokens: ~" + totalTokens + "\n" +
                                        "============================================================\n\n";
                    responseArea.append(completeBlock);
                    
                    // Append the actual response (properly formatted)
                    responseArea.append(response + "\n\n");
                    
                } catch (InterruptedException | CancellationException e) {
                    // Handle cancellation
                    String cancelledBlock = "✗ REQUEST CANCELLED\n" +
                                        "============================================================\n\n";
                    responseArea.append(cancelledBlock);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof InterruptedException || 
                        (cause.getMessage() != null && cause.getMessage().contains("cancelled"))) {
                        String cancelledBlock = "✗ REQUEST CANCELLED\n" +
                                            "============================================================\n\n";
                        responseArea.append(cancelledBlock);
                    } else {
                        String errorBlock ="✗ ANALYSIS FAILED\n" +
                                        "Error: " + (cause != null ? cause.getMessage() : e.getMessage()) + "\n" +
                                        "============================================================\n\n";
                        responseArea.append(errorBlock);
                    }
                } finally {
                    // Clear current request reference
                    currentConsoleRequest = null;
                    currentAIWorker = null;
                    
                    // Reset button states
                    SwingUtilities.invokeLater(() -> {
                        if (consoleAskButton != null) consoleAskButton.setEnabled(true);
                        if (consoleCancelButton != null) consoleCancelButton.setEnabled(false);
                    });
                    
                    // Auto-scroll to bottom
                    SwingUtilities.invokeLater(() -> {
                        JScrollPane scrollPane = (JScrollPane) responseArea.getParent().getParent();
                        JScrollBar vertical = scrollPane.getVerticalScrollBar();
                        vertical.setValue(vertical.getMaximum());
                    });
                }
            }
        };
        
        currentAIWorker.execute();
    }

    private void cancelAIConsoleRequest() {
        // Cancel the worker first
        if (currentAIWorker != null && !currentAIWorker.isDone()) {
            currentAIWorker.cancel(true);
        }
        
        // Cancel the request
        if (currentConsoleRequest != null && !currentConsoleRequest.isCancelled()) {
            currentConsoleRequest.cancel();
        }
        
        // Force disconnect any lingering HTTP connections
        // (handled by OllamaClient's cancellation checks)
        
        // Append cancellation message if not already done
        if (currentConsoleRequest != null && currentConsoleRequest.getStatus() != AIStatus.CANCELLED) {
            String cancelMsg = "\n============================================================\n" +
                            "✗ REQUEST CANCELLED BY USER\n" +
                            "============================================================\n\n";
            responseArea.append(cancelMsg);
        }
        
        // Reset UI state
        if (consoleAskButton != null) consoleAskButton.setEnabled(true);
        if (consoleCancelButton != null) consoleCancelButton.setEnabled(false);
        
        // Clear references
        currentConsoleRequest = null;
        currentAIWorker = null;
        
        // Auto-scroll
        SwingUtilities.invokeLater(() -> {
            JScrollPane scrollPane = (JScrollPane) responseArea.getParent().getParent();
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    private void clearAIResponse() {
        // Clear the response area completely
        responseArea.setText("Click \"Ask AI\" to start analysis with Ollama\n");
        
        // Optional: Add a subtle visual reset (no functional impact)
        responseArea.setCaretPosition(0);
    }

    private JButton findAskButton() {
        // Find the AI Console tab's button panel
        for (java.awt.Component comp : this.getComponents()) {
            if (comp instanceof JTabbedPane) {
                JTabbedPane tabbedPane = (JTabbedPane) comp;
                java.awt.Component consolePanel = tabbedPane.getComponentAt(1); // AI Console tab
                if (consolePanel instanceof JPanel) {
                    return findButtonInPanel((JPanel) consolePanel, "Ask AI");
                }
            }
        }
        return null;
    }

    private JButton findCancelButton() {
        // Find the AI Console tab's button panel
        for (java.awt.Component comp : this.getComponents()) {
            if (comp instanceof JTabbedPane) {
                JTabbedPane tabbedPane = (JTabbedPane) comp;
                java.awt.Component consolePanel = tabbedPane.getComponentAt(1); // AI Console tab
                if (consolePanel instanceof JPanel) {
                    return findButtonInPanel((JPanel) consolePanel, "Cancel");
                }
            }
        }
        return null;
    }

    private JButton findButtonInPanel(JPanel panel, String buttonText) {
        for (java.awt.Component comp : panel.getComponents()) {
            if (comp instanceof JPanel) {
                // Recursively search sub-panels
                JButton result = findButtonInPanel((JPanel) comp, buttonText);
                if (result != null) return result;
            } else if (comp instanceof JButton) {
                JButton button = (JButton) comp;
                if (buttonText.equals(button.getText())) {
                    return button;
                }
            }
        }
        return null;
    }

    /**
     * Performs background version check on startup
     */
    private void performBackgroundVersionCheck() {
        SwingWorker<VersionCheckResult, Void> worker = new SwingWorker<>() {
            @Override
            protected VersionCheckResult doInBackground() {
                return checkForNewVersion();
            }
            
            @Override
            protected void done() {
                try {
                    VersionCheckResult result = get();
                    Configuration config = Configuration.getInstance();
                    
                    if (result.error != null) {
                        // Error occurred, log but don't show in UI
                        api.logging().logToOutput("Background version check failed: " + result.error);
                        config.setVersionCheckError(result.error);
                        return;
                    }
                    
                    // Update configuration
                    config.setUpdateAvailable(result.updateAvailable);
                    config.setLatestVersion(result.latestVersion);
                    config.setLastVersionCheckTime(System.currentTimeMillis());
                    config.setVersionCheckError(null);
                    config.saveToFile();
                    
                    // Update button color
                    SwingUtilities.invokeLater(() -> {
                        updateReleasesButtonColor(result.updateAvailable);
                    });
                    
                    // Log result
                    if (result.updateAvailable) {
                        api.logging().logToOutput("New version available: " + result.latestVersion);
                    } else {
                        api.logging().logToOutput("Already using latest version: " + result.latestVersion);
                    }
                    
                } catch (Exception e) {
                    api.logging().logToError("Error in background version check: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }
    
    private void retryRowAI(int viewRow) {
        // Convert view index to model index for sorting compatibility
        int modelRow = findingsTable.convertRowIndexToModel(viewRow);
        
        List<OllamaRequest> requests = rowToRequestsMap.get(modelRow);
        if (requests != null) {
            boolean anyRetried = false;
            for (OllamaRequest request : requests) {
                if (request.getStatus().isRetryable()) {
                    requestManager.retryRequest(request.getRequestId());
                    anyRetried = true;
                }
            }
            
            if (anyRetried) {
                api.logging().logToOutput("Retrying AI analysis for row " + viewRow);
            }
        }
    }

    private void cancelSelectedAI() {
        int[] selectedRows = findingsTable.getSelectedRows();
        if (selectedRows.length == 0) {
            return;
        }
        
        boolean anyCancelled = false;
        for (int viewRow : selectedRows) {
            int modelRow = findingsTable.convertRowIndexToModel(viewRow);
            List<OllamaRequest> requests = rowToRequestsMap.get(modelRow);
            
            if (requests != null) {
                for (OllamaRequest request : requests) {
                    if (request.getStatus().isCancellable()) {
                        requestManager.cancelRequest(request.getRequestId());
                        anyCancelled = true;
                    }
                }
            }
        }
        
        if (anyCancelled) {
            api.logging().logToOutput("Cancelled AI analysis for selected findings");
            updateButtonStates();
        }
    }

    // Implement StatusUpdateListener methods
    @Override
    public void onStatusUpdate(OllamaRequest request) {
        SwingUtilities.invokeLater(() -> {
            Integer row = findingIdToRowMap.get(request.getFinding().getId());
            if (row != null) {
                // Get the most recent request for this finding
                List<OllamaRequest> requests = rowToRequestsMap.get(row);
                if (requests != null && !requests.isEmpty()) {
                    // Sort by creation time to get the latest
                    OllamaRequest latest = requests.stream()
                        .max(Comparator.comparing(OllamaRequest::getCreatedAt))
                        .orElse(request);
                    
                    // Only update UI if this is the latest request
                    if (latest.getRequestId().equals(request.getRequestId())) {
                        tableModel.setValueAt(request.getStatus().getDisplayName(), row, COL_AI_STATUS);
                        
                        // If request completed, update the finding with AI analysis
                        if (request.getStatus() == AIStatus.COMPLETED && request.getResponse() != null) {
                            request.getFinding().setAiAnalysis(request.getResponse());
                            request.getFinding().setAiStatus(Finding.AiAnalysisStatus.COMPLETED);
                            
                            // Refresh details if this row is selected
                            int selectedViewRow = findingsTable.getSelectedRow();
                            if (selectedViewRow >= 0) {
                                int selectedModelRow = findingsTable.convertRowIndexToModel(selectedViewRow);
                                if (selectedModelRow == row) {
                                    displaySelectedFinding();
                                }
                            }
                        }
                    }
                }
                
                updateProgressBar();
                updateButtonStates();
            }
        });
    }

    @Override
    public void onBatchComplete(List<OllamaRequest> completedRequests) {
        SwingUtilities.invokeLater(() -> {
            // Update button states
            updateButtonStates();
            
            // Update progress bar
            progressBar.setString("AI Analysis Complete");
            progressBar.setValue(100);
            
            api.logging().logToOutput("Batch AI analysis completed: " + 
                completedRequests.size() + " requests processed");
        });
    }

    private void updateProgressBar() {
        int totalRequests = 0;
        int completedRequests = 0;
        int failedRequests = 0;
        
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            String statusStr = (String) tableModel.getValueAt(row, COL_AI_STATUS);
            AIStatus status = getAIStatusFromDisplay(statusStr);
            
            if (status != AIStatus.NOT_REQUESTED) {
                totalRequests++;
                if (status == AIStatus.COMPLETED) {
                    completedRequests++;
                } else if (status.isRetryable()) {
                    failedRequests++;
                }
            }
        }
        
        if (totalRequests > 0) {
            int progress = (int) ((completedRequests / (double) totalRequests) * 100);
            progressBar.setValue(progress);
            progressBar.setString(String.format("AI Analysis: %d/%d (Failed: %d)", 
                completedRequests, totalRequests, failedRequests));
        }
    }

    private void updateButtonStates() {
        int[] selectedRows = findingsTable.getSelectedRows();
        
        if (selectedRows.length == 0) {
            // No selection - disable both buttons
            askAIButton.setEnabled(false);
            cancelButton.setEnabled(false);
            return;
        }
        
        boolean hasAskable = false;
        boolean hasCancellable = false;
        
        for (int viewRow : selectedRows) {
            int modelRow = findingsTable.convertRowIndexToModel(viewRow);
            String statusStr = (String) tableModel.getValueAt(modelRow, COL_AI_STATUS);
            AIStatus status = getAIStatusFromDisplay(statusStr);
            
            if (status != null) {
                // Check if status allows "Ask Ollama"
                if (status == AIStatus.NOT_REQUESTED || 
                    status == AIStatus.COMPLETED ||
                    status == AIStatus.CANCELLED || 
                    status.isRetryable()) {
                    hasAskable = true;
                }
                
                // Check if status allows "Cancel"
                if (status.isCancellable()) {
                    hasCancellable = true;
                }
            }
        }
        
        // Enable/disable buttons based on selection states
        askAIButton.setEnabled(hasAskable);
        cancelButton.setEnabled(hasCancellable);
    }

    /**
     * Exports findings to a CSV file based on selection state
     */
    private void exportFindings() {
        // Check if there are any findings at all
        if (tableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(
                this,
                "No data available to export.",
                "Export Warning",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        
        // Check if any rows are selected
        int[] selectedRows = findingsTable.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(
                this,
                "No row selected.",
                "Export Warning",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        
        // Show format selection dialog
        String[] formats = {"CSV", "HTML"};
        int formatChoice = JOptionPane.showOptionDialog(
            this,
            "Select Export Format:",
            "Export Format",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            formats,
            formats[0]
        );
        
        if (formatChoice == JOptionPane.CLOSED_OPTION) {
            return; // User cancelled
        }
        
        String selectedFormat = formats[formatChoice];
        String projectName = "";

        // If HTML selected, ask for project name
        if ("HTML".equals(selectedFormat)) {
            // Suggest default from directory name if available
            String defaultName = "";
            String directory = directoryField.getText().trim();
            if (!directory.isEmpty()) {
                File dir = new File(directory);
                defaultName = dir.getName();
            }
            
            Object input = JOptionPane.showInputDialog(
                this,
                "Enter project/report name:",
                "Report Title",
                JOptionPane.QUESTION_MESSAGE,
                null,
                null,
                defaultName
            );

            if (input == null) {
                return; // User cancelled
            }
            projectName = input.toString().trim();
            
            if (projectName == null) {
                return; // User cancelled
            }
            
            projectName = projectName.trim();
        }
        // Configure file chooser based on format
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Findings as " + selectedFormat);
        
        String extension;
        String description;
        if ("CSV".equals(selectedFormat)) {
            extension = "csv";
            description = "CSV files (*.csv)";
            fileChooser.setFileFilter(new FileNameExtensionFilter(description, extension));
        } else {
            extension = "html";
            description = "HTML files (*.html)";
            fileChooser.setFileFilter(new FileNameExtensionFilter(description, extension));
        }
        
        // Suggest a default filename with timestamp
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
        fileChooser.setSelectedFile(new File("apk-ollama-findings-" + timestamp + "." + extension));
        
        int userSelection = fileChooser.showSaveDialog(this);
        
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            
            // Ensure correct extension
            final File fileToSave;
            if (!selectedFile.getName().toLowerCase().endsWith("." + extension)) {
                fileToSave = new File(selectedFile.getAbsolutePath() + "." + extension);
            } else {
                fileToSave = selectedFile;
            }
            
            // Store selected rows for background thread
            final int[] rowsToExport = selectedRows.clone();
            final String format = selectedFormat;
            final String reportName = projectName;

            // Perform export in background to avoid UI freeze
            SwingWorker<Void, Void> exportWorker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    if ("CSV".equals(format)) {
                        exportSelectedRowsToCsv(fileToSave, rowsToExport);
                    } else {
                        exportSelectedRowsToHtml(fileToSave, rowsToExport, reportName);
                    }
                    return null;
                }
                
                @Override
                protected void done() {
                    try {
                        get(); // Check for exceptions
                        JOptionPane.showMessageDialog(
                            MainTab.this,
                            "Exported " + rowsToExport.length + " finding(s) successfully to:\n" + fileToSave.getAbsolutePath(),
                            "Export Complete",
                            JOptionPane.INFORMATION_MESSAGE
                        );
                        api.logging().logToOutput("Exported " + rowsToExport.length + " findings to: " + fileToSave.getAbsolutePath());
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(
                            MainTab.this,
                            "Error exporting findings: " + e.getMessage(),
                            "Export Error",
                            JOptionPane.ERROR_MESSAGE
                        );
                        api.logging().logToError("Export failed: " + e.getMessage());
                    }
                }
            };
            
            exportWorker.execute();
        }
    }

    /**
     * Writes only selected findings to CSV file
     */
    private void exportSelectedRowsToCsv(File file, int[] selectedViewRows) throws Exception {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            // Write header
            writer.println("Severity,Title,Category,File,Line Number,Confidence,AI Status,Description,Evidence,AI Analysis");
            
            // Write data rows for selected findings only
            for (int viewRow : selectedViewRows) {
                int modelRow = findingsTable.convertRowIndexToModel(viewRow);
                Finding finding = currentFindings.getAllFindings().get(modelRow);
                
                StringBuilder line = new StringBuilder();
                
                // Severity
                line.append(escapeCsv(finding.getSeverity().toString())).append(",");
                
                // Title
                line.append(escapeCsv(finding.getTitle())).append(",");
                
                // Category
                line.append(escapeCsv(finding.getCategory())).append(",");
                
                // File (just filename, not full path)
                String fileName = new File(finding.getFilePath()).getName();
                line.append(escapeCsv(fileName)).append(",");
                
                // Line Number
                line.append(finding.getLineNumber()).append(",");
                
                // Confidence (as percentage)
                line.append(String.format("%.0f%%", finding.getConfidence() * 100)).append(",");
                
                // AI Status - get from table
                String aiStatus = (String) tableModel.getValueAt(modelRow, COL_AI_STATUS);
                line.append(escapeCsv(aiStatus)).append(",");
                
                // Description
                line.append(escapeCsv(finding.getDescription())).append(",");
                
                // Evidence
                line.append(escapeCsv(finding.getEvidence())).append(",");
                
                // AI Analysis (if available)
                String aiAnalysis = finding.getAiAnalysis();
                if (aiAnalysis == null || aiAnalysis.isEmpty()) {
                    aiAnalysis = "No AI analysis available";
                }
                line.append(escapeCsv(aiAnalysis));
                
                writer.println(line.toString());
            }
        }
    }

    /**
     * Exports selected findings to HTML report format
     */
    private void exportSelectedRowsToHtml(File file, int[] selectedViewRows, String projectName) throws Exception {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
            // Collect selected findings
            List<Finding> selectedFindings = new ArrayList<>();
            for (int viewRow : selectedViewRows) {
                int modelRow = findingsTable.convertRowIndexToModel(viewRow);
                selectedFindings.add(currentFindings.getAllFindings().get(modelRow));
            }
            
            // Calculate severity counts
            Map<Severity, Integer> severityCounts = new EnumMap<>(Severity.class);
            for (Severity severity : Severity.values()) {
                severityCounts.put(severity, 0);
            }
            for (Finding finding : selectedFindings) {
                severityCounts.merge(finding.getSeverity(), 1, Integer::sum);
            }
            
            // Generate timestamp
            String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            
            // Generate HTML
            writer.println("<!DOCTYPE html>");
            writer.println("<html lang=\"en\">");
            writer.println("<head>");
            writer.println("    <meta charset=\"UTF-8\">");
            writer.println("    <title>APK-o-llama Findings Report</title>");
            writer.println("    <style>");
            writer.println("        body { font-family: Arial, sans-serif; margin: 20px; background-color: #ffffff; }");
            writer.println("        h1 { color: #333; border-bottom: 2px solid #666; padding-bottom: 10px; }");
            writer.println("        h2 { color: #444; margin-top: 30px; }");
            writer.println("        h3 { color: #555; margin: 10px 0; }");
            writer.println("        .summary { background-color: #f5f5f5; padding: 15px; border-radius: 5px; margin: 20px 0; }");
            writer.println("        .summary-item { margin: 5px 0; }");
            writer.println("        .finding { border: 1px solid #ddd; margin: 15px 0; padding: 15px; border-radius: 5px; }");
            writer.println("        .finding-header { display: flex; align-items: center; margin-bottom: 10px; }");
            writer.println("        .severity-badge { padding: 3px 10px; border-radius: 3px; font-weight: bold; margin-right: 10px; }");
            writer.println("        .severity-critical { background-color: #ff4444; color: white; }");
            writer.println("        .severity-high { background-color: #ff8800; color: white; }");
            writer.println("        .severity-medium { background-color: #ffcc00; color: black; }");
            writer.println("        .severity-low { background-color: #33b5e5; color: white; }");
            writer.println("        .severity-info { background-color: #aaaaaa; color: white; }");
            writer.println("        .finding-title { font-size: 18px; font-weight: bold; }");
            writer.println("        .finding-meta { color: #666; font-size: 14px; margin: 5px 0; }");
            writer.println("        .finding-section { margin: 15px 0 0 0; }");
            writer.println("        .section-label { font-weight: bold; color: #444; margin-bottom: 5px; }");
            writer.println("        .section-content { background-color: #f9f9f9; padding: 10px; border-left: 3px solid #33b5e5; white-space: pre-wrap; }");
            writer.println("        .evidence { font-family: monospace; background-color: #f0f0f0; }");
            writer.println("        .footer { margin-top: 30px; color: #888; font-size: 12px; text-align: center; }");
            writer.println("    </style>");
            writer.println("</head>");
            writer.println("<body>");
            
            // Header
            String reportTitle = projectName == null || projectName.isEmpty() ? 
                "APK-o-llama Findings Report" : 
                projectName + " Vulnerability Report";

            writer.println("    <div style=\"text-align: center;\">");
            writer.println("        <h1>" + escapeHtml(reportTitle) + "</h1>");
            writer.println("        <p>Generated: " + timestamp + "</p>");
            writer.println("    </div>");
            
            // Summary section
            writer.println("    <div class=\"summary\">");
            writer.println("        <h2>Summary</h2>");
            writer.println("        <div class=\"summary-item\">Total Selected Findings: " + selectedFindings.size() + "</div>");
            writer.println("        <div class=\"summary-item\">Severity Breakdown:</div>");
            writer.println("        <ul style=\"list-style-type: none; padding-left: 0;\">");
            for (Severity severity : Severity.values()) {
                int count = severityCounts.get(severity);
                if (count > 0) {
                    String severityClass = getSeverityCssClass(severity);
                    writer.println("            <li style=\"margin-bottom: 8px;\"><span class=\"severity-badge " + severityClass + "\">" + 
                        severity.getDisplayName() + "</span>: " + count + "</li>");
                }
            }
            writer.println("        </ul>");
            writer.println("    </div>");
            
            // Detailed findings
            writer.println("    <h2>Detailed Findings</h2>");
            
            for (Finding finding : selectedFindings) {
                String severityClass = getSeverityCssClass(finding.getSeverity());
                String fileName = new File(finding.getFilePath()).getName();
                
                writer.println("    <div class=\"finding\">");
                writer.println("        <div class=\"finding-header\">");
                writer.println("            <span class=\"severity-badge " + severityClass + "\">" + 
                    finding.getSeverity().getDisplayName() + "</span>");
                writer.println("            <span class=\"finding-title\">" + escapeHtml(finding.getTitle()) + "</span>");
                writer.println("        </div>");
                writer.println("        <div class=\"finding-meta\">");
                writer.println("            Category: " + escapeHtml(finding.getCategory()) + "<br>");
                writer.println("            File: " + escapeHtml(fileName) + "<br>");
                if (finding.getLineNumber() > 0) {
                    writer.println("            Line: " + finding.getLineNumber() + "<br>");
                }
                writer.println("            Confidence: " + String.format("%.0f%%", finding.getConfidence() * 100) + "<br>");
                writer.println("        </div>");
                
                writer.println("        <div class=\"finding-section\">");
                writer.println("            <div class=\"section-label\">Description:</div>");
                writer.println("            <div class=\"section-content\">" + escapeHtml(finding.getDescription()) + "</div>");
                writer.println("        </div>");
                
                writer.println("        <div class=\"finding-section\">");
                writer.println("            <div class=\"section-label\">Evidence:</div>");
                writer.println("            <div class=\"section-content evidence\">" + escapeHtml(finding.getEvidence()) + "</div>");
                writer.println("        </div>");
                
                // Recommendation (placeholder if not available)
                writer.println("        <div class=\"finding-section\">");
                writer.println("            <div class=\"section-label\">Recommendation:</div>");
                writer.println("            <div class=\"section-content\">Review this finding in context. " +
                    "Consider implementing proper security controls based on the finding type.</div>");
                writer.println("        </div>");
                
                writer.println("    </div>");
            }
            
            // Footer
            writer.println("    <div class=\"footer\">");
            writer.println("        Generated by APK-o-llama v" + VersionManager.getCurrentVersion() + "<br>");
            writer.println("        Author: BerserkiKun | GitHub: https://github.com/BerserkiKun/apk-o-llama");
            writer.println("    </div>");
            
            writer.println("</body>");
            writer.println("</html>");
        }
    }

    /**
     * Returns CSS class for severity badge
     */
    private String getSeverityCssClass(Severity severity) {
        switch (severity) {
            case CRITICAL: return "severity-critical";
            case HIGH: return "severity-high";
            case MEDIUM: return "severity-medium";
            case LOW: return "severity-low";
            case INFO: return "severity-info";
            default: return "severity-info";
        }
    }

    /**
     * Escapes HTML special characters
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
    
    /**
     * Escape special characters for CSV format
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        
        // Replace double quotes with double double quotes
        String escaped = value.replace("\"", "\"\"");
        
        // Wrap in double quotes if contains comma, newline, or double quote
        if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\"")) {
            return "\"" + escaped + "\"";
        }
        
        return escaped;
    }

    private void appendToChat(String sender, String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = java.time.LocalTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            
            String formattedMessage;
            if ("System".equals(sender)) {
                formattedMessage = String.format("\n[%s] 🤖 %s:\n%s\n", timestamp, sender, message);
            } else if ("User".equals(sender)) {
                formattedMessage = String.format("\n[%s] 👤 %s:\n%s\n", timestamp, sender, message);
            } else {
                formattedMessage = String.format("\n[%s] 🤖 %s:\n%s\n", timestamp, sender, message);
            }
            
            responseArea.append(formattedMessage);
            
            // Auto-scroll to bottom
            JScrollPane scrollPane = (JScrollPane) responseArea.getParent().getParent();
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    private AIStatus getAIStatusFromDisplay(String displayName) {
        for (AIStatus status : AIStatus.values()) {
            if (status.getDisplayName().equals(displayName)) {
                return status;
            }
        }
        return null;
    }

    // Custom cell renderer for AI Status column
    private class AIStatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            
            // Convert table row index to model row index for sorting
            int modelRow = table.convertRowIndexToModel(row);

            Component c = super.getTableCellRendererComponent(table, value, 
                isSelected, hasFocus, row, column);
            
            if (column == COL_AI_STATUS && value instanceof String) {
                String statusStr = (String) value;
                AIStatus status = getAIStatusFromDisplay(statusStr);
                
                if (status != null) {
                    switch (status) {
                        case COMPLETED:
                            c.setForeground(new Color(255, 255, 255)); // White
                            setToolTipText("AI analysis completed successfully");
                            break;
                        case IN_PROGRESS:
                            c.setForeground(new Color(255, 255, 255)); // // White
                            setToolTipText("AI analysis in progress...");
                            break;
                        case PENDING:
                            c.setForeground(new Color(255, 255, 255)); // // White
                            setToolTipText("Waiting for AI analysis");
                            break;
                        case CANCELLED:
                            c.setForeground(new Color(128, 128, 128)); // Gray
                            setToolTipText("AI analysis was cancelled");
                            break;
                        case FAILED:
                        case TIMEOUT:
                        case RATE_LIMITED:
                            c.setForeground(new Color(255, 0, 0)); // Red
                            setToolTipText("Click to retry AI analysis");
                            setFont(getFont().deriveFont(Font.BOLD));
                            break;
                        default:
                            c.setForeground(table.getForeground());
                            setToolTipText(null);
                    }
                }
                
                // Special styling for clickable retry statuses
                if (status != null && status.isRetryable()) {
                    setText("<html><u>" + value + "</u></html>");
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else {
                    setText(value.toString());
                    setCursor(Cursor.getDefaultCursor());
                }
            }
            
            // Set confidence column text color to white
            if (column == COL_CONFIDENCE) {
                c.setForeground(Color.WHITE);
            }
            
            return c;
        }
    }

    // Cleanup when tab is closed
    public void cleanup() {
        if (requestManager != null) {
            requestManager.shutdown();
        }
    }

    // Custom comparator for Severity column
    private class SeverityComparator implements Comparator<Object> {
        @Override
        public int compare(Object o1, Object o2) {
            if (!(o1 instanceof Severity) || !(o2 instanceof Severity)) {
                return 0;
            }
            
            Severity s1 = (Severity) o1;
            Severity s2 = (Severity) o2;
            
            // Compare using the numeric level (Critical=4, High=3, Medium=2, Low=1, Info=0)
            return Integer.compare(s1.getLevel(), s2.getLevel());
        }
    }

    // Custom comparator for Confidence column
    private class ConfidenceComparator implements Comparator<Object> {
        @Override
        public int compare(Object o1, Object o2) {
            if (!(o1 instanceof Double) || !(o2 instanceof Double)) {
                return 0;
            }
            
            Double d1 = (Double) o1;
            Double d2 = (Double) o2;
            
            // Compare numeric values (0.0-1.0)
            return Double.compare(d1, d2);
        }
    }
}