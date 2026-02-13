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

import javax.swing.table.TableRowSorter;
import java.util.Comparator;
import models.Severity;  

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
    
    private FindingCollector currentFindings;
    private OllamaClient ollamaClient;
    private OllamaRequestManager requestManager;
    
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
    
    public MainTab(MontoyaApi api) {
        this.api = api;
        this.currentFindings = new FindingCollector();
        this.ollamaClient = new OllamaClient();
        this.requestManager = new OllamaRequestManager(ollamaClient);
        this.requestManager.addStatusUpdateListener(this);
        
        this.findingIdToRowMap = new HashMap<>();
        this.rowToRequestsMap = new HashMap<>();
        
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
        
        JPanel findingsContainer = new JPanel(new BorderLayout());
        findingsContainer.add(findingsPanel, BorderLayout.CENTER);
        findingsContainer.add(buttonPanel, BorderLayout.SOUTH);
        
        tabbedPane.addTab("Findings", findingsContainer);
        
        JPanel promptPanel = createPromptPanel();
        tabbedPane.addTab("AI Console", promptPanel);
        
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
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        panel.add(new JLabel("APK Directory:"));
        
        directoryField = new JTextField(40);
        panel.add(directoryField);
        
        JButton browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> browseForDirectory());
        panel.add(browseButton);
        
        analyzeButton = new JButton("Analyze");
        analyzeButton.addActionListener(e -> startAnalysis());
        panel.add(analyzeButton);
        
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(200, 25));
        panel.add(progressBar);
        
        return panel;
    }
    
    private JPanel createPromptPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JLabel label = new JLabel("Ask AI about the findings:");
        panel.add(label, BorderLayout.NORTH);
        
        promptArea = new JTextArea(3, 40);
        promptArea.setLineWrap(true);
        JScrollPane promptScroll = new JScrollPane(promptArea);
        panel.add(promptScroll, BorderLayout.CENTER);
        
        JButton askButton = new JButton("Ask AI");
        askButton.addActionListener(e -> askAI());
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(askButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        responseArea = new JTextArea();
        responseArea.setEditable(false);
        responseArea.setLineWrap(true);
        responseArea.setWrapStyleWord(true);
        JScrollPane responseScroll = new JScrollPane(responseArea);
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, 
                                              panel, responseScroll);
        splitPane.setDividerLocation(100);
        
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(splitPane, BorderLayout.CENTER);
        
        return wrapper;
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
            Analysis Complete
            
            Files Scanned: %d
            Duration: %d ms
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
        
        // Build list of selected findings
        List<Finding> selectedFindings = new ArrayList<>();
        Map<Finding, Integer> findingToRowMap = new HashMap<>();
        
        for (int row : selectedRows) {
            // Convert view index to model index for sorting compatibility
            int modelRow = findingsTable.convertRowIndexToModel(row);
            Finding finding = currentFindings.getAllFindings().get(modelRow);
            selectedFindings.add(finding);
            findingToRowMap.put(finding, modelRow);
        }
        
        // Check Ollama availability
        if (!ollamaClient.isAvailable()) {
            responseArea.setText("Error: Ollama is not available. " +
                "Make sure Ollama is running (ollama serve)");
            return;
        }
        
        // Update button states
        updateButtonStates();
        
        // Create base prompt
        String promptBase = """
            You are a security researcher with 10 years of experience, writing a bug bounty–style vulnerability report. 
            Based on the details below, generate a clear, professional write-up suitable for submission to a bug bounty program.

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
        
        for (OllamaRequest request : batchRequests) {
            Integer modelRow = findingIdToRowMap.get(request.getFinding().getId());
            if (modelRow != null) {
                rowToRequestsMap.get(modelRow).add(request);
            }
        }
        
        // Show progress
        progressBar.setString("AI Analysis: 0/" + selectedRows.length);
        
        api.logging().logToOutput("Started AI analysis for " + selectedRows.length + " findings");
    }

    private void askAI() {
        String prompt = promptArea.getText().trim();
        
        if (prompt.isEmpty()) {
            return;
        }
        
        if (!ollamaClient.isAvailable()) {
            responseArea.setText("Error: Ollama is not available. " +
                "Make sure Ollama is running (ollama serve)");
            return;
        }
        
        responseArea.setText("Thinking...");
        
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                return ollamaClient.generate(prompt);
            }
            
            @Override
            protected void done() {
                try {
                    String response = get();
                    responseArea.setText(response);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof OllamaClient.OllamaTimeoutException) {
                        responseArea.setText("Request timed out. The model may still be loading. Please try again in a few moments.");
                    } else {
                        responseArea.setText("Error: " + cause.getMessage());
                    }
                } catch (InterruptedException e) {
                    responseArea.setText("Request was interrupted.");
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    responseArea.setText("Error: " + e.getMessage());
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
                // Update the AI Status column
                tableModel.setValueAt(request.getStatus().getDisplayName(), row, COL_AI_STATUS);
                
                // Update progress bar
                updateProgressBar();
                
                // Update button states
                updateButtonStates();
                
                // If this was a completed request and the row is selected, refresh details
                int selectedViewRow = findingsTable.getSelectedRow();
                if (request.getStatus() == AIStatus.COMPLETED && selectedViewRow >= 0) {
                    int selectedModelRow = findingsTable.convertRowIndexToModel(selectedViewRow);
                    if (selectedModelRow == row) {
                        displaySelectedFinding();
                    }
                }
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