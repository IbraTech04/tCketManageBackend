package com.ibrasoft.tcketmanagebackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketQRData;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

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

    private byte[] canonicalize(TicketQRData ticket) throws Exception {
        // Ensure deterministic JSON output
        objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        return objectMapper.writeValueAsBytes(ticket);
    }

    /**
     * Signs the full ticket payload using Ed25519.
     */
    public void signTicket(TicketQRData ticket) throws Exception {

        if (ticket.getTicketID() == null || ticket.getEventID() == null) {
            throw new IllegalArgumentException("Ticket ID and Event ID must be set before signing");
        }

        // Do NOT include signature field in signing input
        ticket.setSignature(null);

        byte[] payloadBytes = canonicalize(ticket);

        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(privateKey);
        sig.update(payloadBytes);

        String signature = Base64.getEncoder().encodeToString(sig.sign());

        ticket.setSignature(signature);
    }

    /**
     * Validates Ed25519 signature against the full ticket payload.
     */
    public boolean validateSignature(TicketQRData ticket) {

        try {
            String signatureB64 = ticket.getSignature();

            if (signatureB64 == null) return false;

            // Remove signature before verifying payload
            String originalSignature = ticket.getSignature();
            ticket.setSignature(null);

            byte[] payloadBytes = canonicalize(ticket);

            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(publicKey);
            sig.update(payloadBytes);

            boolean valid = sig.verify(Base64.getDecoder().decode(originalSignature));

            // restore object (important for caller)
            ticket.setSignature(originalSignature);

            return valid;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Encodes ticket payload (without signature handling).
     * Useful if you want raw payload transport.
     */
    public String toBase64(Object pojo) throws Exception {
        byte[] json = objectMapper.writeValueAsBytes(pojo);
        return Base64.getEncoder().encodeToString(json);
    }

    /**
     * Decodes QR payload (base64 JSON → object).
     */
    public TicketQRData decode(String base64) throws Exception {
        byte[] json = Base64.getDecoder().decode(base64);
        return objectMapper.readValue(json, TicketQRData.class);
    }

    /**
     * Optional helper: builds full QR string
     * FORMAT: base64(payload).base64(signature)
     */
    public String buildQr(TicketQRData ticket) throws Exception {
        signTicket(ticket);

        String payloadB64 = toBase64(ticket);

        return payloadB64 + "." + ticket.getSignature();
    }
}