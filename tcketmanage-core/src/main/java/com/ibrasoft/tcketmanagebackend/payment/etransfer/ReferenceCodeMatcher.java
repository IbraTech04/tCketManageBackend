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
     * The closest run of a memo to a reference code, and how far off it was.
     *
     * <p>{@code excerpt} is the normalized run itself, not just its score, because a caller showing
     * an operator <em>which</em> characters differ has to highlight the same run the number came
     * from. Recomputing it on the other side of an API would be a second source of truth that can
     * disagree with the first — and a highlight that contradicts its own score is worse than none.
     *
     * @param distance edits from {@code excerpt} to the code, or {@link #NO_MATCH}
     * @param excerpt  the matched run, or {@code null} when there was no match
     */
    public record Match(int distance, String excerpt) {
        public boolean matched() {
            return distance != NO_MATCH;
        }
    }

    private static final Match NONE = new Match(NO_MATCH, null);

    /**
     * The fewest single-character edits that turn some run of the memo into {@code referenceCode},
     * with the run that achieved it.
     *
     * <p>Compares the code against every window of the normalized memo rather than the memo as a
     * whole, so surrounding words ("tickets ABCD-EFGH thanks") don't count as edits. Windows one
     * shorter and one longer than the code are included so a dropped or doubled character stays a
     * distance of 1 instead of cascading.
     */
    public Match bestMatch(String memo, String referenceCode) {
        String code = normalize(referenceCode);
        String text = normalize(memo);
        if (code.isEmpty() || text.isEmpty()) {
            return NONE;
        }

        int best = NO_MATCH;
        String bestRun = null;
        // A window shorter than the code costs deletions, longer costs insertions; +/-1 covers the
        // single dropped or doubled character that dominates real typos without inviting long windows
        // to look artificially close.
        //
        // Equal length is tried FIRST and ties are kept (strict < below), which decides which run a
        // caller highlights when several score the same. "ABCDEFGX" and "ABCDEFG" are both one edit
        // from "ABCDEFGH", but only the equal-length run aligns character-for-character against the
        // code — highlighting the short one would claim the buyer dropped a character when they
        // actually mistyped one.
        for (int len : new int[]{code.length(), code.length() - 1, code.length() + 1}) {
            if (len <= 0 || len > text.length()) {
                continue;
            }
            for (int start = 0; start + len <= text.length(); start++) {
                String run = text.substring(start, start + len);
                int d = levenshtein(run, code, best);
                if (d < best) {
                    best = d;
                    bestRun = run;
                    if (best == 0) {
                        return new Match(0, run); // exact run present; nothing can beat it
                    }
                }
            }
        }
        return best <= MAX_USEFUL_DISTANCE ? new Match(best, bestRun) : NONE;
    }

    /**
     * The distance alone, for callers that only rank.
     *
     * @return the distance, or {@link #NO_MATCH} if either side is empty or nothing scores within
     *         {@link #MAX_USEFUL_DISTANCE}
     */
    public int distance(String memo, String referenceCode) {
        return bestMatch(memo, referenceCode).distance();
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
