#ifndef MESSAGES 
#define MESSAGES
#endif

#include <iostream>
#include <cstring>
#include <arpa/inet.h>
#include <unistd.h>
#include <string>

struct message {
    unsigned char requestType;
    uint32_t requestID;
    unsigned char choice;
    size_t length;
    std::vector<unsigned char> messageData;
};

class Message {
    public :
        message msg;
        Message() {
            memset(&msg, 0, sizeof(msg));
        }
        Message(unsigned char* messageBytes, size_t length) {
            msg.requestType = messageBytes[0];
            memcpy(&msg.requestID, messageBytes + 1, sizeof(msg.requestID));
            msg.requestID = ntohl(msg.requestID);
            msg.choice = messageBytes[5];
            msg.length = length - 6;
            msg.messageData.assign(messageBytes + 6, messageBytes + length);
        }
        ~Message() {
            // Destructor
        }
};