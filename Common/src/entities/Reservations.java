package entities;

import java.io.Serializable;
import java.time.LocalDateTime;

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

    public Integer getSubscriberId() {
        return subscriberId;
    }

    public int getNumberOfGuests() {
        return numberOfGuests;
    }

    public String getConfirmationCode() {
        return confirmationCode;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public LocalDateTime getOrderDateTime() {
        return orderDateTime;
    }

    public LocalDateTime getPlacingOrderDate() {
        return placingOrderDate;
    }

    public String getStatus() {
        return status;
    }
    
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    public String getEmail() {
        return email;
    }

    public boolean isSubscriber() {
        return isSubscriber;
    }
    public String getName() {
        return name;
    }
    
 // ===== Setters  =====

    public void setNumberOfGuests(int numberOfGuests) {
        if (numberOfGuests > 0) {
            this.numberOfGuests = numberOfGuests;
        }
    }

    public void setStatus(String status) {
        if (status != null) {
            this.status = status;
        }
    }

    public void setSubscriberId(Integer subscriberId) {
        this.subscriberId = subscriberId;
        this.isSubscriber = (subscriberId != null);
    }

    public void setOrderDateTime(LocalDateTime orderDateTime) {
        if (orderDateTime != null) {
            this.orderDateTime = orderDateTime;
        }
    }
    
    public void setEmail(String Email) {
            this.email = Email;
    }
    
    public void setPhoneNumber(String PhoneNumber) {
        this.phoneNumber = PhoneNumber;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
}
