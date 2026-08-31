package com.ibrasoft.tcketmanagebackend.payment.etransfer;

import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderItem;
import com.ibrasoft.tcketmanagebackend.payment.etransfer.PayerNameMatcher.NameMatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PayerNameMatcherTest {

    private final PayerNameMatcher matcher = new PayerNameMatcher();

    private static Order order(String buyerEmail, String... attendees) {
        List<OrderItem> items = java.util.Arrays.stream(attendees).map(a -> {
            String[] parts = a.split(" ", 2);
            return OrderItem.builder()
                    .attendeeFirstName(parts[0])
                    .attendeeLastName(parts.length > 1 ? parts[1] : "")
                    .build();
        }).toList();
        return Order.builder().buyerEmail(buyerEmail).items(items).build();
    }

    @Test
    void nameInTheBuyerEmailIsAFullMatch() {
        // The only name most orders carry is the one buried in the address.
        assertEquals(NameMatch.FULL,
                matcher.match("SAMMY NIMOUR", order("sammy.nimour@mail.utoronto.ca")));
    }

    @Test
    void digitsAndSeparatorsInTheAddressAreIgnored() {
        assertEquals(NameMatch.FULL,
                matcher.match("Sammy Nimour", order("sammy_nimour04@mail.utoronto.ca")));
    }

    @Test
    void attendeeNamesCount() {
        // The buyer's address gives nothing away, but the seat is in their name.
        assertEquals(NameMatch.FULL,
                matcher.match("SAMMY NIMOUR", order("tickets2026@gmail.com", "Sammy Nimour")));
    }

    @Test
    void surnameAloneIsOnlyPartial() {
        // A shared surname must never promote an order into the list on its own — half the order
        // book would follow it in.
        assertEquals(NameMatch.PARTIAL,
                matcher.match("SAMMY NIMOUR", order("j.nimour@mail.utoronto.ca")));
    }

    @Test
    void singleTokenNameCanNeverBeFull() {
        // "SAMMY" landing on someone called Sammy is a coincidence, not a candidate.
        assertEquals(NameMatch.PARTIAL, matcher.match("SAMMY", order("sammy@example.com")));
    }

    @Test
    void unrelatedNameDoesNotMatch() {
        assertEquals(NameMatch.NONE,
                matcher.match("SAMMY NIMOUR", order("j.chen@mail.utoronto.ca", "Jia Chen")));
    }

    @Test
    void initialsAreDroppedRatherThanMatchedOnEveryone() {
        // A one-letter token collides with almost any address; treating it as a hit would make
        // every order a partial match.
        assertEquals(NameMatch.NONE, matcher.match("J C", order("sammy.nimour@mail.utoronto.ca")));
    }

    @Test
    void accentedLettersStayPartOfTheirToken() {
        assertEquals(NameMatch.FULL, matcher.match("RENÉE MÜLLER", order("renée.müller@example.com")));
    }

    @Test
    void missingDataIsSafeAndNeverMatches() {
        assertEquals(NameMatch.NONE, matcher.match(null, order("a.b@example.com")));
        assertEquals(NameMatch.NONE, matcher.match("   ", order("a.b@example.com")));
        assertEquals(NameMatch.NONE, matcher.match("SAMMY NIMOUR", Order.builder().build()));
    }

    @Test
    void orderOfNamePartsDoesNotMatter() {
        // Banks report "SURNAME, GIVEN" as often as the other way round.
        assertEquals(NameMatch.FULL,
                matcher.match("NIMOUR, SAMMY", order("sammy.nimour@mail.utoronto.ca")));
    }
}
