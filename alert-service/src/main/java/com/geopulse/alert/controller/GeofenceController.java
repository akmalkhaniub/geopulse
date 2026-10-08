package com.geopulse.alert.controller;

import com.geopulse.alert.domain.Geofence;
import com.geopulse.alert.dto.AlertDTOs.GeofenceRequest;
import com.geopulse.alert.dto.AlertDTOs.GeofenceResponse;
import com.geopulse.alert.service.AlertService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/geofences")
public class GeofenceController {

    private final AlertService alertService;

    public GeofenceController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('viewer','operator','admin')")
    public List<GeofenceResponse> listGeofences() {
        return alertService.listGeofences().stream()
                .map(GeofenceResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('viewer','operator','admin')")
    public GeofenceResponse getGeofence(@PathVariable Long id) {
        return GeofenceResponse.from(alertService.getGeofence(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('operator','admin')")
    public ResponseEntity<GeofenceResponse> createGeofence(@Valid @RequestBody GeofenceRequest request) {
        Geofence created = alertService.createGeofence(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(GeofenceResponse.from(created));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGeofence(@PathVariable Long id) {
        alertService.deleteGeofence(id);
    }
}
