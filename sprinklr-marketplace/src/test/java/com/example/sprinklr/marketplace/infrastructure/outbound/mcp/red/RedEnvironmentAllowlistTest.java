package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedEnvironmentAllowlistTest {

    private final RedEnvironmentAllowlist allowlist = new RedEnvironmentAllowlist();

    @Test
    void acceptsAllKnownEnvironments() {
        assertTrue(allowlist.isAllowed("prod"));
        assertTrue(allowlist.isAllowed("prod0"));
        assertTrue(allowlist.isAllowed("prod16"));
        assertTrue(allowlist.isAllowed("prod21"));
        assertTrue(allowlist.isAllowed("spr-uat"));
        assertTrue(allowlist.isAllowed("azrqa"));
        assertTrue(allowlist.isAllowed("qa6"));
        assertEquals(19, allowlist.allowedValues().size());
    }

    @Test
    void rejectsUnknownEnvironments() {
        assertFalse(allowlist.isAllowed("dev"));
        assertFalse(allowlist.isAllowed("production"));
        assertFalse(allowlist.isAllowed("prod7"));
        assertFalse(allowlist.isAllowed(""));
        assertFalse(allowlist.isAllowed(null));
    }
}
