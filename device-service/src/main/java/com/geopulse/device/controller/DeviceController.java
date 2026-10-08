package com.geopulse.device.controller;

import com.geopulse.device.domain.DeviceStatus;
import com.geopulse.device.domain.DeviceType;
import com.geopulse.device.dto.DeviceDTOs.CreateDeviceRequest;
import com.geopulse.device.dto.DeviceDTOs.DeviceLiveStatus;
import com.geopulse.device.dto.DeviceDTOs.DeviceResponse;
import com.geopulse.device.dto.DeviceDTOs.UpdateDeviceRequest;
import com.geopulse.device.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('admin')")
    public DeviceResponse create(@Valid @RequestBody CreateDeviceRequest req) {
        return deviceService.create(req);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('viewer','operator','admin')")
    public Page<DeviceResponse> list(
            @RequestParam(required = false) DeviceStatus status,
            @RequestParam(required = false) DeviceType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return deviceService.list(status, type, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('viewer','operator','admin')")
    public DeviceResponse get(@PathVariable UUID id) {
        return deviceService.get(id);
    }

    @GetMapping("/by-external/{extId}")
    @PreAuthorize("hasAnyRole('viewer','operator','admin')")
    public DeviceResponse getByExternalId(@PathVariable String extId) {
        return deviceService.getByExternalId(extId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    public DeviceResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDeviceRequest req) {
        return deviceService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('admin')")
    public void delete(@PathVariable UUID id) {
        deviceService.delete(id);
    }

    @GetMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('viewer','operator','admin')")
    public DeviceLiveStatus getStatus(@PathVariable UUID id) {
        return deviceService.getLiveStatus(id);
    }
}
