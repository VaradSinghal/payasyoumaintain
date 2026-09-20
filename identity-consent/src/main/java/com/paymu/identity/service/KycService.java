package com.paymu.identity.service;

import com.paymu.identity.model.OwnerDetails;

public interface KycService {
    String verify(OwnerDetails ownerDetails);
}
