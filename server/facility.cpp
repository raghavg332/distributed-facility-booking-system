#ifndef FACILITY_CPP
#define FACILITY_CPP
#endif
#include <iostream>
#include <cstring>
#include "bookings.cpp"
#include <vector>
#include <string>
#include <pqxx/pqxx>

class facility {
    public:
        uint facilityId;
        std::string facilityName;
        std::vector<Booking> bookings;
        facility(uint facilityId, std::string facilityName) {
            pqxx::connection conn("dbanme=facilitydb, user=parmatmasingh, password=aishi2705, host=localhost, port=5432");
            if (conn.is_open()) {
                std::cout << "Connected to database" << std::endl;
            } else {
                std::cerr << "Failed to connect to database" << std::endl;
                return;
            }
            pqxx::work txn(conn);
            std::string query = "SELECT * FROM facility WHERE facility_name = '" + facilityName + "';";
            pqxx::result res = txn.exec(query);
            if (res.size() == 0) {
                std::cerr << "New facility" << std::endl;
                
            }
            this->facilityId = facilityId;
            this->facilityName = facilityName;
        }

        int addBooking(uint bookingStartDay, uint bookingStartHour, uint bookingStartMinute, uint bookingEndDay, uint bookingEndHour, uint bookingEndMinute, std::string userName) {
            Booking booking(this->facilityId, bookingStartDay, bookingStartHour, bookingStartMinute, bookingEndDay, bookingEndHour, bookingEndMinute, userName);
            for (Booking prevbooking : bookings) {
                if (prevbooking.bookingStatus == booked && prevbooking.is_conflicting(booking)) {
                    std::cout << "Booking conflict detected!" << std::endl;
                    booking.bookingStatus = failed;
                    booking.saveToDatabase(); // Save failed booking to database
                    // Return -1 to indicate failure
                    bookings.push_back(booking);
                    return -1;
                }
            }
            booking.bookingStatus = booked;
            booking.saveToDatabase(); // Save successful booking to database
            bookings.push_back(booking);
            return 0; // Return 0 to indicate success
        } 
};