#ifndef MESSAGES_CPP
#define MESSAGES_CPP
#include <iostream>
#include <cstring>
#include <arpa/inet.h>
#include <unistd.h>
#include <string>
#endif



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

        std::tuple<size_t, char*> createReply(std::vector<unsigned char> data, uint8_t errorCode = 0) {
            size_t total_length = sizeof(this->msg.requestType) + sizeof(this->msg.requestID) + sizeof(this->msg.choice) + sizeof(errorCode) + (this->msg.length) + data.size();
            char* replyBytes = new char[total_length];
            size_t offset = 0;
            unsigned char requestType = static_cast<unsigned char>(0b0);
            memcpy(replyBytes, &requestType, sizeof(this->msg.requestType));
            offset += sizeof(this->msg.requestType);
            uint32_t requestId = htonl(this->msg.requestID);
            memcpy(replyBytes + offset, &requestId, sizeof(requestId));
            offset += sizeof(requestId);
            memcpy(replyBytes + offset, &this->msg.choice, sizeof(this->msg.choice));
            offset += sizeof(this->msg.choice);
            memcpy(replyBytes + offset, &errorCode, sizeof(errorCode));
            offset += sizeof(errorCode);
            uint32_t dataLength = htonl(data.size());
            memcpy(replyBytes + offset, &dataLength, sizeof(dataLength));
            offset += sizeof(dataLength);
            memcpy(replyBytes + offset, data.data(), data.size());
            return {total_length, replyBytes};
        }
        ~Message() {
            // Destructor
        }
};
