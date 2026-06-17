package com.ibrasoft.tcketmanagebackend.payment.etransfer;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class InteracEmailParserTest {

    private final InteracEmailParser parser = new InteracEmailParser();

    /** Wraps a memo and amount in a minimal facsimile of the Interac label/value layout. */
    private static String email(String message, String amountLine) {
        return "<html><body>"
                + "<p>Message: </p><p>" + message + "</p>"
                + "<p>Date: </p><p>June 6, 2026</p>"
                + "<p>Reference Number: </p><p>C1AZNtDBMJ5B</p>"
                + "<p>Sent From: </p><p>SAMMY NIMOUR</p>"
                + "<p>Amount: </p><p>" + amountLine + "</p>"
                + "</body></html>";
    }

    @Test
    void parsesRealInteracSample() throws Exception {
        String html;
        try (InputStream in = getClass().getResourceAsStream("/interac-etransfer-sample.html")) {
            assertNotNull(in, "sample email resource missing");
            // The saved page is UTF-16 (BOM); the UTF_16 charset detects endianness from the BOM.
            html = new String(in.readAllBytes(), StandardCharsets.UTF_16);
        }

        ParsedEtransfer parsed = parser.parse(html);

        assertEquals(0, new BigDecimal("35.00").compareTo(parsed.amount()));
        assertEquals("CAD", parsed.currency());
        assertEquals("C1AZNtDBMJ5B", parsed.interacReferenceNumber());
        assertEquals("skmatebroaed", parsed.message());
        // The sample memo has no dash-delimited code, so nothing is extracted (→ would quarantine).
        assertNull(parsed.referenceCode());
    }

    @Test
    void extractsBareCode() {
        assertEquals("ABCD-EFGH", parser.parse(email("ABCD-EFGH", "$10.00 (CAD)")).referenceCode());
    }

    @Test
    void extractsCodeEmbeddedInChattyMemo() {
        // Must pick the real code, not greedily harvest "KETS-ABCD" across the space.
        assertEquals("ABCD-EFGH",
                parser.parse(email("Tickets ABCD-EFGH thanks!", "$10.00 (CAD)")).referenceCode());
    }

    @Test
    void normalizesCaseAndSpacingAroundDash() {
        assertEquals("ABCD-EFGH", parser.parse(email("abcd-efgh", "$10.00 (CAD)")).referenceCode());
        assertEquals("ABCD-EFGH", parser.parse(email("ABCD - EFGH", "$10.00 (CAD)")).referenceCode());
    }

    @Test
    void noCodeWhenDashOmittedOrAbsent() {
        assertNull(parser.parse(email("ABCDEFGH", "$10.00 (CAD)")).referenceCode());
        assertNull(parser.parse(email("no code here", "$10.00 (CAD)")).referenceCode());
    }

    @Test
    void defaultsCurrencyToCadAndHandlesThousandsSeparator() {
        ParsedEtransfer parsed = parser.parse(email("ABCD-EFGH", "$1,234.56"));
        assertEquals(0, new BigDecimal("1234.56").compareTo(parsed.amount()));
        assertEquals("CAD", parsed.currency());
    }

    @Test
    void throwsWhenAmountMissing() {
        String html = "<html><body><p>Message: </p><p>ABCD-EFGH</p></body></html>";
        assertThrows(EtransferParseException.class, () -> parser.parse(html));
    }
}
