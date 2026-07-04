package com.example.sprinklr.marketplace.application.service;

import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.RedQueryPreferencesRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RedQueryPreferencesValidatorTest {

    @Test
    void acceptsValidPreferences() {
        RedQueryPreferencesRequest request = new RedQueryPreferencesRequest(
                List.of("AUDIT_LOGS"),
                List.of(new RedQueryPreferencesRequest.MongoServerTypeRequest(
                        "PAID", List.of("adSet", "paidInitiative"))));

        var domain = RedQueryPreferencesValidator.toDomain(request);

        assertEquals(List.of("AUDIT_LOGS"), domain.elasticsearchServerTypes());
        assertEquals("PAID", domain.mongoServerTypes().get(0).serverType());
        assertEquals(List.of("adSet", "paidInitiative"), domain.mongoServerTypes().get(0).collectionNames());
    }

    @Test
    void rejectsDuplicateMongoServerTypes() {
        RedQueryPreferencesRequest request = new RedQueryPreferencesRequest(
                List.of(),
                List.of(
                        new RedQueryPreferencesRequest.MongoServerTypeRequest("PAID", List.of("adSet")),
                        new RedQueryPreferencesRequest.MongoServerTypeRequest("PAID", List.of("other"))));

        assertThrows(IllegalArgumentException.class, () -> RedQueryPreferencesValidator.toDomain(request));
    }

    @Test
    void rejectsBlankCollectionNames() {
        RedQueryPreferencesRequest request = new RedQueryPreferencesRequest(
                List.of(),
                List.of(new RedQueryPreferencesRequest.MongoServerTypeRequest("PAID", List.of(" "))));

        assertThrows(IllegalArgumentException.class, () -> RedQueryPreferencesValidator.toDomain(request));
    }
}
