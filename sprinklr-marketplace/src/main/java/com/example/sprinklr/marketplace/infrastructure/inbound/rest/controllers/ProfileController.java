package com.example.sprinklr.marketplace.infrastructure.inbound.rest.controllers;

import com.example.sprinklr.marketplace.application.service.ProfileService;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.profile.ProfileResponse;
import com.example.sprinklr.marketplace.infrastructure.security.AuthenticatedUserResolver;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService profileService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    public ProfileController(ProfileService profileService, AuthenticatedUserResolver authenticatedUserResolver) {
        this.profileService = profileService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ProfileResponse getProfile() {
        String userId = authenticatedUserResolver.requireUserId();
        return ProfileResponse.from(profileService.getProfile(userId));
    }
}
