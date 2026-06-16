package com.example.sprinklr.marketplace.infrastructure.security;

import com.example.sprinklr.marketplace.domain.port.outbound.CredentialVaultPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

@Component
public class AesCredentialVault implements CredentialVaultPort {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesCredentialVault(@Value("${app.mcp.encryption-key:}") String encryptionKeyBase64) {
        if (encryptionKeyBase64 == null || encryptionKeyBase64.isBlank()) {
            byte[] generated = new byte[32];
            new SecureRandom().nextBytes(generated);
            this.secretKey = new SecretKeySpec(generated, "AES");
        } else {
            byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
            if (keyBytes.length != 32) {
                throw new IllegalArgumentException("app.mcp.encryption-key must be 32 bytes (base64-encoded)");
            }
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
        }
    }

    @Override
    public String encrypt(Map<String, String> credentials) {
        try {
            byte[] plaintext = OBJECT_MAPPER.writeValueAsBytes(credentials);
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to encrypt credentials", exception);
        }
    }

    @Override
    public Map<String, String> decrypt(String encryptedPayload) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedPayload);
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);

            return OBJECT_MAPPER.readValue(plaintext, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize decrypted credentials", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to decrypt credentials", exception);
        }
    }
}
