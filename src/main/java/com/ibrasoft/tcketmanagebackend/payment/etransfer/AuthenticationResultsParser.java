package com.ibrasoft.tcketmanagebackend.payment.etransfer;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the DMARC verdict out of the {@code Authentication-Results} header(s) a receiving mail
 * server stamps onto inbound mail (RFC 8601). DMARC/SPF/DKIM are evaluated by that server when the
 * message arrives; by the time we fetch it over IMAP the verdict is already recorded, so we consume
 * it rather than re-validating SPF/DKIM ourselves.
 *
 * <p><b>Trust boundary.</b> An {@code Authentication-Results} header is plain text and a spoofer can
 * embed forged ones in a message. Only the header whose {@code authserv-id} (its first token) matches
 * the operator's own server is trusted; everything else is ignored. The receiving server is expected
 * to strip inbound headers bearing its own {@code authserv-id}, so the surviving one is genuine.
 *
 * <p>Pure (operates on the header strings) so it is unit-testable, mirroring {@link InteracEmailParser}.
 */
@Component
public class AuthenticationResultsParser {

    /** "dmarc=pass" / "dmarc=fail" / "dmarc=none" - the method result token. */
    private static final Pattern DMARC = Pattern.compile(
            "\\bdmarc\\s*=\\s*(\\w+)", Pattern.CASE_INSENSITIVE);

    /** "header.from=interac.ca" - the authenticated From domain (optionally quoted). */
    private static final Pattern HEADER_FROM = Pattern.compile(
            "header\\.from\\s*=\\s*\"?([A-Za-z0-9.\\-]+)", Pattern.CASE_INSENSITIVE);

    /**
     * Finds the DMARC verdict recorded by the trusted server.
     *
     * @param headers    every {@code Authentication-Results} header value, in message order (the
     *                   topmost - most recently added by the receiving server - first)
     * @param authservId the operator's own {@code authserv-id}; only a header bearing it is trusted
     * @return the verdict from the first trusted header that carries a {@code dmarc} method, or empty
     *         if no such header is present (the caller fails closed)
     */
    public Optional<DmarcResult> find(String[] headers, String authservId) {
        if (headers == null || authservId == null || authservId.isBlank()) {
            return Optional.empty();
        }
        for (String raw : headers) {
            if (raw == null) {
                continue;
            }
            // Unfold (RFC 5322 folding inserts CRLF + whitespace) into a single line for matching.
            String header = raw.replaceAll("\\s+", " ").trim();
            if (!authservId.equalsIgnoreCase(authservId(header))) {
                continue;
            }
            Matcher dmarc = DMARC.matcher(header);
            if (!dmarc.find()) {
                continue; // trusted header but no DMARC method on it; keep looking
            }
            Matcher from = HEADER_FROM.matcher(header);
            String headerFrom = from.find() ? from.group(1).toLowerCase() : null;
            return Optional.of(new DmarcResult(dmarc.group(1).toLowerCase(), headerFrom));
        }
        return Optional.empty();
    }

    /** The {@code authserv-id} is the first field, before any optional version number or {@code ;}. */
    private static String authservId(String header) {
        int semicolon = header.indexOf(';');
        String firstField = (semicolon >= 0 ? header.substring(0, semicolon) : header).trim();
        if (firstField.isEmpty()) {
            return null;
        }
        int space = firstField.indexOf(' '); // strip a trailing "1" version token, if any
        return space >= 0 ? firstField.substring(0, space) : firstField;
    }

    /**
     * A DMARC outcome extracted from a trusted {@code Authentication-Results} header.
     *
     * @param verdict    the lowercased result, e.g. {@code pass}, {@code fail}, {@code none}
     * @param headerFrom the authenticated From domain (lowercased), or {@code null} if absent
     */
    public record DmarcResult(String verdict, String headerFrom) {

        public boolean isPass() {
            return "pass".equals(verdict);
        }
    }
}
