package com.example.ticket.common.token;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HmacSignerTest {

    private static final String SECRET = "test-secret-key";

    @Test
    void createAndVerifyToken() {
        String payload = "ev1|sc1|1700000000|nonce123";
        String token = HmacSigner.createToken(payload, SECRET);

        assertNotNull(token);
        assertTrue(token.contains("."));

        String extracted = HmacSigner.verifyAndExtract(token, SECRET);
        assertEquals(payload, extracted);
    }

    @Test
    void verifyFailsWithWrongSecret() {
        String payload = "ev1|sc1|1700000000";
        String token = HmacSigner.createToken(payload, SECRET);

        String extracted = HmacSigner.verifyAndExtract(token, "wrong-secret");
        assertNull(extracted);
    }

    @Test
    void verifyFailsWithTamperedToken() {
        String payload = "ev1|sc1|1700000000";
        String token = HmacSigner.createToken(payload, SECRET);

        String tampered = token.substring(0, token.indexOf('.')) + ".tampered_sig";
        String extracted = HmacSigner.verifyAndExtract(tampered, SECRET);
        assertNull(extracted);
    }

    @Test
    void verifyFailsWithNoSeparator() {
        assertNull(HmacSigner.verifyAndExtract("no-dot-token", SECRET));
    }

    @Test
    void verifyFailsWithInvalidBase64() {
        assertNull(HmacSigner.verifyAndExtract("!!!invalid.sig", SECRET));
    }

    @Test
    void signProducesDeterministicResult() {
        String sig1 = HmacSigner.sign("same-payload", SECRET);
        String sig2 = HmacSigner.sign("same-payload", SECRET);
        assertEquals(sig1, sig2);
    }

    @Test
    void signProducesDifferentResultForDifferentPayloads() {
        String sig1 = HmacSigner.sign("payload-a", SECRET);
        String sig2 = HmacSigner.sign("payload-b", SECRET);
        assertNotEquals(sig1, sig2);
    }
}
