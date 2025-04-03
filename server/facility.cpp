#ifndef FACILITY_CPP
#define FACILITY_CPP
#include <iostream>
#include <cstring>
#include "bookings.cpp"
#include <vector>
#include <string>
#include <pqxx/pqxx>
#include <map>
#endif


class facility {
    public:
        std::string facilityId;
        std::string facilityName;
        std::vector<Booking> bookings;
        facility(std::string facilityName) {
            pqxx::connection conn("dbname=facilitydb user=parmatmasingh password=aishi2705 host=localhost port=5432");
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
                this->facilityName = facilityName;
                std::string insertQuery = "INSERT INTO facility (facility_name) VALUES ('" + facilityName + "') RETURNING facility_id;";
                pqxx::result res = txn.exec(insertQuery);
                txn.commit();
                if (res.size() > 0) {
                    this->facilityId = res[0][0].as<std::string>();
                    std::cout << "Facility ID: " << this->facilityId << std::endl;
                } else {
                    std::cerr << "Failed to insert facility" << std::endl;
                }
            } else {
                std::cout << "Facility already exists" << std::endl;
                this->facilityId = res[0][0].as<std::string>();
                this->facilityName = res[0][1].as<std::string>();
                std::cout << "Facility ID: " << this->facilityId << std::endl;
                std::cout << "Facility Name: " << this->facilityName << std::endl;
            }
            std::string bookingQuery = "SELECT * FROM booking WHERE facility_id = '" + this->facilityId + "';";
            pqxx::result bookingRes = txn.exec(bookingQuery);
            for (pqxx::result::const_iterator row = bookingRes.begin(); row != bookingRes.end(); ++row) {
                std::string bookingId = row[0].as<std::string>();
                std::string facilityId = row[1].as<std::string>();
                std::string userName = row[2].as<std::string>();
                uint bookingStartDay = static_cast<uint>(row[3].as<int>());
                uint bookingStartHour = static_cast<uint>(row[4].as<int>());
                uint bookingStartMinute = static_cast<uint>(row[5].as<int>());
                uint bookingEndDay = static_cast<uint>(row[6].as<int>());
                uint bookingEndHour = static_cast<uint>(row[7].as<int>());
                uint bookingEndMinute = static_cast<uint>(row[8].as<int>());
                uint bookingStatus = static_cast<uint>(row[9].as<int>());
                Booking booking(facilityId, bookingStartDay, bookingStartHour, bookingStartMinute, bookingEndDay, bookingEndHour, bookingEndMinute, userName, bookingId, bookingStatus);
                bookings.push_back(booking);
            }
            conn.close();
        }

        std::tuple<int, std::string> addBooking(uint bookingStartDay, uint bookingStartHour, uint bookingStartMinute, uint bookingEndDay, uint bookingEndHour, uint bookingEndMinute, std::string userName) {
            Booking booking(this->facilityId, bookingStartDay, bookingStartHour, bookingStartMinute, bookingEndDay, bookingEndHour, bookingEndMinute, userName);
            for (Booking prevbooking : bookings) {
                if (prevbooking.bookingStatus == booked && prevbooking.is_conflicting(booking)) {
                    std::cout << "Booking conflict detected!" << std::endl;
                    booking.bookingStatus = failed;
                    booking.saveToDatabase(); // Save failed booking to database
                    // Return 1 to indicate failure
                    bookings.push_back(booking);
                    return {1, "Booking conflict detected!"};
                }
            }
            booking.bookingStatus = booked;
            booking.saveToDatabase(); // Save successful booking to database
            bookings.push_back(booking);
            return {0, booking.bookingID}; // Return 0 to indicate success
        } 

        std::map<uint, uint> getBookingTimes(uint queryDay) {
            std::map<uint, uint> bookedSlots;
            for (Booking booking : bookings) {
                std::cout << "Booking ID: " << booking.bookingID << std::endl;
                std::cout << "Facility ID: " << booking.facilityId << std::endl;
                std::cout << "Booking Start Day: " << booking.bookingStartDay << std::endl;
                std::cout << "Booking Start Hour: " << booking.bookingStartHour << std::endl;
                std::cout << "Booking Start Minute: " << booking.bookingStartMinute << std::endl;
                std::cout << "Booking End Day: " << booking.bookingEndDay << std::endl;
                if (booking.bookingStatus == booked && booking.bookingStartDay == queryDay) {
                    if (booking.bookingEndDay != booking.bookingStartDay) {
                        bookedSlots[booking.bookingStartHour * 100 + booking.bookingStartMinute] = 2359;
                    } else {
                        bookedSlots[booking.bookingStartHour * 100 + booking.bookingStartMinute] = booking.bookingEndHour * 100 + booking.bookingEndMinute;
                    }
                }
            }
            return bookedSlots;
        }
};