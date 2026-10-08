package com.geopulse.telemetry.controller;

import com.geopulse.telemetry.dto.TelemetryDTOs.PageResponse;
import com.geopulse.telemetry.dto.TelemetryDTOs.PositionRecord;
import com.geopulse.telemetry.service.TelemetryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {

    private final TelemetryService service;

    public TelemetryController(TelemetryService service) {
        this.service = service;
    }

    @GetMapping("/{deviceId}/latest")
    @PreAuthorize("hasAnyRole('viewer','operator','admin')")
    public PositionRecord getLatest(@PathVariable String deviceId) {
        return service.getLatest(deviceId);
    }

    @GetMapping("/{deviceId}/history")
    @PreAuthorize("hasAnyRole('viewer','operator','admin')")
    public PageResponse<PositionRecord> getHistory(
            @PathVariable String deviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 200), Sort.by(Sort.Direction.DESC, "recordedAt"));
        return PageResponse.from(service.getHistory(deviceId, from, to, pageable));
    }
}
