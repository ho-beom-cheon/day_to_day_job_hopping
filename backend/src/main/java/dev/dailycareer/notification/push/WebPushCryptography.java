package dev.dailycareer.notification.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public final class WebPushCryptography {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();
    private static final byte[] KEY_INFO_PREFIX = "WebPush: info\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CEK_INFO = "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NONCE_INFO = "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII);
    private final WebPushSettings settings;
    private final ObjectMapper json;

    public WebPushCryptography(WebPushSettings settings, ObjectMapper json) { this.settings = settings; this.json = json; }

    public Prepared prepare(URI endpoint, String clientPublicKey, String authSecret, byte[] payload) {
        if (!settings.enabled()) throw new IllegalStateException("Web Push is disabled");
        if (payload.length + 1 > 3993) throw new IllegalArgumentException("Push payload is too large");
        try {
            byte[] clientRaw = Base64.getUrlDecoder().decode(clientPublicKey);
            byte[] auth = Base64.getUrlDecoder().decode(authSecret);
            ECPublicKey clientKey = publicKey(clientRaw);
            var generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
            var ephemeral = generator.generateKeyPair();
            byte[] serverRaw = raw((ECPublicKey) ephemeral.getPublic());

            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(ephemeral.getPrivate());
            agreement.doPhase(clientKey, true);
            byte[] shared = agreement.generateSecret();
            byte[] keyInfo = concat(KEY_INFO_PREFIX, clientRaw, serverRaw);
            byte[] ikm = expand(extract(auth, shared), keyInfo, 32);
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            byte[] prk = extract(salt, ikm);
            byte[] cek = expand(prk, CEK_INFO, 16);
            byte[] nonce = expand(prk, NONCE_INFO, 12);
            byte[] record = java.util.Arrays.copyOf(payload, payload.length + 1);
            record[record.length - 1] = 2;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(record);

            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(salt);
            body.write(ByteBuffer.allocate(4).putInt(4096).array());
            body.write(serverRaw.length);
            body.write(serverRaw);
            body.write(ciphertext);
            return new Prepared(body.toByteArray(), authorization(endpoint));
        } catch (GeneralSecurityException | java.io.IOException failure) {
            throw new IllegalStateException("Unable to prepare Web Push request", failure);
        }
    }

    private String authorization(URI endpoint) throws GeneralSecurityException, java.io.IOException {
        String header = BASE64.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String audience = endpoint.getScheme().toLowerCase() + "://" + endpoint.getHost().toLowerCase()
                + (endpoint.getPort() == -1 || endpoint.getPort() == 443 ? "" : ":" + endpoint.getPort());
        var claims = new LinkedHashMap<String, Object>();
        claims.put("aud", audience);
        claims.put("exp", Instant.now().plusSeconds(12 * 60 * 60).getEpochSecond());
        claims.put("sub", settings.subject());
        String payload = BASE64.encodeToString(json.writeValueAsBytes(claims));
        String signingInput = header + "." + payload;
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(privateKey(settings.privateKeyBytes()), RANDOM);
        signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        String token = signingInput + "." + BASE64.encodeToString(derToJose(signer.sign()));
        return "vapid t=" + token + ", k=" + settings.publicKey();
    }

    private static ECPublicKey publicKey(byte[] raw) throws GeneralSecurityException {
        if (raw.length != 65 || raw[0] != 4) throw new GeneralSecurityException("Invalid uncompressed P-256 key");
        var point = new ECPoint(new BigInteger(1, java.util.Arrays.copyOfRange(raw, 1, 33)),
                new BigInteger(1, java.util.Arrays.copyOfRange(raw, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, parameters()));
    }

    static boolean validClientPublicKey(byte[] raw) {
        try { publicKey(raw); return true; }
        catch (GeneralSecurityException invalid) { return false; }
    }

    private static java.security.PrivateKey privateKey(byte[] raw) throws GeneralSecurityException {
        return KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, raw), parameters()));
    }

    private static ECParameterSpec parameters() throws GeneralSecurityException {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    private static byte[] raw(ECPublicKey key) {
        byte[] result = new byte[65];
        result[0] = 4;
        copy32(key.getW().getAffineX(), result, 1);
        copy32(key.getW().getAffineY(), result, 33);
        return result;
    }

    private static void copy32(BigInteger number, byte[] target, int offset) {
        byte[] source = number.toByteArray();
        int sourceOffset = Math.max(0, source.length - 32);
        int length = Math.min(32, source.length);
        System.arraycopy(source, sourceOffset, target, offset + 32 - length, length);
    }

    private static byte[] extract(byte[] salt, byte[] input) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        return mac.doFinal(input);
    }

    private static byte[] expand(byte[] prk, byte[] info, int length) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        byte[] block = mac.doFinal(concat(info, new byte[]{1}));
        return java.util.Arrays.copyOf(block, length);
    }

    private static byte[] concat(byte[]... values) {
        int length = 0;
        for (byte[] value : values) length += value.length;
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] value : values) { System.arraycopy(value, 0, result, offset, value.length); offset += value.length; }
        return result;
    }

    static byte[] derToJose(byte[] der) throws GeneralSecurityException {
        if (der.length < 8 || der[0] != 0x30) throw new GeneralSecurityException("Invalid ECDSA signature");
        int index = 2;
        if (der[1] < 0) index = 2 + (der[1] & 0x7f);
        if (der[index++] != 0x02) throw new GeneralSecurityException("Invalid ECDSA R value");
        int rLength = der[index++] & 255;
        byte[] result = new byte[64];
        copyInteger(der, index, rLength, result, 0);
        index += rLength;
        if (der[index++] != 0x02) throw new GeneralSecurityException("Invalid ECDSA S value");
        int sLength = der[index++] & 255;
        copyInteger(der, index, sLength, result, 32);
        return result;
    }

    private static void copyInteger(byte[] source, int offset, int length, byte[] target, int targetOffset) throws GeneralSecurityException {
        while (length > 0 && source[offset] == 0) { offset++; length--; }
        if (length > 32 || offset + length > source.length) throw new GeneralSecurityException("Invalid ECDSA integer");
        System.arraycopy(source, offset, target, targetOffset + 32 - length, length);
    }

    public record Prepared(byte[] body, String authorization) {
        public Prepared { body = body.clone(); }
        @Override public byte[] body() { return body.clone(); }
    }
}
