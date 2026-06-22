package com.ibrasoft.tcketmanagebackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketQRData;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

@Service
@AllArgsConstructor
public class CryptoService {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final ObjectMapper objectMapper;

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DEC = Base64.getUrlDecoder();

    /**
     * Serializes and signs a TicketQRData into a JWT-style token.
     * Format: base64url(JSON) + "." + base64url(Ed25519 signature)
     * The signature covers the bytes of the base64url payload string, not the raw JSON,
     * which avoids any JSON canonicalization concerns.
     */
    public String sign(TicketQRData data) throws Exception {
        if (data.getTicketID() == null || data.getEventID() == null) {
            throw new IllegalArgumentException("Ticket ID and Event ID must be set before signing");
        }

        String payloadB64 = B64.encodeToString(objectMapper.writeValueAsBytes(data));

        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(privateKey);
        sig.update(payloadB64.getBytes(StandardCharsets.UTF_8));

        return payloadB64 + "." + B64.encodeToString(sig.sign());
    }

    /**
     * Verifies a token and returns the decoded TicketQRData.
     * Throws if the token is malformed, tampered, or the signature is invalid.
     */
    public TicketQRData verify(String token) throws Exception {
        int dot = token.indexOf('.');
        if (dot < 0) {
            throw new IllegalArgumentException("Malformed token: missing '.'");
        }

        String payloadB64 = token.substring(0, dot);
        String signatureB64 = token.substring(dot + 1);

        Signature sig = Signature.getInstance("Ed25519");
        sig.initVerify(publicKey);
        sig.update(payloadB64.getBytes(StandardCharsets.UTF_8));

        if (!sig.verify(B64_DEC.decode(signatureB64))) {
            throw new SecurityException("Invalid signature");
        }

        return objectMapper.readValue(B64_DEC.decode(payloadB64), TicketQRData.class);
    }

    /**
     * Returns true if the token has a valid signature, false for any failure.
     */
    public boolean isValid(String token) {
        try {
            verify(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
