package entities;

import java.io.Serializable;
import java.time.LocalDate;

public class WaitingEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private int numberOfGuests;
    private String phone;
    private String email;
    private LocalDate date;

    public WaitingEntry(int numberOfGuests, String phone, String email, LocalDate date) {
        this.numberOfGuests = numberOfGuests;
        this.phone = phone;
        this.email = email;
        this.date = date;
    }

    public int getNumberOfGuests() {
        return numberOfGuests;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setNumberOfGuests(int numberOfGuests) {
        this.numberOfGuests = numberOfGuests;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }
}

