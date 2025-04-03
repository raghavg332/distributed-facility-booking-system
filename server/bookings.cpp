#ifndef BOOKINGS_CPP
#define BOOKINGS_CPP
#include <iostream>
#include <cstring>
#include "pqxx/pqxx"
#include <vector>
#include <string>
#include <exception>
#endif

enum BookingStatus {
    pending = 0,
    booked = 1,
    cancelled = 2,
    failed = 3
};

enum days {
    Sunday = 6,
    Monday = 0,
    Tuesday = 1,
    Wednesday = 2,
    Thursday = 3,
    Friday = 4,
    Saturday = 5
};


class Booking {
    public:
        std::string bookingID;
        std::string facilityId;
        uint bookingStartDay;
        uint bookingStartHour;
        uint bookingStartMinute;
        uint bookingEndDay;
        uint bookingEndHour;
        uint bookingEndMinute;
        uint bookingStatus;
        std::string userName;

        Booking(std::string facilityId, uint bookingStartDay, uint bookingStartHour, uint bookingStartMinute, uint bookingEndDay, uint bookingEndHour, uint bookingEndMinute, std::string userName, std::string bookingID = "", uint bookingStatus = pending) {
            this->facilityId = facilityId;
            this->bookingStartDay = bookingStartDay;
            this->bookingStartHour = bookingStartHour;
            this->bookingStartMinute = bookingStartMinute;
            this->bookingEndDay = bookingEndDay;
            this->bookingEndHour = bookingEndHour;
            this->bookingEndMinute = bookingEndMinute;
            this->bookingStatus = bookingStatus;
            this->userName = userName;
            this->bookingID = bookingID;
        }

        Booking(uint bookingId) {
            // Load booking from database
            try {
                pqxx::connection conn("dbname=facilitydb user=parmatmasingh password=aishi2705 host=localhost port=5432");
                if (conn.is_open()) {
                    std::cout << "Connected to database" << std::endl;
                } else {
                    std::cerr << "Failed to connect to database" << std::endl;
                    return;
                }
                pqxx::work txn(conn);
                std::string query = "SELECT * FROM booking WHERE booking_id = '" + std::to_string(bookingId) + "';";
                std::cout << "Query: " << query << std::endl;
                pqxx::result res = txn.exec(query);
                if (res.size() > 0) {
                    this->bookingID = res[0][0].as<std::string>();
                    this->facilityId = res[0][1].as<std::string>();
                    this->userName = res[0][2].as<std::string>();
                    this->bookingStartDay = static_cast<uint>(res[0][3].as<int>());
                    this->bookingStartHour = static_cast<uint>(res[0][4].as<int>());
                    this->bookingStartMinute = static_cast<uint>(res[0][5].as<int>());
                    this->bookingEndDay = static_cast<uint>(res[0][6].as<int>());
                    this->bookingEndHour = static_cast<uint>(res[0][7].as<int>());
                    this->bookingEndMinute = static_cast<uint>(res[0][8].as<int>());
                    this->bookingStatus = static_cast<uint>(res[0][9].as<int>());
                } else {
                    std::cerr << "Booking not found" << std::endl;
                }
                conn.close();
            }
            catch (const std::exception &e) {
                std::cerr << "Error loading booking: " << e.what() << std::endl;
            }
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
                pqxx::connection conn("dbname=facilitydb user=parmatmasingh password=aishi2705 host=localhost port=5432");
                if (conn.is_open()) {
                    std::cout << "Connected to database" << std::endl;
                } else {
                    std::cerr << "Failed to connect to database" << std::endl;
                    return;
                }
                pqxx::work txn(conn);
                pqxx::result res;
                if (this->bookingID != "") {
                    res = txn.exec(
                        "UPDATE booking SET facility_id = $1, username = $2, start_day = $3, start_hour = $4, start_minute = $5, end_day = $6, end_hour = $7, end_minute = $8, booking_status = $9 WHERE booking_id = $10 RETURNING booking_id",
                        pqxx::params(
                            this->facilityId,
                            this->userName,
                            this->bookingStartDay,
                            this->bookingStartHour,
                            this->bookingStartMinute,
                            this->bookingEndDay,
                            this->bookingEndHour,
                            this->bookingEndMinute,
                            this->bookingStatus,
                            this->bookingID
                        )
                    );
                } else {

                    res = txn.exec(
                        "INSERT INTO booking (facility_id, username, start_day, start_hour, start_minute, end_day, end_hour, end_minute, booking_status) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9) RETURNING booking_id",
                        pqxx::params(
                            this->facilityId,
                            this->userName,
                            this->bookingStartDay,
                            this->bookingStartHour,
                            this->bookingStartMinute,
                            this->bookingEndDay,
                            this->bookingEndHour,
                            this->bookingEndMinute,
                            this->bookingStatus
                        )
                    );
                }
                txn.commit();
                if (res.size() > 0) {
                    this->bookingID = res[0][0].as<std::string>();
                    std::cout << "Booking saved with ID: " << this->bookingID << std::endl;
                } else {
                    std::cerr << "Failed to save booking" << std::endl;
                }
                conn.close();
            }
            catch (const std::exception &e) {
                std::cerr << "Error saving booking: " << e.what() << std::endl;
            }
        }

        int changeBookingMinutes(int change) {
            // Change booking start time by a certain number of minutes, changes should not be accross days
            uint currentBookingStart = this->bookingStartHour * 60 + this->bookingStartMinute;
            uint currentBookingEnd = this->bookingEndHour * 60 + this->bookingEndMinute;
            int newBookingStart = (int)currentBookingStart + change;
            int newBookingEnd = (int)currentBookingEnd + change;

            if (newBookingStart > 0 && newBookingStart < 1440 && newBookingEnd > 0 && newBookingEnd < 1440) {
                this->bookingStartHour = (uint)newBookingStart / 60;
                this->bookingStartMinute = (uint)newBookingStart % 60;
                this->bookingEndHour = (uint)newBookingEnd / 60;
                this->bookingEndMinute =(uint)newBookingEnd % 60;
                this->saveToDatabase();
                return 0;
            } else {
                std::cerr << "Invalid booking time" << std::endl;
                return 1;
            }
        }
};