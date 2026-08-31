package com.ibrasoft.tcketmanagebackend.payment.etransfer;

import org.springframework.stereotype.Component;

/**
 * Scores how close a buyer's memo came to an order reference code.
 *
 * <p>This exists because the common failure is a <em>mistyped</em> code, not an absent one. The code
 * alphabet ({@code 23456789ABCDEFGHJKMNPQRSTVWXYZ}) deliberately omits I, L, O and U precisely
 * because those are the characters people transcribe wrong, which means a botched code is usually one
 * or two edits away from a real one and is recoverable. {@link InteracEmailParser} only recognises an
 * exact {@code XXXX-XXXX}, so everything else falls through to the review queue with the answer
 * sitting right there in the memo.
 *
 * <p>What this is <strong>not</strong>: an auto-matcher. It ranks candidates for a human to choose
 * from. {@link EtransferConfirmationService} never auto-confirms on a partial match, and a near-miss
 * on eight characters is exactly that — at distance 2 the memo is as close to several codes as to the
 * right one. Nothing here settles an order on its own.
 */
@Component
public class ReferenceCodeMatcher {

    /**
     * Beyond this many edits the "match" carries no information: at distance 4 on an 8-character code
     * you are closer to noise than to the buyer's intent, and surfacing it would train operators to
     * click through suggestions without reading them.
     */
    public static final int MAX_USEFUL_DISTANCE = 3;

    /** Returned when the memo contains nothing resembling the code. */
    public static final int NO_MATCH = Integer.MAX_VALUE;

    /**
     * The fewest single-character edits that turn some run of the memo into {@code referenceCode}.
     *
     * <p>Compares the code against every window of the normalized memo rather than the memo as a
     * whole, so surrounding words ("tickets ABCD-EFGH thanks") don't count as edits. Windows one
     * shorter and one longer than the code are included so a dropped or doubled character stays a
     * distance of 1 instead of cascading.
     *
     * @return the distance, or {@link #NO_MATCH} if either side is empty or nothing scores within
     *         {@link #MAX_USEFUL_DISTANCE}
     */
    public int distance(String memo, String referenceCode) {
        String code = normalize(referenceCode);
        String text = normalize(memo);
        if (code.isEmpty() || text.isEmpty()) {
            return NO_MATCH;
        }

        int best = NO_MATCH;
        // A window shorter than the code costs deletions, longer costs insertions; +/-1 covers the
        // single dropped or doubled character that dominates real typos without inviting long windows
        // to look artificially close.
        for (int len = code.length() - 1; len <= code.length() + 1; len++) {
            if (len <= 0 || len > text.length()) {
                continue;
            }
            for (int start = 0; start + len <= text.length(); start++) {
                int d = levenshtein(text.substring(start, start + len), code, best);
                if (d < best) {
                    best = d;
                    if (best == 0) {
                        return 0; // exact run present; nothing can beat it
                    }
                }
            }
        }
        return best <= MAX_USEFUL_DISTANCE ? best : NO_MATCH;
    }

    /** Whether the memo is close enough to {@code referenceCode} to be worth showing an operator. */
    public boolean isCandidate(String memo, String referenceCode) {
        return distance(memo, referenceCode) != NO_MATCH;
    }

    /**
     * Uppercases and strips everything that isn't a letter or digit, so the dash in {@code XXXX-XXXX},
     * spacing, and punctuation in a chatty memo are all invisible to the comparison. Without this the
     * dash alone would cost an edit against every code.
     */
    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toUpperCase(c));
            }
        }
        return sb.toString();
    }

    /**
     * Standard two-row Levenshtein, abandoned early once every cell in a row exceeds {@code cutoff}.
     * The cutoff matters: this runs across every open order for every receipt in the queue, and the
     * overwhelming majority of pairs are nowhere near each other and can be dropped after one row.
     */
    private static int levenshtein(String a, String b, int cutoff) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            int rowBest = current[0];
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
                rowBest = Math.min(rowBest, current[j]);
            }
            if (rowBest >= cutoff) {
                return NO_MATCH; // cannot improve on the best seen so far
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
