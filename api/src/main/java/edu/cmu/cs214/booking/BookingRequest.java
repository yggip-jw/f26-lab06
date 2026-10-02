package edu.cmu.cs214.booking;

/**
 * Immutable booking details, validated by {@link BookingApi#createBooking(BookingRequest)}.
 *
 * @param roomId the room to book, non-null
 * @param startMinute first minute, inclusive, counted from midnight
 * @param endMinute first minute after the booking, exclusive; greater than startMinute
 * @param waitlistKey caller's key, or null to decline waitlisting
 * @param notes caller's notes, or null if none
 */
public record BookingRequest(String roomId, long startMinute, long endMinute,
                             String waitlistKey, String notes) {
}
