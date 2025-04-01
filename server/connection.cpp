#include <iostream>
#include <arpa/inet.h>
#include <unistd.h>
#include <cstring>
#include "message.cpp"


class Connection {
    public :
        int socket_fd;
        sockaddr_in serverAddress, clientAddress;
        socklen_t clientAddressLength = sizeof(clientAddress);
        unsigned char buffer[1024];
        Connection(int port = 8080):buffer{0} {
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
            while (true) {
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
                case 1:
                    memcpy(&facilityNameLength, msg.msg.messageData.data(), sizeof(facilityNameLength));
                    facilityNameLength = ntohl(facilityNameLength);
                    facilityName = std::string((char*)msg.msg.messageData.data(), static_cast<size_t>(facilityNameLength));
                    std::cout << "Facility Name Length: " << facilityNameLength << std::endl;
                    std::cout << "Facility Name: " << facilityName << std::endl;
                    // Check for availability

                    break;
    
                default:
                    break;
                }
            }
        }


};


int main() {
    std::cout << "Starting server..." << std::endl;
    Connection conn;
    conn.listen();

}