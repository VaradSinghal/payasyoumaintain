package com.paymu.identity.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OwnerDetails(
        @NotBlank(message = "Owner name is required")
        String fullName,
        
        @NotBlank(message = "Aadhaar number or PAN is required for KYC")
        String kycDocumentNumber,

        @Pattern(regexp = "^(AADHAAR|PAN)$", message = "KYC document type must be AADHAAR or PAN")
        String kycDocumentType,
        
        @NotBlank(message = "Email is required")
        String email,
        
        @NotBlank(message = "Phone number is required")
        String phoneNumber
) {
    public OwnerDetails mask() {
        return new OwnerDetails(
                fullName,
                maskKycDocument(kycDocumentNumber),
                kycDocumentType,
                maskEmail(email),
                maskPhoneNumber(phoneNumber)
        );
    }

    private static String maskKycDocument(String kyc) {
        if (kyc == null) return null;
        if (kyc.length() <= 4) return kyc;
        return "X".repeat(kyc.length() - 4) + kyc.substring(kyc.length() - 4);
    }

    private static String maskPhoneNumber(String phone) {
        if (phone == null) return null;
        if (phone.length() <= 4) return phone;
        return "X".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }

    private static String maskEmail(String emailStr) {
        if (emailStr == null) return null;
        int atIndex = emailStr.indexOf('@');
        if (atIndex <= 1) {
            return emailStr;
        }
        return emailStr.substring(0, 1) + "***" + emailStr.substring(atIndex);
    }
}
