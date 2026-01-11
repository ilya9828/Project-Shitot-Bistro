package entities;

import java.io.Serializable;

/**
 * Payment entity for processing payments (simulated - academic purposes only).
 * Contains payment details including card information.
 */
public class Payment implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String confirmationCode;
    private String paymentMethod; // "Credit Card", "Cash", "Digital Wallet"
    private double amount;
    private boolean isMember;
    private double discount;
    
    // Credit Card details (only used when paymentMethod is "Credit Card")
    private String cardNumber;
    private String cardHolderName;
    private String expiryDate; // MM/YY format
    private String cvv;
    
    /**
     * Constructor for non-card payments (Cash, Digital Wallet)
     */
    public Payment(String confirmationCode, String paymentMethod, double amount, boolean isMember, double discount) {
        this.confirmationCode = confirmationCode;
        this.paymentMethod = paymentMethod;
        this.amount = amount;
        this.isMember = isMember;
        this.discount = discount;
    }
    
    /**
     * Constructor for Credit Card payments
     */
    public Payment(String confirmationCode, String paymentMethod, double amount, boolean isMember, double discount,
                   String cardNumber, String cardHolderName, String expiryDate, String cvv) {
        this(confirmationCode, paymentMethod, amount, isMember, discount);
        this.cardNumber = cardNumber;
        this.cardHolderName = cardHolderName;
        this.expiryDate = expiryDate;
        this.cvv = cvv;
    }
    
    // Getters
    public String getConfirmationCode() {
        return confirmationCode;
    }
    
    public String getPaymentMethod() {
        return paymentMethod;
    }
    
    public double getAmount() {
        return amount;
    }
    
    public boolean isMember() {
        return isMember;
    }
    
    public double getDiscount() {
        return discount;
    }
    
    public String getCardNumber() {
        return cardNumber;
    }
    
    public String getCardHolderName() {
        return cardHolderName;
    }
    
    public String getExpiryDate() {
        return expiryDate;
    }
    
    public String getCvv() {
        return cvv;
    }
    
    // Setters
    public void setConfirmationCode(String confirmationCode) {
        this.confirmationCode = confirmationCode;
    }
    
    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
    
    public void setAmount(double amount) {
        this.amount = amount;
    }
    
    public void setMember(boolean isMember) {
        this.isMember = isMember;
    }
    
    public void setDiscount(double discount) {
        this.discount = discount;
    }
    
    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }
    
    public void setCardHolderName(String cardHolderName) {
        this.cardHolderName = cardHolderName;
    }
    
    public void setExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
    }
    
    public void setCvv(String cvv) {
        this.cvv = cvv;
    }
}

