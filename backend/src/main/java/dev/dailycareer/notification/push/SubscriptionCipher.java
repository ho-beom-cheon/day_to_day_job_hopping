package dev.dailycareer.notification.push;

import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public final class SubscriptionCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final WebPushSettings settings;

    public SubscriptionCipher(WebPushSettings settings) { this.settings = settings; }

    public Encrypted encrypt(String value, String purpose) {
        requireEnabled();
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(settings.subscriptionKeyBytes(), "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad(purpose));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] stored = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, stored, 0, iv.length);
            System.arraycopy(encrypted, 0, stored, iv.length, encrypted.length);
            return new Encrypted(stored, settings.keyId());
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Unable to encrypt push subscription", failure);
        }
    }

    public String decrypt(byte[] stored, String purpose, String keyId) {
        requireEnabled();
        if (!settings.keyId().equals(keyId) || stored == null || stored.length < 29) {
            throw new IllegalStateException("Unsupported push subscription key");
        }
        try {
            byte[] iv = java.util.Arrays.copyOfRange(stored, 0, 12);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(settings.subscriptionKeyBytes(), "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad(purpose));
            return new String(cipher.doFinal(stored, 12, stored.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Unable to decrypt push subscription", failure);
        }
    }

    private byte[] aad(String purpose) { return (purpose + ":" + settings.keyId()).getBytes(StandardCharsets.UTF_8); }
    private void requireEnabled() { if (!settings.enabled()) throw new ApiException(ApiErrorCode.PUSH_DISABLED); }
    public record Encrypted(byte[] bytes, String keyId) { public Encrypted { bytes = bytes.clone(); } @Override public byte[] bytes(){return bytes.clone();} }
}
