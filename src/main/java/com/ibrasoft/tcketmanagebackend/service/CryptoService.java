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

    public void signTicket(TicketQRData ticket) throws Exception {
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privateKey);
        sig.update(ticket.getTicketID().toString().getBytes(StandardCharsets.UTF_8));
        ticket.setSignature(Base64.getEncoder().encodeToString(sig.sign()));
    }

    public boolean validateSignature(TicketQRData ticket) {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(ticket.getTicketID().toString().getBytes(StandardCharsets.UTF_8));
            return sig.verify(Base64.getDecoder().decode(ticket.getSignature()));
        } catch (Exception e) {
            return false;
        }
    }

    public String toBase64(Object pojo) throws Exception {
        String json = objectMapper.writeValueAsString(pojo);
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
