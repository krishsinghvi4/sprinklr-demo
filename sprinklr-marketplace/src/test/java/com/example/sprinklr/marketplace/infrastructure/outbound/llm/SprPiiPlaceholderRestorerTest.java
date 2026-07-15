package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SprPiiPlaceholderRestorerTest {

    @Test
    void restoresIndexedEmailPlaceholderFromUserText() {
        String args = "{\"query\":\"assignee: !$SPR_PII_Email_Address@1$!\"}";
        String restored = SprPiiPlaceholderRestorer.restoreEmailPlaceholders(
                args,
                List.of("find tickets for krish.singhvi@sprinklr.com")
        );
        assertEquals("{\"query\":\"assignee: krish.singhvi@sprinklr.com\"}", restored);
        assertFalse(SprPiiPlaceholderRestorer.containsEmailPlaceholder(restored));
    }

    @Test
    void leavesTextUnchangedWhenNoPlaceholder() {
        String args = "{\"query\":\"assignee = currentUser()\"}";
        assertEquals(args, SprPiiPlaceholderRestorer.restoreEmailPlaceholders(
                args, List.of("krish.singhvi@sprinklr.com")));
    }

    @Test
    void mapsSecondPlaceholderToSecondEmail() {
        String text = "!$SPR_PII_Email_Address@2$!";
        String restored = SprPiiPlaceholderRestorer.restoreEmailPlaceholders(
                text,
                List.of("a@x.com and b@y.com")
        );
        assertEquals("b@y.com", restored);
        assertTrue(SprPiiPlaceholderRestorer.containsEmailPlaceholder("!$SPR_PII_Email_Address@1$!"));
    }
}
