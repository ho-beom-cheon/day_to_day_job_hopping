package dev.dailycareer.integration.notion;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
final class NotionSecretCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final NotionSettings settings;

    NotionSecretCipher(NotionSettings settings) { this.settings = settings; }

    String encrypt(long user, String reference, String token) {
        settings.required();
        byte[] nonce = new byte[12];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(settings.key(), "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad(user, reference));
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Cannot encrypt Notion credential", failure);
        }
    }

    String decrypt(long user, String reference, String value) {
        settings.required();
        try {
            byte[] packed = Base64.getUrlDecoder().decode(value);
            if (packed.length < 29) throw new IllegalArgumentException("Invalid credential ciphertext");
            byte[] nonce = java.util.Arrays.copyOfRange(packed, 0, 12);
            byte[] encrypted = java.util.Arrays.copyOfRange(packed, 12, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(settings.key(), "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad(user, reference));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException failure) {
            throw new IllegalStateException("Cannot decrypt Notion credential", failure);
        }
    }

    private byte[] aad(long user, String reference) {
        return (user + ":" + reference + ":" + settings.keyId()).getBytes(StandardCharsets.UTF_8);
    }
}
