import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FacilityBookingGUI extends JFrame {
    private static final String SERVER_ADDRESS = "127.0.0.1";
    private static final int SERVER_PORT = 2222;
    private static final int CLIENT_PORT = 8080;
    private static int monitorDuration;
    private static int request_id = 1000;
    private DatagramSocket socket;
    private InetAddress serverAddress;

    // GUI Components
    private JTabbedPane tabbedPane;
    private JTextArea responseArea;
    private JPanel queryPanel, bookPanel, changePanel, monitorPanel;

    public FacilityBookingGUI() {
        // Setup frame
        setTitle("Facility Booking System");
        setSize(600, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        try {
            socket = new DatagramSocket(CLIENT_PORT);
            serverAddress = InetAddress.getByName(SERVER_ADDRESS);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error initializing network: " + e.getMessage(),
                    "Network Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        // Create main components
        tabbedPane = new JTabbedPane();

        // Create panels for each function
        createQueryPanel();
        createBookPanel();
        createChangePanel();
        createMonitorPanel();

        // Add tabs
        tabbedPane.addTab("Query Availability", queryPanel);
        tabbedPane.addTab("Book Facility", bookPanel);
        tabbedPane.addTab("Change Booking", changePanel);
        tabbedPane.addTab("Monitor Facility", monitorPanel);

        // Response area
        responseArea = new JTextArea();
        responseArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(responseArea);
        scrollPane.setPreferredSize(new Dimension(580, 150));

        // Layout
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout());
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        mainPanel.add(scrollPane, BorderLayout.SOUTH);

        add(mainPanel);

        // Add window close handler
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            }
        });
    }

    private void createQueryPanel() {
        queryPanel = new JPanel();
        queryPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        queryPanel.add(new JLabel("Facility Name:"), gbc);

        JTextField facilityField = new JTextField(20);
        gbc.gridx = 1;
        gbc.gridy = 0;
        queryPanel.add(facilityField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        queryPanel.add(new JLabel("Days (select multiple):"), gbc);

        String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        JList<String> daysList = new JList<>(days);
        daysList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane daysScroll = new JScrollPane(daysList);
        daysScroll.setPreferredSize(new Dimension(200, 100));
        gbc.gridx = 1;
        gbc.gridy = 1;
        queryPanel.add(daysScroll, gbc);

        JButton queryButton = new JButton("Query Availability");
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        queryPanel.add(queryButton, gbc);

        queryButton.addActionListener(e -> {
            String facility = facilityField.getText().trim();
            List<String> selectedDays = daysList.getSelectedValuesList();

            if (facility.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter a facility name",
                        "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (selectedDays.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please select at least one day",
                        "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                // Construct and send message
                DatagramPacket request = constructQueryMessage(facility, selectedDays);
                sendMessage(request);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void createBookPanel() {
        bookPanel = new JPanel();
        bookPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Facility name
        gbc.gridx = 0;
        gbc.gridy = 0;
        bookPanel.add(new JLabel("Facility Name:"), gbc);

        JTextField facilityField = new JTextField(20);
        gbc.gridx = 1;
        gbc.gridy = 0;
        bookPanel.add(facilityField, gbc);

        // Start day
        gbc.gridx = 0;
        gbc.gridy = 1;
        bookPanel.add(new JLabel("Start Day:"), gbc);

        String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        JComboBox<String> startDayCombo = new JComboBox<>(days);
        gbc.gridx = 1;
        gbc.gridy = 1;
        bookPanel.add(startDayCombo, gbc);

        // Start time
        gbc.gridx = 0;
        gbc.gridy = 2;
        bookPanel.add(new JLabel("Start Time (HH:MM):"), gbc);

        JPanel startTimePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        SpinnerModel startHourModel = new SpinnerNumberModel(9, 0, 23, 1);
        JSpinner startHourSpinner = new JSpinner(startHourModel);

        SpinnerModel startMinuteModel = new SpinnerNumberModel(0, 0, 59, 1);
        JSpinner startMinuteSpinner = new JSpinner(startMinuteModel);

        startTimePanel.add(startHourSpinner);
        startTimePanel.add(new JLabel(":"));
        startTimePanel.add(startMinuteSpinner);

        gbc.gridx = 1;
        gbc.gridy = 2;
        bookPanel.add(startTimePanel, gbc);

        // End day
        gbc.gridx = 0;
        gbc.gridy = 3;
        bookPanel.add(new JLabel("End Day:"), gbc);

        JComboBox<String> endDayCombo = new JComboBox<>(days);
        gbc.gridx = 1;
        gbc.gridy = 3;
        bookPanel.add(endDayCombo, gbc);

        // End time
        gbc.gridx = 0;
        gbc.gridy = 4;
        bookPanel.add(new JLabel("End Time (HH:MM):"), gbc);

        JPanel endTimePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        SpinnerModel endHourModel = new SpinnerNumberModel(10, 0, 23, 1);
        JSpinner endHourSpinner = new JSpinner(endHourModel);

        SpinnerModel endMinuteModel = new SpinnerNumberModel(0, 0, 59, 1);
        JSpinner endMinuteSpinner = new JSpinner(endMinuteModel);

        endTimePanel.add(endHourSpinner);
        endTimePanel.add(new JLabel(":"));
        endTimePanel.add(endMinuteSpinner);

        gbc.gridx = 1;
        gbc.gridy = 4;
        bookPanel.add(endTimePanel, gbc);

        // Book button
        JButton bookButton = new JButton("Book Facility");
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        bookPanel.add(bookButton, gbc);

        bookButton.addActionListener(e -> {
            String facility = facilityField.getText().trim();
            String startDay = (String) startDayCombo.getSelectedItem();
            int startHour = (Integer) startHourSpinner.getValue();
            int startMinute = (Integer) startMinuteSpinner.getValue();

            String endDay = (String) endDayCombo.getSelectedItem();
            int endHour = (Integer) endHourSpinner.getValue();
            int endMinute = (Integer) endMinuteSpinner.getValue();

            if (facility.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter a facility name",
                        "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                // Construct and send message
                DatagramPacket request = constructBookMessage(
                        facility, startDay, startHour, startMinute,
                        endDay, endHour, endMinute
                );
                sendMessage(request);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void createChangePanel() {
        changePanel = new JPanel();
        changePanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Confirmation number
        gbc.gridx = 0;
        gbc.gridy = 0;
        changePanel.add(new JLabel("Confirmation Number:"), gbc);

        JTextField confirmationField = new JTextField(10);
        gbc.gridx = 1;
        gbc.gridy = 0;
        changePanel.add(confirmationField, gbc);

        // Postpone or Advance
        gbc.gridx = 0;
        gbc.gridy = 1;
        changePanel.add(new JLabel("Action:"), gbc);

        JRadioButton postponeButton = new JRadioButton("Postpone");
        JRadioButton advanceButton = new JRadioButton("Advance");
        postponeButton.setSelected(true);

        ButtonGroup actionGroup = new ButtonGroup();
        actionGroup.add(postponeButton);
        actionGroup.add(advanceButton);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actionPanel.add(postponeButton);
        actionPanel.add(advanceButton);

        gbc.gridx = 1;
        gbc.gridy = 1;
        changePanel.add(actionPanel, gbc);

        // Offset
        gbc.gridx = 0;
        gbc.gridy = 2;
        changePanel.add(new JLabel("Offset (minutes):"), gbc);

        SpinnerModel offsetModel = new SpinnerNumberModel(30, 1, 1440, 5);
        JSpinner offsetSpinner = new JSpinner(offsetModel);
        gbc.gridx = 1;
        gbc.gridy = 2;
        changePanel.add(offsetSpinner, gbc);

        // Change button
        JButton changeButton = new JButton("Change Booking");
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        changePanel.add(changeButton, gbc);

        changeButton.addActionListener(e -> {
            String confirmationStr = confirmationField.getText().trim();
            boolean isPostpone = postponeButton.isSelected();
            int offset = (Integer) offsetSpinner.getValue();

            if (confirmationStr.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter a confirmation number",
                        "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                int confirmationNumber = Integer.parseInt(confirmationStr);
                // Construct and send message
                DatagramPacket request = constructChangeMessage(
                        confirmationNumber, isPostpone, offset
                );
                sendMessage(request);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Confirmation number must be a valid integer",
                        "Input Error", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void createMonitorPanel() {
        monitorPanel = new JPanel();
        monitorPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Facility name
        gbc.gridx = 0;
        gbc.gridy = 0;
        monitorPanel.add(new JLabel("Facility Name:"), gbc);

        JTextField facilityField = new JTextField(20);
        gbc.gridx = 1;
        gbc.gridy = 0;
        monitorPanel.add(facilityField, gbc);

        // Duration
        gbc.gridx = 0;
        gbc.gridy = 1;
        monitorPanel.add(new JLabel("Duration (minutes):"), gbc);

        SpinnerModel durationModel = new SpinnerNumberModel(5, 1, 60, 1);
        JSpinner durationSpinner = new JSpinner(durationModel);
        gbc.gridx = 1;
        gbc.gridy = 1;
        monitorPanel.add(durationSpinner, gbc);

        // Monitor button
        JButton monitorButton = new JButton("Start Monitoring");
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        monitorPanel.add(monitorButton, gbc);

        monitorButton.addActionListener(e -> {
            String facility = facilityField.getText().trim();
            int duration = (Integer) durationSpinner.getValue();

            if (facility.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter a facility name",
                        "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                monitorDuration = duration;
                // Construct and send message
                DatagramPacket request = constructMonitorMessage(facility, duration);
                sendMessage(request);

                // Start listening in a separate thread
                new Thread(() -> {
                    try {
                        monitorButton.setEnabled(false);
                        addResponse("Monitoring started for " + duration + " minutes...");
                        listenForMessages();
                        monitorButton.setEnabled(true);
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(FacilityBookingGUI.this,
                                    "Error during monitoring: " + ex.getMessage(),
                                    "Monitor Error", JOptionPane.ERROR_MESSAGE);
                            monitorButton.setEnabled(true);
                        });
                    }
                }).start();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    // Message construction methods
    private DatagramPacket constructQueryMessage(String facilityName, List<String> days) {
        byte[] nameBytes = facilityName.getBytes(StandardCharsets.UTF_8);

        // Construct day bitmask
        byte dayMask = 0;
        for (String day : days) {
            byte dayEnum = encodeDay(day);
            dayMask |= 1 << dayEnum;
        }
        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + 1 + 4 + nameBytes.length + 1);
        buffer.put((byte) 1); // request
        buffer.putInt(request_id++);
        buffer.put((byte) 1); // choice 1 (query)
        buffer.putInt(nameBytes.length);
        buffer.put(nameBytes);
        buffer.put(dayMask);

        byte[] message = buffer.array();
        return new DatagramPacket(message, message.length, serverAddress, SERVER_PORT);
    }

    private DatagramPacket constructBookMessage(String facilityName, String startDay,
                                                int startHour, int startMinute,
                                                String endDay, int endHour, int endMinute) {
        byte[] nameBytes = facilityName.getBytes(StandardCharsets.UTF_8);
        byte startDayCode = encodeDay(startDay);
        byte endDayCode = encodeDay(endDay);

        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + 1 + 4 + nameBytes.length + 6);
        buffer.put((byte) 1); // request
        buffer.putInt(request_id++);
        buffer.put((byte) 2); // choice 2 (book)
        buffer.putInt(nameBytes.length);
        buffer.put(nameBytes);
        buffer.put(startDayCode);
        buffer.put((byte) startHour);
        buffer.put((byte) startMinute);
        buffer.put(endDayCode);
        buffer.put((byte) endHour);
        buffer.put((byte) endMinute);

        byte[] message = buffer.array();
        return new DatagramPacket(message, message.length, serverAddress, SERVER_PORT);
    }

    private DatagramPacket constructChangeMessage(int confirmationNumber, boolean isPostpone, int offset) {
        int signOfOffset = isPostpone ? 1 : 0;

        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + 1 + 4 + 1 + 4);
        buffer.put((byte) 1); // request
        buffer.putInt(request_id++);
        buffer.put((byte) 3); // choice 3 (change)
        buffer.putInt(confirmationNumber);
        buffer.put((byte) signOfOffset);
        buffer.putInt(offset);

        byte[] message = buffer.array();
        return new DatagramPacket(message, message.length, serverAddress, SERVER_PORT);
    }

    private DatagramPacket constructMonitorMessage(String facilityName, int duration) {
        byte[] nameBytes = facilityName.getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + 1 + 4 + nameBytes.length + 4);
        buffer.put((byte) 1); // request
        buffer.putInt(request_id++);
        buffer.put((byte) 4); // choice 4 (monitor)
        buffer.putInt(nameBytes.length);
        buffer.put(nameBytes);
        buffer.putInt(duration);

        byte[] message = buffer.array();
        return new DatagramPacket(message, message.length, serverAddress, SERVER_PORT);
    }


    // Network methods
    private void sendMessage(DatagramPacket request) throws IOException {
        socket.send(request);
        addResponse("Request sent");

        byte[] buffer = new byte[1024];
        DatagramPacket response = new DatagramPacket(buffer, buffer.length);

        socket.setSoTimeout(3000);
        try {
            socket.receive(response);
            String responseString = new String(
                    response.getData(), 0, response.getLength(), StandardCharsets.UTF_8
            );
            addResponse("Response received: " + responseString);
        } catch (SocketTimeoutException e) {
            addResponse("No response received (timeout)");
        }
    }

    private void listenForMessages() throws IOException {
        byte[] buffer = new byte[1024];
        long duration = (long) monitorDuration * 1000 * 60;
        long starttime = System.currentTimeMillis();
        socket.setSoTimeout(1000);

        while (System.currentTimeMillis() - starttime < duration) {
            try {
                DatagramPacket request = new DatagramPacket(buffer, buffer.length);
                socket.receive(request);
                String receiveString = new String(
                        request.getData(), 0, request.getLength(), StandardCharsets.UTF_8
                );
                addResponse("Monitor update: " + receiveString);
            } catch (SocketTimeoutException e) {
                // Timeout is expected, continue
            }
        }
        addResponse("Monitoring completed");
    }

    private void addResponse(String text) {
        SwingUtilities.invokeLater(() -> {
            responseArea.append(text + "\n");
            // Scroll to the bottom
            responseArea.setCaretPosition(responseArea.getDocument().getLength());
        });
    }

    private static byte encodeDay(String day) {
        byte dayEnum;

        switch (day.trim().toLowerCase()) {
            case "monday" -> dayEnum = 0;
            case "tuesday" -> dayEnum = 1;
            case "wednesday" -> dayEnum = 2;
            case "thursday" -> dayEnum = 3;
            case "friday" -> dayEnum = 4;
            case "saturday" -> dayEnum = 5;
            case "sunday" -> dayEnum = 6;
            default -> throw new IllegalArgumentException("Invalid day: " + day);
        }
        return dayEnum;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FacilityBookingGUI gui = new FacilityBookingGUI();
            gui.setVisible(true);
        });
    }
}