package com.ibrasoft.tcketmanagebackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
public class KeyConfig {

    private byte[] readPem(String path) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Key not found: " + path);
            }

            String pem = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            String normalized = pem
                    .replaceAll("-----BEGIN (.*)-----", "")
                    .replaceAll("-----END (.*)-----", "")
                    .replaceAll("\\s", "");

            return Base64.getDecoder().decode(normalized);
        }
    }

    @Bean
    public PrivateKey privateKey() throws Exception {
        PKCS8EncodedKeySpec spec =
                new PKCS8EncodedKeySpec(readPem("keys/private.pem"));

        return KeyFactory.getInstance("Ed25519")
                .generatePrivate(spec);
    }

    @Bean
    public PublicKey publicKey() throws Exception {
        X509EncodedKeySpec spec =
                new X509EncodedKeySpec(readPem("keys/public.pem"));

        return KeyFactory.getInstance("Ed25519")
                .generatePublic(spec);
    }
}
