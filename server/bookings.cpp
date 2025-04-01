#ifndef BOOKINGS_CPP
#define BOOKINGS_CPP
#define SUNDAY 6
#define MONDAY 0
#define TUESDAY 1
#define WEDNESDAY 2
#define THURSDAY 3
#define FRIDAY 4
#define SATURDAY 5
#define pending 0
#define booked 1
#define cancelled 2
#define failed 3
#endif
#include <iostream>
#include <cstring>
#include "pqxx/pqxx"
#include <vector>
#include <string>
#include <exception>



class Booking {
    public:
        std::string bookingID;
        uint facilityId;
        uint bookingStartDay;
        uint bookingStartHour;
        uint bookingStartMinute;
        uint bookingEndDay;
        uint bookingEndHour;
        uint bookingEndMinute;
        uint bookingStatus;
        std::string userName;

        Booking(uint facilityId, uint bookingStartDay, uint bookingStartHour, uint bookingStartMinute, uint bookingEndDay, uint bookingEndHour, uint bookingEndMinute, std::string userName) {
            this->facilityId = facilityId;
            this->bookingStartDay = bookingStartDay;
            this->bookingStartHour = bookingStartHour;
            this->bookingStartMinute = bookingStartMinute;
            this->bookingEndDay = bookingEndDay;
            this->bookingEndHour = bookingEndHour;
            this->bookingEndMinute = bookingEndMinute;
            this->bookingStatus = pending;
            this->userName = userName;
            this->bookingID = "";
        }

        bool is_conflicting(Booking otherBooking) {
            if (this->facilityId != otherBooking.facilityId) {
                return false;
            }
            if (this->bookingStartDay == otherBooking.bookingStartDay) {
                if (this->bookingStartHour < otherBooking.bookingEndHour && this->bookingEndHour > otherBooking.bookingStartHour) {
                    return true;
                }
            }
            return false;
        }

        void saveToDatabase() {
            // Save booking to database
            try {
                pqxx::connection conn("dbanme=facilitydb, user=parmatmasingh, password=aishi2705, host=localhost, port=5432");
                if (conn.is_open()) {
                    std::cout << "Connected to database" << std::endl;
                } else {
                    std::cerr << "Failed to connect to database" << std::endl;
                    return;
                }
                pqxx::work txn(conn);

                pqxx::result res = txn.exec_params("INSERT INTO booking (facility_id, username, start_day, start_hour, start_minute, end_day, end_hour, end_minute) VALUES ($1, $2, $3, $4, $5, $6, $7, $8) RETURNING booking_id",
                    this->facilityId,
                    this->userName,
                    this->bookingStartDay,
                    this->bookingStartHour,
                    this->bookingStartMinute,
                    this->bookingEndDay,
                    this->bookingEndHour,
                    this->bookingEndMinute
                );
                if (res.size() > 0) {
                    this->bookingID = res[0][0].as<std::string>();
                    std::cout << "Booking saved with ID: " << this->bookingID << std::endl;
                } else {
                    std::cerr << "Failed to save booking" << std::endl;
                }
            }
            catch (const std::exception &e) {
                std::cerr << "Error saving booking: " << e.what() << std::endl;
            }
        }


};