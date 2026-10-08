package com.geopulse.alert.controller;

import com.geopulse.alert.domain.Alert;
import com.geopulse.alert.domain.AlertStatus;
import com.geopulse.alert.dto.AlertDTOs.AlertResponse;
import com.geopulse.alert.dto.AlertDTOs.PageResponse;
import com.geopulse.alert.service.AlertService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('viewer','operator','admin')")
    public PageResponse<AlertResponse> listAlerts(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 200), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Alert> result = alertService.getAlerts(deviceId, status, pageable);
        return PageResponse.from(result.map(AlertResponse::from));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('viewer','operator','admin')")
    public AlertResponse getAlert(@PathVariable Long id) {
        return AlertResponse.from(alertService.getAlert(id));
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('operator','admin')")
    public AlertResponse acknowledgeAlert(@PathVariable Long id,
                                          @AuthenticationPrincipal Jwt jwt,
                                          Principal principal) {
        String username = resolveUsername(jwt, principal);
        return AlertResponse.from(alertService.acknowledgeAlert(id, username));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('operator','admin')")
    public AlertResponse resolveAlert(@PathVariable Long id,
                                      @AuthenticationPrincipal Jwt jwt,
                                      Principal principal) {
        String username = resolveUsername(jwt, principal);
        return AlertResponse.from(alertService.resolveAlert(id, username));
    }

    private String resolveUsername(Jwt jwt, Principal principal) {
        if (jwt != null) {
            String preferredUsername = jwt.getClaimAsString("preferred_username");
            if (preferredUsername != null && !preferredUsername.isBlank()) {
                return preferredUsername;
            }
            if (jwt.getSubject() != null) {
                return jwt.getSubject();
            }
        }
        return principal != null ? principal.getName() : "anonymous";
    }
}
