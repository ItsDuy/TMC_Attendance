package com.tmc.reporting.web;

import com.tmc.identity.api.AccessScopeService;
import com.tmc.reporting.service.EvacuationService;
import com.tmc.reporting.service.LiveSiteService;
import com.tmc.security.api.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Live site map for the Live attendance page. What each viewer gets (all, own team, head counts
 * only) comes from {@code AccessScopeService}; names are removed on the server, not in the browser.
 */
@RestController
@RequestMapping("/api/live")
public class LiveSiteController {

    private final LiveSiteService liveSites;
    private final EvacuationService evacuations;
    private final AccessScopeService scopes;

    public LiveSiteController(LiveSiteService liveSites, EvacuationService evacuations, AccessScopeService scopes) {
        this.liveSites = liveSites;
        this.evacuations = evacuations;
        this.scopes = scopes;
    }

    /** Fire drill: everyone still inside, per factory, printable. Needs names, so not for executives. */
    @GetMapping("/evacuation")
    @PreAuthorize("hasAnyRole('SECURITY','HR','ADMIN')")
    public EvacuationService.Evacuation evacuation() {
        return evacuations.evacuation();
    }

    @GetMapping("/sites")
    @PreAuthorize("hasAnyRole('SUPERVISOR','SECURITY','ADMIN','HR','EXECUTIVE')")
    public LiveSiteService.LiveSites sites(Authentication authentication) {
        return liveSites.snapshot(scopes.scopeFor(SecurityUtils.user(authentication)));
    }
}
