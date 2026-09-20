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
) {}
