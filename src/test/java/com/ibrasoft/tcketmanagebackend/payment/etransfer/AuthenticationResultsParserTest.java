package com.ibrasoft.tcketmanagebackend.payment.etransfer;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationResultsParserTest {

    private final AuthenticationResultsParser parser = new AuthenticationResultsParser();

    @Test
    void extractsAlignedPass() {
        String[] headers = {"mx.google.com; spf=pass smtp.mailfrom=interac.ca; "
                + "dkim=pass header.i=@interac.ca; "
                + "dmarc=pass (p=REJECT sp=REJECT dis=NONE) header.from=interac.ca"};

        Optional<AuthenticationResultsParser.DmarcResult> result = parser.find(headers, "mx.google.com");

        assertTrue(result.isPresent());
        assertTrue(result.get().isPass());
        assertEquals("interac.ca", result.get().headerFrom());
    }

    @Test
    void matchesAuthservIdCaseInsensitivelyAndIgnoresVersionToken() {
        String[] headers = {"Mail.Lensbridge.Tech 1; dmarc=pass header.from=interac.ca"};

        Optional<AuthenticationResultsParser.DmarcResult> result = parser.find(headers, "mail.lensbridge.tech");

        assertTrue(result.isPresent());
        assertTrue(result.get().isPass());
    }

    @Test
    void readsFailVerdict() {
        String[] headers = {"mx.google.com; dmarc=fail header.from=interac.ca"};

        Optional<AuthenticationResultsParser.DmarcResult> result = parser.find(headers, "mx.google.com");

        assertTrue(result.isPresent());
        assertFalse(result.get().isPass());
        assertEquals("fail", result.get().verdict());
    }

    @Test
    void handlesFoldedHeaderAndQuotedHeaderFrom() {
        // RFC 5322 folding: CRLF + leading whitespace splits the value across lines.
        String[] headers = {"mx.google.com;\r\n spf=pass;\r\n dmarc=pass header.from=\"interac.ca\""};

        Optional<AuthenticationResultsParser.DmarcResult> result = parser.find(headers, "mx.google.com");

        assertTrue(result.isPresent());
        assertTrue(result.get().isPass());
        assertEquals("interac.ca", result.get().headerFrom());
    }

    @Test
    void ignoresHeaderFromUntrustedAuthservId() {
        String[] headers = {"evil.example.com; dmarc=pass header.from=interac.ca"};

        assertTrue(parser.find(headers, "mx.google.com").isEmpty());
    }

    @Test
    void picksTheTrustedHeaderAmongSeveral() {
        String[] headers = {
                "evil.example.com; dmarc=pass header.from=interac.ca", // forged, untrusted id
                "mx.google.com; dmarc=fail header.from=interac.ca"};    // genuine

        Optional<AuthenticationResultsParser.DmarcResult> result = parser.find(headers, "mx.google.com");

        assertTrue(result.isPresent());
        assertFalse(result.get().isPass());
    }

    @Test
    void skipsTrustedHeaderWithoutDmarcMethod() {
        String[] headers = {
                "mx.google.com; spf=pass smtp.mailfrom=interac.ca", // no dmarc method here
                "mx.google.com; dmarc=pass header.from=interac.ca"};

        Optional<AuthenticationResultsParser.DmarcResult> result = parser.find(headers, "mx.google.com");

        assertTrue(result.isPresent());
        assertTrue(result.get().isPass());
    }

    @Test
    void emptyWhenNoHeadersOrNoAuthservId() {
        assertTrue(parser.find(null, "mx.google.com").isEmpty());
        assertTrue(parser.find(new String[]{"mx.google.com; dmarc=pass"}, null).isEmpty());
        assertTrue(parser.find(new String[]{"mx.google.com; dmarc=pass"}, "  ").isEmpty());
    }
}
