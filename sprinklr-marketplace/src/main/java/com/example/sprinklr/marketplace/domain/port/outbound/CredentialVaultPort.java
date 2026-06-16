package com.example.sprinklr.marketplace.domain.port.outbound;

import java.util.Map;

public interface CredentialVaultPort {

    String encrypt(Map<String, String> credentials);

    Map<String, String> decrypt(String encryptedPayload);
}
