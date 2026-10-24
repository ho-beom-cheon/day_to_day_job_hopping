package dev.dailycareer.integration.browser;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public final class BrowserSecretCipher {
    private final BrowserOAuthSettings settings;
    private static final SecureRandom RANDOM = new SecureRandom();
    public BrowserSecretCipher(BrowserOAuthSettings settings) { this.settings = settings; }
    public String encrypt(String context, String value) {
        byte[] nonce = new byte[12]; RANDOM.nextBytes(nonce);
        try {
            var cipher = cipher(Cipher.ENCRYPT_MODE, context, nonce);
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(12 + encrypted.length).put(nonce).put(encrypted).array());
        } catch (Exception failure) { throw new IllegalStateException("Credential encryption failed"); }
    }
    public String decrypt(String context, String value) {
        try {
            byte[] packed = Base64.getDecoder().decode(value);
            if (packed.length < 29) throw new IllegalArgumentException();
            var cipher = cipher(Cipher.DECRYPT_MODE, context, Arrays.copyOfRange(packed, 0, 12));
            return new String(cipher.doFinal(Arrays.copyOfRange(packed, 12, packed.length)), StandardCharsets.UTF_8);
        } catch (Exception failure) { throw new IllegalStateException("Credential decryption failed"); }
    }
    private Cipher cipher(int mode, String context, byte[] nonce) throws Exception {
        if (!settings.keyReady()) throw new IllegalStateException();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(settings.key(), "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
