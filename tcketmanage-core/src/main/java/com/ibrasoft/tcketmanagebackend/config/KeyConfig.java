package com.ibrasoft.tcketmanagebackend.config;

import com.ibrasoft.tcketmanagebackend.security.CryptoProperties;
import com.ibrasoft.tcketmanagebackend.security.TicketSigningKeys;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Loads the Ed25519 ticket-signing key pair from wherever {@link CryptoProperties} points.
 *
 * <p>Both keys are read at startup so a missing or malformed key fails the context rather than the
 * first ticket issued. The failure message names the property to set and how to generate a key,
 * because "Key not found" on its own is what a fresh checkout used to hit with nothing to act on.
 */
@Configuration
public class KeyConfig {

    private static final String KEYGEN_HINT = """
            Generate a key pair with:
              openssl genpkey -algorithm ed25519 -out private.pem
              openssl pkey -in private.pem -pubout -out public.pem
            then point tcketmanage.crypto.private-key and tcketmanage.crypto.public-key at them \
            (e.g. file:/etc/tcketmanage/private.pem).""";

    @Bean
    public TicketSigningKeys ticketSigningKeys(CryptoProperties properties) {
        return new TicketSigningKeys(
                readPrivateKey(properties.getPrivateKey()),
                readPublicKey(properties.getPublicKey()));
    }

    private PrivateKey readPrivateKey(Resource resource) {
        try {
            return KeyFactory.getInstance("Ed25519")
                    .generatePrivate(new PKCS8EncodedKeySpec(readPem(resource, "tcketmanage.crypto.private-key")));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "tCketManage could not parse the Ed25519 private key at " + describe(resource)
                            + ". It must be an unencrypted PKCS#8 PEM. " + KEYGEN_HINT, e);
        }
    }

    private PublicKey readPublicKey(Resource resource) {
        try {
            return KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(readPem(resource, "tcketmanage.crypto.public-key")));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "tCketManage could not parse the Ed25519 public key at " + describe(resource)
                            + ". It must be an X.509 PEM. " + KEYGEN_HINT, e);
        }
    }

    /** Strips the PEM armour and decodes the base64 body to DER. */
    private byte[] readPem(Resource resource, String property) {
        if (resource == null || !resource.exists()) {
            throw new IllegalStateException(
                    "tCketManage requires an Ed25519 signing key, but " + property + " points at "
                            + describe(resource) + ", which does not exist. Key material is not shipped "
                            + "inside the tcketmanage-core jar — every deployment supplies its own, so "
                            + "that no two deployments can sign each other's tickets. " + KEYGEN_HINT);
        }
        try (InputStream in = resource.getInputStream()) {
            String normalized = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("-----BEGIN (.*)-----", "")
                    .replaceAll("-----END (.*)-----", "")
                    .replaceAll("\\s", "");
            return Base64.getDecoder().decode(normalized);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "tCketManage could not read the key at " + describe(resource)
                            + " (from " + property + ").", e);
        }
    }

    private String describe(Resource resource) {
        return resource == null ? "(unset)" : resource.getDescription();
    }
}
