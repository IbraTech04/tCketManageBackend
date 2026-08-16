package com.ibrasoft.tcketmanagebackend.security;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * The Ed25519 key pair tickets are signed and verified with.
 *
 * <p>Deliberately a named, core-specific type rather than the bare {@link PrivateKey} and
 * {@link PublicKey} beans this replaces. Those were both generically typed and generically named
 * ({@code privateKey}, {@code publicKey}), which is a poor neighbour inside an embedding host: a
 * host that signs its own JWTs could pick up core's ticket-signing key by type without anything
 * looking wrong at the injection site. Core's key material is now only reachable by asking for it
 * explicitly.
 *
 * @param privateKey signs ticket QR payloads; see {@code CryptoService.sign}
 * @param publicKey  verifies them at scan time; see {@code CryptoService.verify}
 */
public record TicketSigningKeys(PrivateKey privateKey, PublicKey publicKey) {
}
