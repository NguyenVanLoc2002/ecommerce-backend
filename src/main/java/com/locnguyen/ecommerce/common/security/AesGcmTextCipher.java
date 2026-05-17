package com.locnguyen.ecommerce.common.security;

import com.locnguyen.ecommerce.common.config.CarrierProperties;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class AesGcmTextCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private final CarrierProperties carrierProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Failed to encrypt carrier configuration secret");
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Failed to decrypt carrier configuration secret");
        }
    }

    private SecretKeySpec secretKey() {
        String encoded = carrierProperties.getConfigEncryptionKey();
        if (encoded == null || encoded.isBlank()) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Carrier config encryption key is not configured");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Carrier config encryption key must be Base64 encoded");
        }
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Carrier config encryption key must decode to 16, 24, or 32 bytes");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}
