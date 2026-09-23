package com.koustubh.bank.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class PinChangeForm {

    @NotBlank
    private String currentPin;

    @NotBlank
    @Pattern(regexp = "\\d{4}", message = "must be exactly 4 digits")
    private String newPin;

    @NotBlank
    private String confirmPin;

    @AssertTrue(message = "PINs do not match")
    public boolean isConfirmed() {
        return newPin != null && newPin.equals(confirmPin);
    }

    public String getCurrentPin() { return currentPin; }
    public void setCurrentPin(String currentPin) { this.currentPin = currentPin; }
    public String getNewPin() { return newPin; }
    public void setNewPin(String newPin) { this.newPin = newPin; }
    public String getConfirmPin() { return confirmPin; }
    public void setConfirmPin(String confirmPin) { this.confirmPin = confirmPin; }
}
