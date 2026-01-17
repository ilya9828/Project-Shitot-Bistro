package entities;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Entity class representing an entry in the waiting list.
 * Contains customer contact information and reservation details for walk-in customers.
 * 
 * @author Dream Team
 * @version 300.1.6
 */
public class WaitingEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private int numberOfGuests;
    private String phone;
    private String email;
    private LocalDate date;

    /**
     * Constructor for creating a new WaitingEntry object.
     * 
     * @param numberOfGuests The number of guests in the party
     * @param phone The contact phone number
     * @param email The contact email address
     * @param date The date for the waiting list entry
     */
    public WaitingEntry(int numberOfGuests, String phone, String email, LocalDate date) {
        this.numberOfGuests = numberOfGuests;
        this.phone = phone;
        this.email = email;
        this.date = date;
    }

    /**
     * Gets the number of guests for this waiting list entry.
     * 
     * @return The number of guests
     */
    public int getNumberOfGuests() {
        return numberOfGuests;
    }

    /**
     * Gets the phone number for this waiting list entry.
     * 
     * @return The phone number
     */
    public String getPhone() {
        return phone;
    }

    /**
     * Gets the email address for this waiting list entry.
     * 
     * @return The email address
     */
    public String getEmail() {
        return email;
    }

    /**
     * Gets the date for this waiting list entry.
     * 
     * @return The date
     */
    public LocalDate getDate() {
        return date;
    }

    /**
     * Sets the number of guests for this waiting list entry.
     * 
     * @param numberOfGuests The number of guests
     */
    public void setNumberOfGuests(int numberOfGuests) {
        this.numberOfGuests = numberOfGuests;
    }

    /**
     * Sets the phone number for this waiting list entry.
     * 
     * @param phone The phone number
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /**
     * Sets the email address for this waiting list entry.
     * 
     * @param email The email address
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Sets the date for this waiting list entry.
     * 
     * @param date The date
     */
    public void setDate(LocalDate date) {
        this.date = date;
    }
}

