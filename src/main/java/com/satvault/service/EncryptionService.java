package com.satvault.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptionService(@Value("${satvault.encryption.master-key}") String rawKey) {
        byte[] keyBytes = deriveAes256Key(rawKey);
        this.masterKey = new SecretKeySpec(keyBytes, "AES");
    }

    private byte[] deriveAes256Key(String rawKey) {
        try {
            byte[] decoded = Base64.getDecoder().decode(rawKey);
            if (decoded.length == 32) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // Fall through to SHA-256 derivation for non-base64 keys
        }
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return sha256.digest(rawKey.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize AES-256 encryption key", e);
        }
    }

    /**
     * Encrypts plaintext using AES-256-GCM with a fresh random 96-bit IV.
     * Output format: [12-byte IV][ciphertext + 16-byte GCM auth tag]
     */
    public byte[] encrypt(byte[] plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);

            byte[] output = new byte[IV_LENGTH_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, output, 0, IV_LENGTH_BYTES);
            System.arraycopy(ciphertext, 0, output, IV_LENGTH_BYTES, ciphertext.length);
            return output;
        } catch (Exception e) {
            throw new IllegalStateException("File encryption failed", e);
        }
    }

    /**
     * Decrypts [12-byte IV][ciphertext + auth tag] using AES-256-GCM.
     */
    public byte[] decrypt(byte[] ivAndCiphertext) {
        if (ivAndCiphertext == null || ivAndCiphertext.length <= IV_LENGTH_BYTES) {
            throw new IllegalArgumentException("Invalid encrypted payload");
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(ivAndCiphertext, 0, iv, 0, IV_LENGTH_BYTES);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return cipher.doFinal(ivAndCiphertext, IV_LENGTH_BYTES, ivAndCiphertext.length - IV_LENGTH_BYTES);
        } catch (Exception e) {
            throw new IllegalStateException("File decryption failed", e);
        }
    }
}
