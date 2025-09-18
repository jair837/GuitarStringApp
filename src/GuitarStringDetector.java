import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;

/**
 * A stable guitar string tuner with a big circular display
 * Won't jump around easily - stays locked on detected strings
 */
public class GuitarStringDetector extends JFrame {

    // Standard guitar string frequencies and names
    private static final double[] STRING_FREQUENCIES = {82.41, 110.00, 146.83, 196.00, 246.94, 329.63};
    private static final String[] STRING_NAMES = {"E", "A", "D", "G", "B", "E"};
    private static final String[] STRING_FULL_NAMES = {"Low E (6th)", "A (5th)", "D (4th)", "G (3rd)", "B (2nd)", "High E (1st)"};

    // Colors for each string
    private static final Color[] STRING_COLORS = {
            new Color(220, 50, 50),   // Low E - Red
            new Color(255, 140, 0),   // A - Orange
            new Color(255, 215, 0),   // D - Gold
            new Color(50, 205, 50),   // G - Green
            new Color(30, 144, 255),  // B - Blue
            new Color(138, 43, 226)   // High E - Purple
    };

    // Audio setup
    private AudioFormat audioFormat;
    private TargetDataLine microphone;
    private boolean isListening = false;
    private Thread audioThread;

    // Stability settings - these prevent jumping around
    private static final double MIN_VOLUME_THRESHOLD = 0.03;  // Ignore quiet sounds
    private static final int REQUIRED_CONFIRMATIONS = 6;      // Need 6 consistent readings
    private static final double FREQUENCY_TOLERANCE = 15.0;   // ±15 Hz tolerance

    // Current state
    private String currentString = "-";
    private String lockedString = "-";
    private int confirmationCount = 0;
    private double currentVolume = 0.0;
    private List<String> recentDetections = new ArrayList<>();

    // GUI components
    private JPanel circlePanel;
    private JButton startStopButton;
    private JLabel volumeLabel;
    private JLabel statusLabel;
    private JProgressBar volumeMeter;

    /**
     * Constructor - sets up the guitar string tuner
     */
    public GuitarStringDetector() {
        setupWindow();
        setupAudio();
        createInterface();
        setLocationRelativeTo(null);
    }

    /**
     * Setup the main window
     */
    private void setupWindow() {
        setTitle("Guitar String Detection");
        setSize(600, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        // Dark background for modern look
        getContentPane().setBackground(new Color(30, 30, 35));
    }

    /**
     * Setup audio recording system
     */
    private void setupAudio() {
        // High quality audio format for accurate detection
        audioFormat = new AudioFormat(44100.0f, 16, 1, true, false);

        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
            microphone = (TargetDataLine) AudioSystem.getLine(info);
        } catch (LineUnavailableException e) {
            JOptionPane.showMessageDialog(this,
                    "Cannot access microphone. Please check your audio settings.",
                    "Microphone Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Create the complete user interface
     */
    private void createInterface() {
        setLayout(new BorderLayout());

        // Top section - title and status
        createHeader();

        // Center section - giant circle display
        createCircleDisplay();

        // Bottom section - controls and volume meter
        createControls();
    }

    /**
     * Create header with title and status
     */
    private void createHeader() {
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBackground(new Color(45, 45, 50));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 15, 20));

        // Title
        JLabel titleLabel = new JLabel("Guitar String Detection");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 28));
        titleLabel.setForeground(Color.BLACK);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Status label
        statusLabel = new JLabel("Click 'Start Listening' to begin tuning");
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        statusLabel.setForeground(new Color(19, 18, 18));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        headerPanel.add(titleLabel);
        headerPanel.add(Box.createVerticalStrut(10));
        headerPanel.add(statusLabel);

        add(headerPanel, BorderLayout.NORTH);
    }

    /**
     * Create the giant circular display in the center
     */
    private void createCircleDisplay() {
        circlePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawCircularDisplay(g);
            }
        };

        circlePanel.setBackground(new Color(30, 30, 35));
        circlePanel.setPreferredSize(new Dimension(400, 400));

        add(circlePanel, BorderLayout.CENTER);
    }

    /**
     * Draw the giant circular display
     */
    private void drawCircularDisplay(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = circlePanel.getWidth();
        int height = circlePanel.getHeight();
        int centerX = width / 2;
        int centerY = height / 2;
        int radius = Math.min(width, height) / 3;

        // Determine colors based on current string
        Color circleColor = new Color(60, 60, 70);
        Color textColor = Color.WHITE;

        if (!currentString.equals("-")) {
            // Find the string index and use its color
            for (int i = 0; i < STRING_NAMES.length; i++) {
                if (STRING_NAMES[i].equals(currentString)) {
                    circleColor = STRING_COLORS[i];
                    textColor = Color.BLACK;
                    break;
                }
            }
        }

        // Draw outer glow ring if we have a strong signal
        if (currentVolume > MIN_VOLUME_THRESHOLD) {
            g2d.setColor(new Color(circleColor.getRed(), circleColor.getGreen(), circleColor.getBlue(), 50));
            g2d.fill(new Ellipse2D.Double(centerX - radius - 20, centerY - radius - 20,
                    (radius + 20) * 2, (radius + 20) * 2));
        }

        // Draw main circle
        g2d.setColor(circleColor);
        g2d.fill(new Ellipse2D.Double(centerX - radius, centerY - radius, radius * 2, radius * 2));

        // Draw circle border
        g2d.setColor(circleColor.brighter());
        g2d.setStroke(new BasicStroke(4));
        g2d.draw(new Ellipse2D.Double(centerX - radius, centerY - radius, radius * 2, radius * 2));

        // Draw the string letter in the center - GIANT SIZE
        g2d.setColor(textColor);
        g2d.setFont(new Font("Arial", Font.BOLD, 120));
        FontMetrics fm = g2d.getFontMetrics();
        String displayText = currentString;
        int textX = centerX - fm.stringWidth(displayText) / 2;
        int textY = centerY + fm.getAscent() / 2 - 10;
        g2d.drawString(displayText, textX, textY);

        // Draw string name below if we have a detection
        if (!currentString.equals("-")) {
            for (int i = 0; i < STRING_NAMES.length; i++) {
                if (STRING_NAMES[i].equals(currentString)) {
                    g2d.setFont(new Font("Arial", Font.BOLD, 18));
                    FontMetrics fm2 = g2d.getFontMetrics();
                    String stringName = STRING_FULL_NAMES[i];
                    int nameX = centerX - fm2.stringWidth(stringName) / 2;
                    int nameY = centerY + radius + 30;
                    g2d.drawString(stringName, nameX, nameY);
                    break;
                }
            }
        }

        // Draw confidence indicator dots around the circle
        if (confirmationCount > 0) {
            drawConfidenceDots(g2d, centerX, centerY, radius + 35);
        }
    }

    /**
     * Draw confidence indicator dots around the circle
     */
    private void drawConfidenceDots(Graphics2D g2d, int centerX, int centerY, int dotRadius) {
        g2d.setColor(Color.WHITE);
        int totalDots = REQUIRED_CONFIRMATIONS;
        int filledDots = confirmationCount;

        for (int i = 0; i < totalDots; i++) {
            double angle = (i * 2 * Math.PI) / totalDots - Math.PI / 2; // Start from top
            int dotX = (int) (centerX + dotRadius * Math.cos(angle)) - 6;
            int dotY = (int) (centerY + dotRadius * Math.sin(angle)) - 6;

            if (i < filledDots) {
                g2d.fillOval(dotX, dotY, 12, 12); // Filled dot
            } else {
                g2d.drawOval(dotX, dotY, 12, 12); // Empty dot
            }
        }
    }

    /**
     * Create control panel with buttons and volume meter
     */
    private void createControls() {
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        controlPanel.setBackground(new Color(45, 45, 50));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 25, 30));

        // Volume display
        volumeLabel = new JLabel("Volume: Silent");
        volumeLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        volumeLabel.setForeground(Color.WHITE);
        volumeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Volume meter
        volumeMeter = new JProgressBar(0, 100);
        volumeMeter.setValue(0);
        volumeMeter.setStringPainted(true);
        volumeMeter.setString("Input Level");
        volumeMeter.setBackground(new Color(60, 60, 60));
        volumeMeter.setForeground(new Color(100, 200, 100));
        volumeMeter.setMaximumSize(new Dimension(400, 25));
        volumeMeter.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Start/Stop button
        startStopButton = new JButton("🎤 Start Listening");
        startStopButton.setFont(new Font("Arial", Font.BOLD, 18));
        startStopButton.setBackground(new Color(70, 130, 70));
        startStopButton.setForeground(Color.BLACK);
        startStopButton.setFocusPainted(false);
        startStopButton.setPreferredSize(new Dimension(200, 50));
        startStopButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        startStopButton.addActionListener(e -> {
            if (isListening) {
                stopListening();
            } else {
                startListening();
            }
        });

        // Reset button
        JButton resetButton = new JButton("🔄 Reset Detection");
        resetButton.setFont(new Font("Arial", Font.PLAIN, 14));
        resetButton.setBackground(new Color(130, 130, 70));
        resetButton.setForeground(Color.BLACK);
        resetButton.setFocusPainted(false);
        resetButton.setPreferredSize(new Dimension(150, 35));
        resetButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        resetButton.addActionListener(e -> resetDetection());

        // Add components with spacing
        controlPanel.add(volumeLabel);
        controlPanel.add(Box.createVerticalStrut(8));
        controlPanel.add(volumeMeter);
        controlPanel.add(Box.createVerticalStrut(20));
        controlPanel.add(startStopButton);
        controlPanel.add(Box.createVerticalStrut(10));
        controlPanel.add(resetButton);

        add(controlPanel, BorderLayout.SOUTH);
    }

    /**
     * Reset the detection system
     */
    private void resetDetection() {
        currentString = "-";
        lockedString = "-";
        confirmationCount = 0;
        recentDetections.clear();

        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Detection reset - play a string");
            circlePanel.repaint();
        });
    }

    /**
     * Start listening to the microphone
     */
    private void startListening() {
        if (microphone == null) {
            JOptionPane.showMessageDialog(this,
                    "Microphone not available!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            microphone.open(audioFormat);
            microphone.start();
            isListening = true;

            startStopButton.setForeground(Color.black);
            startStopButton.setText("🛑 Stop Listening");
            startStopButton.setBackground(new Color(180, 70, 70));

            // Start audio processing thread
            audioThread = new Thread(this::processAudio);
            audioThread.setDaemon(true);
            audioThread.start();

            statusLabel.setText("Listening... Play a guitar string");

        } catch (LineUnavailableException e) {
            JOptionPane.showMessageDialog(this,
                    "Cannot start microphone: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Stop listening to the microphone
     */
    private void stopListening() {
        isListening = false;

        if (microphone != null && microphone.isOpen()) {
            microphone.stop();
            microphone.close();
        }

        startStopButton.setForeground(Color.black);
        startStopButton.setText("🎤 Start Listening");
        startStopButton.setBackground(new Color(70, 130, 70));

        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Stopped listening");
            volumeLabel.setText("Volume: Silent");
            volumeMeter.setValue(0);
        });
    }

    /**
     * Main audio processing loop - handles stability and detection
     */
    private void processAudio() {
        byte[] buffer = new byte[8192]; // Large buffer for accurate frequency analysis

        while (isListening) {
            try {
                int bytesRead = microphone.read(buffer, 0, buffer.length);

                if (bytesRead > 0) {
                    // Calculate volume level
                    currentVolume = calculateVolume(buffer, bytesRead);

                    // Only analyze if volume is above threshold
                    String detectedString = "-";
                    if (currentVolume > MIN_VOLUME_THRESHOLD) {
                        double frequency = detectPrimaryFrequency(buffer, bytesRead);
                        if (frequency > 0) {
                            detectedString = findClosestString(frequency);
                        }
                    }

                    // Process the detection with stability logic
                    processStringDetection(detectedString);

                    // Update the display
                    SwingUtilities.invokeLater(this::updateDisplay);
                }

                Thread.sleep(100); // Slower updates for more stability

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                System.err.println("Audio processing error: " + e.getMessage());
            }
        }
    }

    /**
     * Calculate volume level of the audio signal
     */
    private double calculateVolume(byte[] audioData, int length) {
        long sum = 0;
        for (int i = 0; i < length - 1; i += 2) {
            int sample = (audioData[i] & 0xFF) | ((audioData[i + 1] & 0xFF) << 8);
            if (sample > 32767) sample -= 65536;
            sum += Math.abs(sample);
        }
        return (double) sum / (length / 2) / 32768.0;
    }

    /**
     * Detect the primary frequency using auto correlation
     * This is more accurate for single notes than FFT
     */
    private double detectPrimaryFrequency(byte[] audioData, int length) {
        // Convert to double array
        double[] samples = new double[length / 2];
        for (int i = 0; i < samples.length; i++) {
            int sample = (audioData[i * 2] & 0xFF) | ((audioData[i * 2 + 1] & 0xFF) << 8);
            if (sample > 32767) sample -= 65536;
            samples[i] = sample / 32768.0;
        }

        // Apply window function to reduce artifacts
        for (int i = 0; i < samples.length; i++) {
            samples[i] *= 0.5 * (1 - Math.cos(2 * Math.PI * i / (samples.length - 1)));
        }

        // Auto correlation for pitch detection
        int minPeriod = (int) (44100 / 400.0); // Max 400 Hz
        int maxPeriod = (int) (44100 / 70.0);  // Min 70 Hz

        double maxCorrelation = 0;
        int bestPeriod = 0;

        for (int period = minPeriod; period <= maxPeriod && period < samples.length / 2; period++) {
            double correlation = 0;
            for (int i = 0; i < samples.length - period; i++) {
                correlation += samples[i] * samples[i + period];
            }

            if (correlation > maxCorrelation) {
                maxCorrelation = correlation;
                bestPeriod = period;
            }
        }

        // Need strong correlation to be confident
        if (maxCorrelation > 0.4 && bestPeriod > 0) {
            return 44100.0 / bestPeriod;
        }

        return -1; // No clear frequency found
    }

    /**
     * Find the closest guitar string to the detected frequency
     */
    private String findClosestString(double frequency) {
        double minDifference = Double.MAX_VALUE;
        String closestString = "-";

        for (int i = 0; i < STRING_FREQUENCIES.length; i++) {
            double difference = Math.abs(frequency - STRING_FREQUENCIES[i]);

            // Only consider it a match if it's within tolerance
            if (difference < FREQUENCY_TOLERANCE && difference < minDifference) {
                minDifference = difference;
                closestString = STRING_NAMES[i];
            }
        }

        return closestString;
    }

    /**
     * Process string detection with stability logic
     * This prevents jumping between strings
     */
    private void processStringDetection(String detectedString) {
        // Add to recent detections
        recentDetections.add(detectedString);
        if (recentDetections.size() > 10) {
            recentDetections.remove(0);
        }

        // If we detect the same string as currently locked
        if (detectedString.equals(lockedString)) {
            confirmationCount = Math.min(confirmationCount + 1, REQUIRED_CONFIRMATIONS);
        }
        // If we detect a different string
        else if (!detectedString.equals("-")) {
            // Count how many recent detections match this new string
            long matchCount = recentDetections.stream()
                    .filter(s -> s.equals(detectedString))
                    .count();

            // Only change if we have enough confirmations
            if (matchCount >= REQUIRED_CONFIRMATIONS) {
                lockedString = detectedString;
                confirmationCount = (int) matchCount;
            }
        }
        // If we detect silence
        else {
            // Slowly decrease confidence
            confirmationCount = Math.max(0, confirmationCount - 1);
            if (confirmationCount == 0) {
                lockedString = "-";
            }
        }

        currentString = lockedString;
    }

    /**
     * Update all visual elements
     */
    private void updateDisplay() {
        // Update volume display
        int volumePercent = (int) (currentVolume * 100);
        volumeMeter.setValue(Math.min(100, volumePercent));
        volumeLabel.setText("Volume: " + (volumePercent > 3 ? volumePercent + "%" : "Silent"));

        // Update status
        if (currentString.equals("-")) {
            statusLabel.setText("Play a guitar string clearly...");
        } else {
            int confidencePercent = (confirmationCount * 100) / REQUIRED_CONFIRMATIONS;
            statusLabel.setText("Detected: " + currentString + " string (" + confidencePercent + "% confidence)");
        }

        // Repaint the circle
        circlePanel.repaint();
    }

    /**
     * Main method - start the application
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Enable better text rendering
                System.setProperty("awt.useSystemAAFontSettings", "on");
                System.setProperty("swing.text", "true");

                new GuitarStringDetector().setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null,
                        "Failed to start Guitar String Tuner: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}