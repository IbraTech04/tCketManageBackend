package com.ibrasoft.tcketmanagebackend.payment.etransfer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReferenceCodeMatcherTest {

    private final ReferenceCodeMatcher matcher = new ReferenceCodeMatcher();

    @Test
    void exactCodeIsDistanceZero() {
        assertEquals(0, matcher.distance("ABCD-EFGH", "ABCD-EFGH"));
    }

    @Test
    void punctuationAndCaseAreInvisible() {
        // The dash is stripped on both sides; without that it would cost an edit against every code.
        assertEquals(0, matcher.distance("abcd efgh", "ABCD-EFGH"));
        assertEquals(0, matcher.distance("abcdefgh", "ABCD-EFGH"));
        assertEquals(0, matcher.distance("  ABCD--EFGH!  ", "ABCD-EFGH"));
    }

    @Test
    void surroundingWordsCostNothing() {
        // Windowing is the point: a chatty memo must not read as a dozen edits.
        assertEquals(0, matcher.distance("tickets ABCD-EFGH thanks!", "ABCD-EFGH"));
    }

    @Test
    void singleSubstitutionIsDistanceOne() {
        // The typo this whole class exists for: one character read wrong off a screen.
        assertEquals(1, matcher.distance("ABCD-EFGX", "ABCD-EFGH"));
        assertEquals(1, matcher.distance("Tickets ABCD-EFGX thanks", "ABCD-EFGH"));
    }

    @Test
    void droppedCharacterIsDistanceOne() {
        // The shorter window exists for this: seven of the eight characters, nothing to pad with.
        assertEquals(1, matcher.distance("ABCD-EFG", "ABCD-EFGH"));
    }

    @Test
    void strayExtraCharacterStillContainsTheCodeExactly() {
        // "ABCDEFGHH" contains "ABCDEFGH" verbatim, so this is a match, not a near-miss — the buyer
        // typed the code correctly and fumbled an extra keystroke after it. Scoring it 0 is what puts
        // the right order at the top of the queue.
        assertEquals(0, matcher.distance("ABCD-EFGHH", "ABCD-EFGH"));
        assertEquals(0, matcher.distance("XABCD-EFGH", "ABCD-EFGH"));
    }

    @Test
    void confusableCharactersTheAlphabetExcludes() {
        // The code alphabet omits I, L, O and U because people transcribe them wrong. A buyer who
        // wrote O for 0 or I for 1 should still land within reach.
        assertEquals(1, matcher.distance("ABCD-EFGO", "ABCD-EFG0"));
        assertEquals(1, matcher.distance("ABCD-EFGI", "ABCD-EFG7"));
    }

    @Test
    void unrelatedMemoDoesNotMatch() {
        assertEquals(ReferenceCodeMatcher.NO_MATCH, matcher.distance("thanks for the tickets", "ABCD-EFGH"));
        assertEquals(ReferenceCodeMatcher.NO_MATCH, matcher.distance("", "ABCD-EFGH"));
        assertFalse(matcher.isCandidate("happy birthday", "ABCD-EFGH"));
    }

    @Test
    void beyondThreeEditsIsTreatedAsNoise() {
        // Four edits on eight characters is closer to noise than intent; surfacing it would train
        // operators to click through suggestions without reading them.
        assertEquals(ReferenceCodeMatcher.NO_MATCH, matcher.distance("ABCD-1234", "WXYZ-EFGH"));
    }

    @Test
    void nullsAreSafeAndNeverMatch() {
        assertEquals(ReferenceCodeMatcher.NO_MATCH, matcher.distance(null, "ABCD-EFGH"));
        assertEquals(ReferenceCodeMatcher.NO_MATCH, matcher.distance("ABCD-EFGH", null));
        assertEquals(ReferenceCodeMatcher.NO_MATCH, matcher.distance(null, null));
    }

    @Test
    void closerCodeScoresLowerThanFartherOne() {
        // The ranking property the queue depends on: the order the buyer meant sorts first.
        String memo = "order ABCD-EFGX";
        int intended = matcher.distance(memo, "ABCD-EFGH");
        int other = matcher.distance(memo, "ABCD-EFRT");
        assertTrue(intended < other,
                "expected the near-miss code to score lower than an unrelated one, got "
                        + intended + " vs " + other);
    }

    @Test
    void longMemoDoesNotFalselyMatchByWindowing() {
        // A long memo offers many windows; none of them should manufacture a match out of prose.
        String memo = "Hello there, please find attached the payment for the event this weekend, thanks";
        assertEquals(ReferenceCodeMatcher.NO_MATCH, matcher.distance(memo, "ABCD-EFGH"));
    }
}
