package com.paymu.identity.service;

import com.paymu.identity.model.OwnerDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MockKycService implements KycService {
    private static final Logger log = LoggerFactory.getLogger(MockKycService.class);

    @Override
    public String verify(OwnerDetails ownerDetails) {
        log.info("Mock KYC check for {} document {}", 
                 ownerDetails.kycDocumentType(), 
                 ownerDetails.kycDocumentNumber());
        return "VERIFIED";
    }
}
