package com.ibrasoft.tcketmanagebackend.payment.etransfer;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the financial fields from an Interac e-Transfer notification email.
 *
 * <p>Interac notifications are heavy Outlook/MSO HTML where each datum is a "label" paragraph
 * (e.g. {@code Message:}) followed by a "value" paragraph. Rather than couple to that brittle DOM
 * layout, we let Jsoup flatten the document to clean visible text and then anchor on the label
 * words. In reading order the body yields:
 *
 * <pre>... Message: &lt;memo&gt; Date: &lt;date&gt; Reference Number: &lt;ref&gt; Sent From: &lt;name&gt; Amount: $&lt;amt&gt; (CAD) ...</pre>
 *
 * <p>The parser is pure (operates on an HTML string) so it is unit-testable against a saved sample.
 * Sender trust and order matching live in {@link EtransferConfirmationService}.
 */
@Component
public class InteracEmailParser {

    /**
     * Reference-code alphabet, mirroring {@code OrderTransactions} ({@code 23456789ABCDEFGHJKMNPQRSTVWXYZ}
     * digits 2-9 and letters excluding the ambiguous I, L, O, U). Codes are issued as two
     * dash-separated quads ({@code XXXX-XXXX}) and buyers copy-paste them, so we anchor on the dash
     * (optional surrounding spaces) with alphanumeric word boundaries. Anchoring on the dash avoids
     * greedily harvesting a spurious "code" from ordinary words in a chatty memo; a memo that omits
     * the dash simply won't match and gets quarantined for an operator.
     */
    private static final Pattern CODE = Pattern.compile(
            "(?<![A-Za-z0-9])([2-9A-HJKMNP-TV-Z]{4})\\s*-\\s*([2-9A-HJKMNP-TV-Z]{4})(?![A-Za-z0-9])",
            Pattern.CASE_INSENSITIVE);

    /** "Message: &lt;memo&gt;" up to (but not including) the next known label or end of text. */
    private static final Pattern MESSAGE = Pattern.compile(
            "Message:\\s*(.*?)\\s*(?=Date:|Reference Number:|Sent From:|Amount:|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** "Amount: $1,234.56 (CAD)" - currency group optional, thousands separators tolerated. */
    private static final Pattern AMOUNT = Pattern.compile(
            "Amount:\\s*\\$?\\s*([\\d,]+\\.\\d{2})\\s*(?:\\(([A-Za-z]{3})\\))?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern REFERENCE_NUMBER = Pattern.compile(
            "Reference Number:\\s*([A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE);

    /**
     * Parses the notification body.
     *
     * @param html the raw HTML of the email
     * @return the extracted fields
     * @throws EtransferParseException if the amount can't be located (i.e. this isn't a recognizable
     *                                 deposit notification)
     */
    public ParsedEtransfer parse(String html) {
        // Jsoup strips the MSO markup, <style>, <script> and collapses whitespace to a clean string.
        String text = Jsoup.parse(html).text();

        Matcher amountMatcher = AMOUNT.matcher(text);
        if (!amountMatcher.find()) {
            throw new EtransferParseException("No 'Amount:' field found; not a recognizable e-Transfer notification.");
        }
        BigDecimal amount = new BigDecimal(amountMatcher.group(1).replace(",", ""));
        String currency = amountMatcher.group(2) != null
                ? amountMatcher.group(2).toUpperCase()
                : "CAD";

        String message = firstGroup(MESSAGE, text);
        String referenceCode = extractCode(message);
        String interacReferenceNumber = firstGroup(REFERENCE_NUMBER, text);

        return new ParsedEtransfer(message, referenceCode, amount, currency, interacReferenceNumber);
    }

    /** Returns the canonical {@code XXXX-XXXX} code embedded in the memo, or {@code null} if none. */
    private static String extractCode(String message) {
        if (message == null) {
            return null;
        }
        Matcher m = CODE.matcher(message);
        if (!m.find()) {
            return null;
        }
        return (m.group(1) + "-" + m.group(2)).toUpperCase();
    }

    private static String firstGroup(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        if (!m.find()) {
            return null;
        }
        String value = m.group(1);
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
