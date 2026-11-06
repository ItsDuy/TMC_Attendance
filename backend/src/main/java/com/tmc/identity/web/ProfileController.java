package com.tmc.identity.web;

import com.tmc.identity.api.ProfileService;
import com.tmc.security.api.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Who am I?" for the signed-in person. Lives in identity (not security) so security never depends
 * on identity; identity already depends on security's API. Thin adapter: the work is in ProfileService.
 */
@RestController
@RequestMapping("/api/me")
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ProfileService.Profile me(Authentication authentication) {
        return profiles.of(SecurityUtils.user(authentication));
    }
}
