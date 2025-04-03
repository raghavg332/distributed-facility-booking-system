#include <iostream>
#include <arpa/inet.h>
#include <unistd.h>
#include <cstring>
#include <pqxx/pqxx>
#include "message.cpp"
#include "facility.cpp"
#include <vector>
#include <cmath>
#include <atomic>
#include <unordered_map>
#include <thread>
#include <map>
#include <csignal>




std::atomic<bool> running(true);
void handleSignal(int) {
    std::cout << "Received signal to terminate. Cleaning up..." << std::endl;
    running = false;
}

enum {
    PREPONE = 0,
    POSTPONE = 1
};

struct MonitorClients {
    int socket_fd;
    sockaddr_in clientAddress;
    socklen_t clientAddressLength = sizeof(clientAddress);
    std::chrono::steady_clock::time_point expires;
    Message msg;
};

std::unordered_multimap<std::string, MonitorClients> clients;

class Connection {
    public :
        int socket_fd;
        sockaddr_in serverAddress, clientAddress;
        socklen_t clientAddressLength = sizeof(clientAddress);
        unsigned char buffer[1024];
        Connection(int port = 8000):buffer{0} {
            socket_fd = socket(AF_INET, SOCK_DGRAM, 0);
            serverAddress.sin_family = AF_INET;
            serverAddress.sin_port = htons(port);
            serverAddress.sin_addr.s_addr = INADDR_ANY;
            std::cout << "Socket created" << std::endl;
            // bind(socket_fd, (struct sockaddr *)&serverAddress, sizeof(serverAddress));
            if (bind(socket_fd, (struct sockaddr *)&serverAddress, sizeof(serverAddress)) < 0) {
                perror("Bind failed");
                close(socket_fd);
                exit(EXIT_FAILURE);
            }
        }
        ~Connection() {
            close(socket_fd);
            std::cout << "Socket closed" << std::endl;
        }
        void listen() {
            while (running) {
                memset(buffer, 0, sizeof(buffer));
                int n = recvfrom(socket_fd, buffer, sizeof(buffer), 0, (struct sockaddr *)&clientAddress, &clientAddressLength);
                if (n < 0) {
                    std::cerr << "Receive failed" << std::endl;
                }
                std::cout << "Received: " << buffer << std::endl;
                // sendto(socket_fd, buffer, n, 0, (struct sockaddr *)&clientAddress, clientAddressLength);
                Message msg(buffer, n);
                std::cout << "Request Type: " << (int)msg.msg.requestType << std::endl;
                std::cout << "Request ID: " << msg.msg.requestID << std::endl;
                std::cout << "Choice: " << (int)msg.msg.choice << std::endl;
                uint32_t facilityNameLength;
                std::string facilityName;
                switch ((int)msg.msg.choice)
                {
                case 1: {
                    memcpy(&facilityNameLength, msg.msg.messageData.data(), sizeof(facilityNameLength));
                    facilityNameLength = ntohl(facilityNameLength);
                    facilityName = std::string((char*)msg.msg.messageData.data() + 4, static_cast<size_t>(facilityNameLength));
                    std::cout << "Facility Name Length: " << facilityNameLength << std::endl;
                    std::cout << "Facility Name: " << facilityName << std::endl;
                    // Check for availability
                    facility fac(facilityName);
                    std::cout << "Facility ID: " << fac.facilityId << std::endl;
                    char maskedDaysBit;
                    memcpy(&maskedDaysBit, msg.msg.messageData.data() + 4 + facilityNameLength, sizeof(maskedDaysBit));
                    std::vector<int> days;
                    while (maskedDaysBit > 0) {
                        int day = floor(log2(maskedDaysBit));
                        days.push_back(day);
                        maskedDaysBit = maskedDaysBit - pow(2, day);
                    }
                    std::cout << "Days: ";
                    for (int i = 0; i < days.size(); i++) {
                        std::cout << days[i] << " ";
                    }
                    std::cout << std::endl;
                    std::map<uint, std::map<uint, uint>> bookedSlots;
                    for (int i = 0; i < days.size(); i++) {
                        bookedSlots[days[i]] = fac.getBookingTimes(days[i]);
                    }
                    std::vector<unsigned char> data;
                    data.push_back((unsigned char) days.size());
                    std::cout << "Number of days: " << days.size() << std::endl;
                    for (const auto& [day, slots] : bookedSlots) {
                        std::cout << "Day: " << day << std::endl;
                        data.push_back((unsigned char)day);
                        data.push_back((unsigned char)slots.size());
                        for (const auto& [start, end] : slots) {
                            std::cout << "Start: " << start << ", End: " << end << std::endl;
                            data.push_back((unsigned char)(start / 100));
                            data.push_back((unsigned char)(start % 100));
                            data.push_back((unsigned char)(end / 100));
                            data.push_back((unsigned char)(end % 100));
                        }
                    }
                    auto [total_length, replyBuffer] = msg.createReply(data);
                    sendto(socket_fd, replyBuffer, total_length, 0, (struct sockaddr *)&clientAddress, clientAddressLength);
                    break;
                }
                case 2: {
                    int offset = 0;
                    uint32_t userNameLength;
                    memcpy(&userNameLength, msg.msg.messageData.data(), sizeof(userNameLength));
                    userNameLength = ntohl(userNameLength);
                    offset += sizeof(userNameLength);
                    std::string userName = std::string((char*)msg.msg.messageData.data() + offset, static_cast<size_t>(userNameLength));
                    offset += userNameLength;
                    std::cout << "User Name Length: " << userNameLength << std::endl;
                    std::cout << "User Name: " << userName << std::endl;
                    memcpy(&facilityNameLength, msg.msg.messageData.data() + offset, sizeof(facilityNameLength));
                    facilityNameLength = ntohl(facilityNameLength);
                    offset += sizeof(facilityNameLength);
                    facilityName = std::string((char*)msg.msg.messageData.data() + offset, static_cast<size_t>(facilityNameLength));
                    offset += facilityNameLength;
                    std::cout << "Facility Name Length: " << facilityNameLength << std::endl;
                    std::cout << "Facility Name: " << facilityName << std::endl;
                    uint8_t startDay, startHour, startMinute;
                    // std::cout << "Raw bytes (hex) at offset " << offset << ": ";
                    // for (size_t i = 0; i < sizeof(startDay); i++) {
                    //     printf("%02X ", static_cast<uint8_t>(msg.msg.messageData.data()[offset + i]));
                    // }
                    std::cout << std::endl;
                    memcpy(&startDay, msg.msg.messageData.data() + offset, sizeof(startDay));
                    offset += sizeof(startDay);
                    memcpy(&startHour, msg.msg.messageData.data() + offset, sizeof(startHour));
                    offset += sizeof(startHour);
                    memcpy(&startMinute, msg.msg.messageData.data() + offset, sizeof(startMinute));
                    offset += sizeof(startMinute);
                    std::cout << "Start Day: " << (int)startDay << std::endl;
                    std::cout << "Start Hour: " << (int)startHour << std::endl;
                    std::cout << "Start Minute: " << (int)startMinute << std::endl;
                    uint8_t endDay, endHour, endMinute;
                    memcpy(&endDay, msg.msg.messageData.data() + offset, sizeof(endDay));
                    offset += sizeof(endDay);
                    memcpy(&endHour, msg.msg.messageData.data() + offset, sizeof(endHour));
                    offset += sizeof(endHour);
                    memcpy(&endMinute, msg.msg.messageData.data() + offset, sizeof(endMinute));
                    offset += sizeof(endMinute);
                    std::cout << "End Day: " << (int)endDay << std::endl;
                    std::cout << "End Hour: " << (int)endHour << std::endl;
                    std::cout << "End Minute: " << (int)endMinute << std::endl;
                    // Check for booking
                    facility fac(facilityName);
                    std::cout << "Facility ID: " << fac.facilityId << std::endl;

                    auto [bookingStatus, bookingResult] = fac.addBooking(startDay, startHour, startMinute, endDay, endHour, endMinute, userName);
                    std::cout << "Booking Status: " << bookingStatus << std::endl;
                    std::cout << "Booking Result: " << bookingResult << std::endl;
                    std::vector<unsigned char> data;
                    data.push_back((unsigned char) bookingStatus);
                    uint32_t bookingResultLength = htonl(bookingResult.size());
                    data.insert(data.end(), (unsigned char*)&bookingResultLength, (unsigned char*)&bookingResultLength + sizeof(bookingResultLength));
                    data.insert(data.end(), bookingResult.begin(), bookingResult.end());
                    auto [total_length, replyBuffer] = msg.createReply(data);
                    sendto(socket_fd, replyBuffer, total_length, 0, (struct sockaddr *)&clientAddress, clientAddressLength);
                    break;
                }
                case 3: {
                    int offset = 0;
                    uint32_t userNameLength;
                    memcpy(&userNameLength, msg.msg.messageData.data(), sizeof(userNameLength));
                    userNameLength = ntohl(userNameLength);
                    offset += sizeof(userNameLength);
                    std::string userName = std::string((char*)msg.msg.messageData.data() + offset, static_cast<size_t>(userNameLength));
                    offset += userNameLength;
                    std::cout << "User Name Length: " << userNameLength << std::endl;
                    std::cout << "User Name: " << userName << std::endl;

                    uint32_t confirmationId;
                    memcpy(&confirmationId, msg.msg.messageData.data() + offset, sizeof(confirmationId));
                    confirmationId = ntohl(confirmationId);
                    std::cout << "Confirmation ID: " << (int)confirmationId << std::endl;
                    offset += sizeof(confirmationId);

                    Booking retrievedBooking = Booking(confirmationId);
                    std::cout << "Booking ID: " << retrievedBooking.bookingID << std::endl;
                    std::cout << "Facility ID: " << retrievedBooking.facilityId << std::endl;

                    if (userName != retrievedBooking.userName) {
                        std::cerr << "User name does not match" << std::endl;
                        std::vector<unsigned char> data;
                        // data.push_back((unsigned char) 3);
                        auto [total_length, replyBuffer] = msg.createReply(data, 3);
                        sendto(socket_fd, replyBuffer, total_length, 0, (struct sockaddr *)&clientAddress, clientAddressLength);
                        break;
                    }

                    uint8_t preponeOrPostpone;
                    memcpy(&preponeOrPostpone, msg.msg.messageData.data() + offset, sizeof(preponeOrPostpone));
                    std::cout << "Prepone or Postpone: " << (int)preponeOrPostpone << std::endl;
                    offset += sizeof(preponeOrPostpone);

                    uint32_t shiftMinutes;
                    memcpy(&shiftMinutes, msg.msg.messageData.data() + offset, sizeof(shiftMinutes));
                    shiftMinutes = ntohl(shiftMinutes);
                    std::cout << "Shift Minutes: " << shiftMinutes << std::endl;
                    offset += sizeof(shiftMinutes);
                    int change;
                    if (preponeOrPostpone == POSTPONE) {
                        change = (int)shiftMinutes;
                    }
                    else {
                        change = (int)-shiftMinutes;
                    }
                    std::cout << "Change: " << change << std::endl;

                    int changeStatus = retrievedBooking.changeBookingMinutes(change);
                    std::cout << "Change Status: " << changeStatus << std::endl;
                    std::vector<unsigned char> data;
                    data.push_back((unsigned char)retrievedBooking.bookingStartDay);
                    data.push_back((unsigned char)retrievedBooking.bookingStartHour);
                    data.push_back((unsigned char)retrievedBooking.bookingStartMinute);
                    data.push_back((unsigned char)retrievedBooking.bookingEndDay);
                    data.push_back((unsigned char)retrievedBooking.bookingEndHour);
                    data.push_back((unsigned char)retrievedBooking.bookingEndMinute);

                    auto [totallength, buffer] = msg.createReply(data, changeStatus);
                    sendto(socket_fd, buffer, totallength, 0, (struct sockaddr *)&clientAddress, clientAddressLength);
                    std::cout << "Reply sent" << std::endl;
                    break;
                }

                case 4: {
                    int offset = 0;
                    memcpy(&facilityNameLength, msg.msg.messageData.data(), sizeof(facilityNameLength));
                    facilityNameLength = ntohl(facilityNameLength);
                    offset += sizeof(facilityNameLength);
                    facilityName = std::string((char*)msg.msg.messageData.data() + offset, static_cast<size_t>(facilityNameLength));
                    offset += facilityNameLength;
                    std::cout << "Facility Name Length: " << facilityNameLength << std::endl;
                    std::cout << "Facility Name: " << facilityName << std::endl;

                    uint32_t durationToWatch;
                    memcpy(&durationToWatch, msg.msg.messageData.data() + offset, sizeof(durationToWatch));
                    durationToWatch = ntohl(durationToWatch);
                    std::cout << "Duration to watch: " << durationToWatch << std::endl;

                    facility fac(facilityName);

                    clients.emplace(fac.facilityName, MonitorClients{socket_fd, clientAddress, clientAddressLength , std::chrono::steady_clock::now() + std::chrono::minutes(durationToWatch), msg});
                    
                    std::string message = "Monitoring started for facility " + fac.facilityName + " for " + std::to_string(durationToWatch) + " minutes";
                    std::vector<unsigned char> data;
                    data.push_back((unsigned char)message.size());
                    data.insert(data.end(), message.begin(), message.end());
                    auto [totallength, replybuffer] = msg.createReply(data);
                    std::cout << "Sending notification to client" << std::endl;
                    sendto(socket_fd, replybuffer, totallength, 0,
                           (struct sockaddr *)&clientAddress, clientAddressLength);
                    std::cout << "Reply sent" << std::endl;
                }

                default:
                    break;
                }
                std::this_thread::sleep_for(std::chrono::milliseconds(100));
            }
            std::cout << "Exiting listen loop" << std::endl;
            std::cout << "Closing socket" << std::endl;
            close(socket_fd);
            std::cout << "Socket closed" << std::endl;
        }


};

void notificationListenerThread() {
    try {
        pqxx::connection conn("dbname=facilitydb user=parmatmasingh password=aishi2705 host=localhost port=5432");
        pqxx::work txn(conn);
        txn.exec("LISTEN booking_update");
        txn.commit();

        conn.listen("booking_update", [&](pqxx::notification notif) {
            std::string channel = std::string(notif.channel);
            std::string payload = std::string(notif.payload);

            std::cout << "Notification received on channel: " << channel << std::endl;
            std::cout << "Payload: " << payload << std::endl;

            std::stringstream ss((std::string(payload)));
            std::string action, facilityName;
            int startDay, startHour, startMinute, endDay, endHour, endMinute;

            std::getline(ss, action, ':');
            std::getline(ss, facilityName, ':');
            ss >> startDay;
            ss.ignore(1, ':');
            ss >> startHour;
            ss.ignore(1, ':');
            ss >> startMinute;
            ss.ignore(1, ':');
            ss >> endDay;
            ss.ignore(1, ':');
            ss >> endHour;
            ss.ignore(1, ':');
            ss >> endMinute;

            if (action == "INSERT" || action == "UPDATE" || action == "DELETE") {
                std::cout << "Action: " << action << std::endl;
                std::cout << "Facility Name: " << facilityName << std::endl;

                auto range = clients.equal_range(facilityName);
                for (auto it = range.first; it != range.second; ++it) {
                    if (std::chrono::steady_clock::now() < it->second.expires) {
                        std::vector<unsigned char> data;
                        std::string msg;

                        if (action == "INSERT" || action == "UPDATE") {
                            msg = "📢 Booking " + action + " for facility " + facilityName +
                                  " from Day " + std::to_string(startDay) + " " +
                                  std::to_string(startHour) + ":" + std::to_string(startMinute) +
                                  " to Day " + std::to_string(endDay) + " " +
                                  std::to_string(endHour) + ":" + std::to_string(endMinute);
                        } else { // DELETE
                            msg = "📢 Booking " + action + " for facility " + facilityName;
                        }

                        data.push_back((unsigned char)msg.size());
                        data.insert(data.end(), msg.begin(), msg.end());
                        auto [totallenght, replybuffer] = it->second.msg.createReply(data);
                        std::cout << "Sending notification to client" << std::endl;
                        sendto(it->second.socket_fd, replybuffer, totallenght, 0,
                               (struct sockaddr *)&it->second.clientAddress, it->second.clientAddressLength);
                    } else {
                        std::cout << "Client expired: " << std::endl;
                    }
                }
            } else {
                std::cerr << "Invalid action" << std::endl;
            }
        });

        while (running) {
            conn.await_notification(10);
        }
    }
    catch (const std::exception &e) {
        std::cerr << "Error in notification listener thread: " << e.what() << std::endl;
    }
}



int main() {
    std::cout << "Starting server..." << std::endl;
    std::signal(SIGINT, handleSignal);
    std::signal(SIGTERM, handleSignal);
    Connection conn;
    std::thread notificationThread(notificationListenerThread);
    std::thread listenerThread([&conn]() {
        conn.listen();
    });
    notificationThread.join();
    listenerThread.join();

    std::cout << "Server stopped" << std::endl;
}