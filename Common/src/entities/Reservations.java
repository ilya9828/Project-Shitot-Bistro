package entities;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Entity class representing a restaurant reservation.
 * Contains all reservation details including subscriber information,
 * guest count, confirmation codes, dates, and contact information.
 * 
 * @author Dream Team
 * @version 300.1.6
 */
public class Reservations implements Serializable {

    private static final long serialVersionUID = 1L;

    // ===== Columns from DB =====

    private Integer subscriberId;          // subscriber_id
    private int numberOfGuests;             // number_of_guests
    private String confirmationCode;        // confirmation_code
    private String orderNumber;              // order_number

    private LocalDateTime orderDateTime;     // order_time&date
    private LocalDateTime placingOrderDate;  // time&date_of_placing_order
    private String status;
    private String email;
    private String phoneNumber;                   // status
    private boolean isSubscriber;            // is_subscriber
    private String name;
    
    /**
     * Constructor for creating a new Reservations object.
     * 
     * @param subscriberId The subscriber ID (null if guest)
     * @param numberOfGuests The number of guests for the reservation
     * @param confirmationCode The unique confirmation code for the reservation
     * @param orderNumber The order number associated with the reservation
     * @param orderDateTime The date and time of the reservation
     * @param placingOrderDate The date and time when the reservation was placed
     * @param status The current status of the reservation
     * @param isSubscriber Whether the reservation is for a subscriber (true) or guest (false)
     * @param email Contact email address
     * @param phoneNumber Contact phone number
     * @param name Customer name
     */
    // ===== Constructor =====
    public Reservations(Integer subscriberId,
                       int numberOfGuests,
                       String confirmationCode,
                       String orderNumber,
                       LocalDateTime orderDateTime,
                       LocalDateTime placingOrderDate,
                       String status,
                       boolean isSubscriber,
                       String email,
                       String phoneNumber,
                       String name) {

        this.subscriberId = subscriberId;
        this.numberOfGuests = numberOfGuests;
        this.confirmationCode = confirmationCode;
        this.orderNumber = orderNumber;
        this.orderDateTime = orderDateTime;
        this.placingOrderDate = placingOrderDate;
        this.status = status;
        this.isSubscriber = isSubscriber;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.name = name;
        
    }

    // ===== Getters =====

    /**
     * Gets the subscriber ID.
     * 
     * @return The subscriber ID, or null if this is a guest reservation
     */
    public Integer getSubscriberId() {
        return subscriberId;
    }

    /**
     * Gets the number of guests for this reservation.
     * 
     * @return The number of guests
     */
    public int getNumberOfGuests() {
        return numberOfGuests;
    }

    /**
     * Gets the confirmation code for this reservation.
     * 
     * @return The confirmation code
     */
    public String getConfirmationCode() {
        return confirmationCode;
    }

    /**
     * Gets the order number for this reservation.
     * 
     * @return The order number
     */
    public String getOrderNumber() {
        return orderNumber;
    }

    /**
     * Gets the date and time of the reservation.
     * 
     * @return The order date and time
     */
    public LocalDateTime getOrderDateTime() {
        return orderDateTime;
    }

    /**
     * Gets the date and time when the reservation was placed.
     * 
     * @return The placing order date and time
     */
    public LocalDateTime getPlacingOrderDate() {
        return placingOrderDate;
    }

    /**
     * Gets the current status of the reservation.
     * 
     * @return The reservation status
     */
    public String getStatus() {
        return status;
    }
    
    /**
     * Gets the phone number associated with this reservation.
     * 
     * @return The phone number
     */
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    /**
     * Gets the email address associated with this reservation.
     * 
     * @return The email address
     */
    public String getEmail() {
        return email;
    }

    /**
     * Checks if this reservation is for a subscriber.
     * 
     * @return true if subscriber, false if guest
     */
    public boolean isSubscriber() {
        return isSubscriber;
    }
    
    /**
     * Gets the customer name for this reservation.
     * 
     * @return The customer name
     */
    public String getName() {
        return name;
    }
    
 // ===== Setters  =====

    /**
     * Sets the number of guests for this reservation.
     * Only updates if the value is greater than 0.
     * 
     * @param numberOfGuests The number of guests (must be > 0)
     */
    public void setNumberOfGuests(int numberOfGuests) {
        if (numberOfGuests > 0) {
            this.numberOfGuests = numberOfGuests;
        }
    }

    /**
     * Sets the status of this reservation.
     * Only updates if the status is not null.
     * 
     * @param status The reservation status
     */
    public void setStatus(String status) {
        if (status != null) {
            this.status = status;
        }
    }

    /**
     * Sets the subscriber ID for this reservation.
     * Automatically updates the isSubscriber flag based on whether subscriberId is null.
     * 
     * @param subscriberId The subscriber ID, or null for guest reservations
     */
    public void setSubscriberId(Integer subscriberId) {
        this.subscriberId = subscriberId;
        this.isSubscriber = (subscriberId != null);
    }

    /**
     * Sets the date and time for this reservation.
     * Only updates if the dateTime is not null.
     * 
     * @param orderDateTime The order date and time
     */
    public void setOrderDateTime(LocalDateTime orderDateTime) {
        if (orderDateTime != null) {
            this.orderDateTime = orderDateTime;
        }
    }
    
    /**
     * Sets the email address for this reservation.
     * 
     * @param Email The email address
     */
    public void setEmail(String Email) {
            this.email = Email;
    }
    
    /**
     * Sets the phone number for this reservation.
     * 
     * @param PhoneNumber The phone number
     */
    public void setPhoneNumber(String PhoneNumber) {
        this.phoneNumber = PhoneNumber;
    }
    
    /**
     * Sets the customer name for this reservation.
     * 
     * @param name The customer name
     */
    public void setName(String name) {
        this.name = name;
    }
    
}
