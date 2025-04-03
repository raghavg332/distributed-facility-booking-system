import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Main class for the Facility Booking System client application.
 * This program allows users to interact with a facility booking server
 * using UDP protocol to query availability, book facilities, manage bookings,
 * and monitor facilities.
 */
public class Main {
    private static int monitorDuration; // Duration in minutes for facility monitoring
    public static byte[] usernameBytes; // Global storage for the user's username in byte format

    /**
     * Main method that initializes the client and handles user interaction.
     * @param args Command line arguments (not used)
     * @throws IOException If there's an error with network operations
     */
    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);

        // Initialize UDP socket and server information
        DatagramSocket socket = new DatagramSocket(8080);
        InetAddress serverAddress = InetAddress.getByName("192.168.0.113"); //IP of server machine
        int serverPort = 8014;
        int request_id = 4000; // Starting request ID

        // Get username from user
        System.out.println("Welcome to the Facility Booking System!");
        System.out.println("Please enter your username: ");
        String username = scanner.nextLine();
        usernameBytes = username.getBytes(StandardCharsets.UTF_8);

        // Main application loop
        while (true){
            System.out.println("Menu : ");
            System.out.println("1. Query Availability");
            System.out.println("2. Book Facility");
            System.out.println("3. Change Booking");
            System.out.println("4. Monitor Facility (with Callback)");
            System.out.println("5. View All Your Bookings");
            System.out.println("6. Get Facility Access Token");
            System.out.println("7. Exit");

            int choice = scanner.nextInt();
            scanner.nextLine(); // Consume the newline character

            // Exit application if user chooses option 7
            if (choice==7){
                break;
            }

            // Construct appropriate request message based on user's choice
            DatagramPacket request = null;
            request = construct_message(choice, request_id, serverAddress, serverPort);

            request_id++; // Increment request ID for the next request

            String response = null;
            send_message(request, socket, choice);

            // For monitoring, listen for callback messages from the server
            if (choice==4){
                listenForMessages(socket);
            }
        }
        // Close socket when exiting
        socket.close();
        return;
    }

    /**
     * Constructs a UDP packet with the appropriate format based on the selected operation.
     *
     * @param choice User menu selection (1-6)
     * @param request_id Unique identifier for this request
     * @param serverAddress Server IP address
     * @param serverPort Server port number
     * @return Constructed DatagramPacket ready to be sent
     */
    private static DatagramPacket construct_message(int choice, int request_id, InetAddress serverAddress, int serverPort){
        Scanner scanner = new Scanner(System.in);

        // Option 1: Query Availability
        if (choice == 1){
            System.out.println("Name of facility to enquire: ");
            String name = scanner.nextLine();

            System.out.println("Enter days (e.g., Monday, Wednesday, Friday): ");
            String dayLine = scanner.nextLine();
            String[] days = dayLine.split(",");

            // Trim and map day names to bitmask
            byte dayMask = 0;
            for (String d : days) {
                byte dayEnum = encodeDay(d);
                // Set the corresponding bit in the bitmask for each day
                dayMask |= 1 << dayEnum;
            }

            byte response_or_request = 1; // 1 indicates this is a request
            byte choice_byte = 1; // Option 1 = query availability
            byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);

            // Prepare buffer with required data
            ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + 1 + 4 + nameBytes.length + 1);

            buffer.put(response_or_request);
            buffer.putInt(request_id);
            buffer.put(choice_byte);
            buffer.putInt(nameBytes.length); // Length of facility name
            buffer.put(nameBytes); // Facility name bytes
            buffer.put(dayMask); // Days bitmask

            byte[] message = buffer.array();
            return new DatagramPacket(message, message.length, serverAddress, serverPort);
        }
        // Option 2: Book Facility
        else if (choice==2) {
            System.out.println("Name of facility to book: ");
            String name = scanner.nextLine();
            byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);

            // Get booking start information
            System.out.println("Enter day (e.g., Monday, Wednesday, Friday) of booking start: ");
            String start = scanner.nextLine();
            byte startDay = encodeDay(start);

            System.out.println("Enter start time (hr): ");
            int startHour = scanner.nextInt();
            scanner.nextLine(); // Consume newline

            System.out.println("Enter start time (minute): ");
            int startMinute = scanner.nextInt();
            scanner.nextLine(); // Consume newline

            // Get booking end information
            System.out.println("Enter day (e.g., Monday, Wednesday, Friday) of booking end: ");
            String end = scanner.nextLine();
            byte endDay = encodeDay(end);

            System.out.println("Enter end time (hr): ");
            int endHour = scanner.nextInt();
            scanner.nextLine(); // Consume newline

            System.out.println("Enter end time (minute): ");
            int endMinute = scanner.nextInt();
            scanner.nextLine(); // Consume newline

            byte response_or_request = 1; // 1 indicates this is a request
            byte choice_byte = 2; // Option 2 = book facility

            // Prepare buffer with booking data
            ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + 1 + 4 + usernameBytes.length + 4 + nameBytes.length + 6);
            buffer.put(response_or_request);
            buffer.putInt(request_id);
            buffer.put(choice_byte);
            buffer.putInt(usernameBytes.length);
            buffer.put(usernameBytes);
            buffer.putInt(nameBytes.length);
            buffer.put(nameBytes);
            // Add booking time details
            buffer.put(startDay);
            buffer.put((byte) startHour);
            buffer.put((byte) startMinute);
            buffer.put(endDay);
            buffer.put((byte) endHour);
            buffer.put((byte) endMinute);

            byte[] message = buffer.array();
            return new DatagramPacket(message, message.length, serverAddress, serverPort);
        }
        // Option 3: Change Booking
        else if (choice == 3){
            System.out.println("What is the confirmation number of your booking? ");
            int confirmation_number = scanner.nextInt();
            scanner.nextLine(); // Consume newline

            System.out.println("Do you want to Postpone (A) the booking or Advance (B) the booking? ");
            String post_or_pre = scanner.nextLine();
            int sign_of_offset;
            if (post_or_pre.equals("A")){
                sign_of_offset = 1; // 1 for postpone (positive offset)
            }
            else{
                sign_of_offset = 0; // 0 for advance (negative offset)
            }

            System.out.println("By how many minutes do you wish to shift the booking? ");
            int offset = scanner.nextInt();

            byte response_or_request = 1; // 1 indicates this is a request
            byte choice_byte = 3; // Option 3 = change booking

            // Prepare buffer with change booking data
            ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + 1 + 4 + usernameBytes.length + 4 + 1 + 4);
            buffer.put(response_or_request);
            buffer.putInt(request_id);
            buffer.put((byte) choice_byte);
            buffer.putInt(usernameBytes.length);
            buffer.put(usernameBytes);
            buffer.putInt(confirmation_number);
            buffer.put((byte) sign_of_offset); // 1 = postpone, 0 = advance
            buffer.putInt(offset); // Number of minutes to shift

            byte[] message = buffer.array();
            return new DatagramPacket(message, message.length, serverAddress, serverPort);
        }
        // Option 4: Monitor Facility
        else if (choice==4) {
            System.out.println("What is the facility which you would like to monitor? ");
            String facility = scanner.nextLine();
            byte[] byteName = facility.getBytes(StandardCharsets.UTF_8);

            System.out.println("How long would you like to monitor the facility? ");
            int duration = scanner.nextInt();
            scanner.nextLine(); // Consume newline

            monitorDuration = duration; // Store duration for callback listening

            byte response_or_request = 1; // 1 indicates this is a request
            byte choice_byte = 4; // Option 4 = monitor facility

            // Prepare buffer with monitoring data
            ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + 1 + 4 + byteName.length + 4);
            buffer.put(response_or_request);
            buffer.putInt(request_id);
            buffer.put((byte) choice);
            buffer.putInt(byteName.length);
            buffer.put(byteName);
            buffer.putInt(duration); // Duration in minutes

            byte[] message = buffer.array();
            return new DatagramPacket(message, message.length, serverAddress, serverPort);
        }
        // Option 5: View All Your Bookings
        else if (choice == 5){
            byte response_or_request = 1; // 1 indicates this is a request
            byte choice_byte = 5; // Option 5 = view all bookings

            // Prepare buffer with username for getting bookings
            ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + 1 + 4 + usernameBytes.length);
            buffer.put(response_or_request);
            buffer.putInt(request_id);
            buffer.put(choice_byte);
            buffer.putInt(usernameBytes.length);
            buffer.put(usernameBytes);

            byte[] message = buffer.array();
            return new DatagramPacket(message, message.length, serverAddress, serverPort);
        }
        // Option 6: Get Facility Access Token
        else if (choice==6){
            byte response_or_request = 1; // 1 indicates this is a request
            byte choice_byte = 6; // Option 6 = get access token

            System.out.println("What is the Confirmation Number of your booking? ");
            int confimation_id = scanner.nextInt();

            // Prepare buffer with confirmation ID to get access token
            ByteBuffer buffer = ByteBuffer.allocate(1+4+1+4+ usernameBytes.length+4);
            buffer.put(response_or_request);
            buffer.putInt(request_id);
            buffer.put(choice_byte);
            buffer.putInt(usernameBytes.length);
            buffer.put(usernameBytes);
            buffer.putInt(confimation_id);

            byte[] message = buffer.array();
            return new DatagramPacket(message, message.length, serverAddress, serverPort);
        }
        return null;
    }

    /**
     * Sends a request message to the server and handles the response.
     * Implements retry logic for reliability.
     *
     * @param request The prepared request packet
     * @param socket The UDP socket for communication
     * @param choice User's menu choice (used for response handling)
     * @throws IOException If there's an error with network operations
     */
    private static void send_message(DatagramPacket request, DatagramSocket socket, int choice) throws IOException {
        int retries = 3; // Number of retry attempts
        byte[] buffer = new byte[1024]; // Buffer for response
        DatagramPacket response = new DatagramPacket(buffer, buffer.length);

        for (int attempt = 1; attempt <= retries; attempt++) {
            try {
                socket.send(request);
                System.out.println("📤 Sent request (Attempt " + attempt + ")");

                socket.setSoTimeout(3000); // Wait up to 3s for response
                socket.receive(response);

                byte[] respBytes = response.getData();
                int respLength = response.getLength();

                System.out.println();
                // Process the response based on the original request type
                handleServerResponse(respBytes, respLength, choice);
                return;

            } catch (SocketTimeoutException e) {
                System.out.println("⏱ Timeout on attempt " + attempt);
            } catch (IOException e) {
                System.out.println("❌ IO Error: " + e.getMessage());
                break;
            }
        }

        System.out.println("❌ Failed after " + retries + " attempts.");
        return;
    }

    /**
     * Listens for callback messages from the server during facility monitoring.
     *
     * @param listen_socket The UDP socket for receiving messages
     * @throws IOException If there's an error with network operations
     */
    private static void listenForMessages(DatagramSocket listen_socket) throws IOException {
        byte[] buffer = new byte[1024];
        long duration = (long) monitorDuration * 1000 * 60; // Convert minutes to milliseconds
        long starttime = System.currentTimeMillis();
        listen_socket.setSoTimeout(1000); // Set a 1-second timeout for socket receive operation

        // Continue listening until the monitoring duration is complete
        while (System.currentTimeMillis() - starttime < duration){
            try {
                DatagramPacket request = new DatagramPacket(buffer, buffer.length);
                listen_socket.receive(request);
                // Convert received data to string and display
                String receiveString = new String(request.getData(), 0, request.getLength(), StandardCharsets.UTF_8);
                System.out.println(receiveString);
            }
            catch (SocketTimeoutException e){
                // Ignore timeout, just continue the loop
            }
        }
    }

    /**
     * Processes server responses based on the original request type.
     *
     * @param response The response data bytes
     * @param length Length of valid data in the response
     * @param choice The original user menu choice
     */
    private static void handleServerResponse(byte[] response, int length, int choice){
        ByteBuffer buffer = ByteBuffer.wrap(response, 0, length);
        byte responseType = buffer.get(); // Response indicator
        int requestId = buffer.getInt(); // ID matching the original request
        byte choice_rec = buffer.get(); // Choice code from response
        byte ifError = buffer.get(); // Error code (0 = success)

        // Option 1: Handle query availability response
        if (choice == 1){
            int dataLen = buffer.getInt();
            queryAvailabilityHandler(buffer);
            return;
        }
        // Option 2: Handle booking confirmation response
        else if (choice == 2){
            int unnecessary = buffer.getInt();
            byte status = buffer.get();
            if (status == 1){
                //Booking conflict
                System.out.println("Booking cannot be made! There is a conflict with another booking.");
                return;
            }
            int lengthMessage = buffer.getInt();
            byte[] stringBytes = new byte[lengthMessage];

            buffer.get(stringBytes);
            String confirmationId = new String(stringBytes, StandardCharsets.UTF_8);
            System.out.println("Booking has been made! Confirmation ID: " + confirmationId);
            return;
        }
        // Option 3: Handle booking change response
        else if (choice ==3){
            if (ifError==3){
                System.out.println("You are not authorised to change this booking!");
            }
            else if (ifError==1){
                //conflict
                System.out.println("Booking cannot be changed! There is a conflict with another booking.");
            }
            else {
                // Successful change - display new booking details
                int lengthMessage = buffer.getInt();
                byte startDay = buffer.get();
                byte startHour = buffer.get();
                byte startMinute = buffer.get();
                byte endDay = buffer.get();
                byte endHour = buffer.get();
                byte endMinute = buffer.get();
                System.out.println("Booking has been changed! New booking details:");
                System.out.println("Start: " + decodeDay(startDay) + " " + startHour + ":" + startMinute);
                System.out.println("End: " + decodeDay(endDay) + " " + endHour + ":" + endMinute);
            }
            return;
        }
        // Option 4: Handle monitor setup confirmation response
        else if (choice == 4){
            byte[] remaining = new byte[buffer.remaining()];
            buffer.get(remaining);
            String message = new String(remaining, StandardCharsets.UTF_8);
            System.out.println(message);
            return;
        }
        // Option 5: Handle view bookings response
        else if (choice == 5){
            int dataLen = buffer.getInt();
            byte numBookings = buffer.get();
            System.out.println("📋 You have " + numBookings + " booking" + (numBookings == 1 ? "" : "s") + ":");
            System.out.println("─────────────────────────────────────────────");

            // Process each booking in the response
            for (int i = 0; i < numBookings; i++){
                byte startDay = buffer.get();
                byte startHour = buffer.get();
                byte startMinute = buffer.get();
                byte endHour = buffer.get();
                byte endMinute = buffer.get();

                byte booking_id_length = buffer.get();
                byte[] booking_id = new byte[booking_id_length];
                buffer.get(booking_id);

                byte facility_length = buffer.get();
                byte[] facility = new byte[facility_length];
                buffer.get(facility);

                // Convert bytes to human-readable format
                String bid = new String(booking_id, StandardCharsets.UTF_8);
                String facilityName = new String(facility, StandardCharsets.UTF_8);
                String start = String.format("%02d:%02d", startHour, startMinute);
                String end = String.format("%02d:%02d", endHour, endMinute);

                // Display booking details
                System.out.println("📌 Booking ID : " + bid);
                System.out.println("🏟️  Facility   : " + facilityName);
                System.out.println("📅 Day        : " + decodeDay(startDay));
                System.out.println("⏰ Time       : " + start + " → " + end);
                System.out.println("─────────────────────────────────────────────");
            }
            return;
        }
        // Option 6: Handle access token response
        else if (choice == 6){
            if (ifError == 0){
                // Success - display access token
                int lengthMessage = buffer.getInt();
                byte lengthCode = buffer.get();
                byte[] accessCode = new byte[lengthCode];
                buffer.get(accessCode);
                String accessCodeString = new String(accessCode, StandardCharsets.UTF_8);
                System.out.println(" Received Access Token: " + accessCodeString);
            }
            else if (ifError == 1){
                //access code has been generated for this booking, just print that
                System.out.println("Access Token has already been generated for this booking.");
            }
            else if (ifError == 2){
                //wrong user for this booking
                System.out.println("You are not the owner of this booking.");
            }
            return;
        }
    }

    /**
     * Processes a facility availability response and displays available time slots.
     *
     * @param buffer ByteBuffer containing availability data
     */
    private static void queryAvailabilityHandler(ByteBuffer buffer){
        byte numDays = buffer.get(); // Number of days in the response

        // Process each day's availability
        for (int i = 0; i < numDays; i++) {
            byte day = buffer.get();
            String dayString = decodeDay(day);
            byte numSlots = buffer.get();

            // Parse all booked slots into a list
            List<int[]> booked = new ArrayList<>();
            for (int j = 0; j < numSlots; j++) {
                int startHour = Byte.toUnsignedInt(buffer.get());
                int startMinute = Byte.toUnsignedInt(buffer.get());
                int endHour = Byte.toUnsignedInt(buffer.get());
                int endMinute = Byte.toUnsignedInt(buffer.get());

                // Convert hours/minutes to minutes for easier calculation
                int start = startHour * 60 + startMinute;
                int end = endHour * 60 + endMinute;
                booked.add(new int[]{start, end});
            }

            // Sort by start time just in case
            booked.sort(Comparator.comparingInt(a -> a[0]));

            System.out.println("🗓 Day: " + dayString);
            System.out.println("🟢 Available slots:");

            // Define day boundaries (in minutes)
            int startOfDay = 0;           // 00:00
            int endOfDay = 24 * 60;       // 24:00

            // Find gaps between booked slots (these are available)
            int prevEnd = startOfDay;
            for (int[] slot : booked) {
                int slotStart = slot[0];
                if (slotStart > prevEnd) {
                    // Found available slot between prevEnd and slotStart
                    printSlot(prevEnd, slotStart);
                }
                prevEnd = Math.max(prevEnd, slot[1]);
            }

            // Check for availability at the end of the day
            if (prevEnd < endOfDay) {
                printSlot(prevEnd, endOfDay);
            }

            System.out.println();
        }
    }

    /**
     * Helper method to print a time slot in a formatted way.
     *
     * @param start Start time in minutes from midnight
     * @param end End time in minutes from midnight
     */
    private static void printSlot(int start, int end) {
        int startH = start / 60;
        int startM = start % 60;
        int endH = end / 60;
        int endM = end % 60;
        System.out.printf("   ➤ %02d:%02d to %02d:%02d\n", startH, startM, endH, endM);
    }

    /**
     * Converts a day name to its corresponding numeric code.
     *
     * @param day Day name (e.g., "Monday")
     * @return Byte code representing the day (0-6)
     */
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

    /**
     * Converts a day code back to its name.
     *
     * @param dayEnum Byte code representing the day (0-6)
     * @return Day name (e.g., "Monday")
     */
    private static String decodeDay(byte dayEnum) {
        return switch (dayEnum) {
            case 0 -> "Monday";
            case 1 -> "Tuesday";
            case 2 -> "Wednesday";
            case 3 -> "Thursday";
            case 4 -> "Friday";
            case 5 -> "Saturday";
            case 6 -> "Sunday";
            default -> "Invalid(" + dayEnum + ")";
        };
    }
}