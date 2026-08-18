package com.ibrasoft.tcketmanagebackend.model.order;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The buyer-facing copy lives in the enum rather than the template, so it can be checked here
 * without rendering HTML. These guard the things that are actually easy to get wrong when a fifth
 * notice is added: a forgotten {@code %s}, or a blank field that renders as an empty paragraph.
 */
class OrderNotificationTest {

    @Test
    void everyNotice_namesTheEventInItsSubject() {
        for (OrderNotification notification : OrderNotification.values()) {
            String subject = notification.subject("Spring Gala");
            assertTrue(subject.contains("Spring Gala"),
                    notification + " must name the event in its subject: " + subject);
            assertFalse(subject.contains("%s"), notification + " left an unfilled placeholder");
        }
    }

    @Test
    void everyNotice_hasCompleteCopy() {
        for (OrderNotification notification : OrderNotification.values()) {
            assertFalse(notification.getBadge().isBlank(), notification + " has no badge");
            assertFalse(notification.getHeadline().isBlank(), notification + " has no headline");
            assertFalse(notification.getMessage().isBlank(), notification + " has no message");
            assertFalse(notification.getFollowUp().isBlank(), notification + " has no follow-up");
            assertTrue(notification.getAccentColor().matches("#[0-9a-f]{6}"),
                    notification + " needs a 6-digit hex accent for the email's inline styles");
        }
    }
}
