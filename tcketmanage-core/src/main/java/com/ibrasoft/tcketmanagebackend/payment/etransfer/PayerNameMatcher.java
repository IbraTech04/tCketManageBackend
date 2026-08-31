package com.ibrasoft.tcketmanagebackend.payment.etransfer;

import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderItem;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Compares the payer's name, as the bank reports it, against the names an order carries.
 *
 * <p>This exists for <em>recall</em>, not proof. When the memo is blank the code matcher has nothing
 * to work with and the queue offers no candidates at all, leaving an operator to go and find the
 * order by hand — which they do by recognising the payer's name. Doing that lookup for them is the
 * whole point.
 *
 * <p>It is deliberately NOT evidence. A bank's name for an account is routinely not the buyer's name
 * on the order — a parent pays for a student, someone uses a joint account, a legal name meets a
 * preferred one. So a name match brings a candidate into view and says why it is there; it never
 * marks a field as agreeing, and it never settles anything on its own.
 *
 * <p>An order has no name of its own, so the comparison runs against everything that carries one:
 * the local part of {@code buyerEmail} and the attendee names on the line items.
 */
@Component
public class PayerNameMatcher {

    /**
     * How much of the payer's name the order accounts for.
     *
     * <p>Only {@link #FULL} is strong enough to surface a candidate the code matcher rejected.
     * {@link #PARTIAL} is reported when a candidate is already in the list for another reason, but
     * never promotes one on its own: a shared surname would otherwise drag half the order book into
     * every queue entry, and a suggestion list nobody can trust is worse than an empty one.
     */
    public enum NameMatch { FULL, PARTIAL, NONE }

    /**
     * Tokens shorter than this are dropped from both sides. Initials and stray particles ("J", "DE")
     * collide with everything and would turn a coincidence into a suggestion.
     */
    private static final int MIN_TOKEN = 2;

    /**
     * A single-token payer name can never reach {@link NameMatch#FULL}. "SAMMY" alone matching some
     * order's attendee named Sammy is a coincidence worth showing beside real evidence, not a reason
     * to put that order in front of an operator as a candidate.
     */
    private static final int MIN_TOKENS_FOR_FULL = 2;

    public NameMatch match(String payerName, Order order) {
        Set<String> payer = tokenize(payerName);
        if (payer.isEmpty()) {
            return NameMatch.NONE;
        }
        Set<String> orderTokens = orderTokens(order);
        if (orderTokens.isEmpty()) {
            return NameMatch.NONE;
        }

        long hits = payer.stream().filter(orderTokens::contains).count();
        if (hits == 0) {
            return NameMatch.NONE;
        }
        boolean everyTokenAccountedFor = hits == payer.size();
        return everyTokenAccountedFor && payer.size() >= MIN_TOKENS_FOR_FULL
                ? NameMatch.FULL
                : NameMatch.PARTIAL;
    }

    /** Every name-bearing token on the order: the email's local part, plus attendee names. */
    private static Set<String> orderTokens(Order order) {
        Set<String> tokens = new HashSet<>();
        tokens.addAll(tokenize(localPart(order.getBuyerEmail())));

        List<OrderItem> items = order.getItems();
        if (items != null) {
            for (OrderItem item : items) {
                tokens.addAll(tokenize(item.getAttendeeFirstName()));
                tokens.addAll(tokenize(item.getAttendeeLastName()));
                tokens.addAll(tokenize(localPart(item.getAttendeeEmail())));
            }
        }
        return tokens;
    }

    /**
     * The part of an address before the {@code @}. Buyers overwhelmingly have their own name in it
     * ({@code sammy.nimour@...}), which is the only name many orders carry at all.
     */
    private static String localPart(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    /**
     * Uppercase alphabetic runs. Splitting on everything else means dots, underscores and the digits
     * people append to addresses ({@code sammy.nimour04}) all fall away, and accented letters survive
     * as letters rather than splitting a name in two.
     */
    private static Set<String> tokenize(String value) {
        Set<String> tokens = new HashSet<>();
        if (value == null) {
            return tokens;
        }
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetter(c)) {
                current.append(Character.toUpperCase(c));
            } else {
                if (current.length() >= MIN_TOKEN) {
                    tokens.add(current.toString());
                }
                current.setLength(0);
            }
        }
        if (current.length() >= MIN_TOKEN) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
