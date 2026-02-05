package com.example.ticket.common.token;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public final class HmacSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private HmacSigner() {
    }

    public static String sign(String payload, String secret) {
        byte[] mac = computeMac(payload.getBytes(StandardCharsets.UTF_8), secret);
        return ENCODER.encodeToString(mac);
    }

    public static String createToken(String payload, String secret) {
        String encoded = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String sig = sign(payload, secret);
        return encoded + "." + sig;
    }


    public static String verifyAndExtract(String token, String secret) {
        int dotIndex = token.indexOf('.');
        if (dotIndex < 0) {
            return null;
        }

        String encodedPayload = token.substring(0, dotIndex);
        String providedSig = token.substring(dotIndex + 1);

        String payload;
        try {
            payload = new String(DECODER.decode(encodedPayload), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }

        String expectedSig = sign(payload, secret);
        if (!constantTimeEquals(expectedSig, providedSig)) {
            return null;
        }

        return payload;
    }

    private static byte[] computeMac(byte[] data, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
