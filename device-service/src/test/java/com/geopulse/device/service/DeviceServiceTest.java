package com.geopulse.device.service;

import com.geopulse.common.error.ApiException;
import com.geopulse.device.domain.ConnectivityStatus;
import com.geopulse.device.domain.Device;
import com.geopulse.device.domain.DeviceStatus;
import com.geopulse.device.domain.DeviceType;
import com.geopulse.device.dto.DeviceDTOs.CreateDeviceRequest;
import com.geopulse.device.dto.DeviceDTOs.DeviceResponse;
import com.geopulse.device.dto.DeviceDTOs.UpdateDeviceRequest;
import com.geopulse.device.repository.DeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private SetOperations<String, String> setOps;

    private DeviceService service;

    @BeforeEach
    void setUp() {
        service = new DeviceService(deviceRepository, redis);
    }

    @Test
    void createsDeviceSuccessfully() {
        when(redis.opsForSet()).thenReturn(setOps);
        when(deviceRepository.existsByExternalIdAndDeletedFalse("GPS-001")).thenReturn(false);
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> {
            Device d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        CreateDeviceRequest req = new CreateDeviceRequest("GPS-001", "Delivery Van", DeviceType.VEHICLE, "mem-1");
        DeviceResponse resp = service.create(req);

        assertThat(resp).isNotNull();
        assertThat(resp.externalId()).isEqualTo("GPS-001");
        assertThat(resp.name()).isEqualTo("Delivery Van");
        assertThat(resp.status()).isEqualTo(DeviceStatus.ACTIVE);
        assertThat(resp.connectivity()).isEqualTo(ConnectivityStatus.OFFLINE);
        verify(setOps).add(DeviceService.REDIS_REGISTERED_SET, "GPS-001");
    }

    @Test
    void throwsConflictOnDuplicateExternalId() {
        when(deviceRepository.existsByExternalIdAndDeletedFalse("GPS-001")).thenReturn(true);

        CreateDeviceRequest req = new CreateDeviceRequest("GPS-001", "Van", DeviceType.VEHICLE, "mem-1");
        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void softDeletesDeviceAndEvictsFromRedis() {
        when(redis.opsForSet()).thenReturn(setOps);
        UUID id = UUID.randomUUID();
        Device d = new Device();
        d.setId(id);
        d.setExternalId("GPS-001");
        d.setDeleted(false);
        d.setStatus(DeviceStatus.ACTIVE);

        when(deviceRepository.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(d));
        when(deviceRepository.save(any())).thenReturn(d);

        service.delete(id);

        assertThat(d.isDeleted()).isTrue();
        assertThat(d.getStatus()).isEqualTo(DeviceStatus.INACTIVE);
        verify(setOps).remove(DeviceService.REDIS_REGISTERED_SET, "GPS-001");
    }

    @Test
    void updatesDeviceSuccessfully() {
        UUID id = UUID.randomUUID();
        Device d = new Device();
        d.setId(id);
        d.setExternalId("GPS-001");
        d.setName("Old Name");
        d.setType(DeviceType.VEHICLE);
        d.setStatus(DeviceStatus.ACTIVE);

        when(deviceRepository.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(d));
        when(deviceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UpdateDeviceRequest req = new UpdateDeviceRequest("New Name", DeviceType.ASSET_TAG, DeviceStatus.LOST, "mem-2");
        DeviceResponse resp = service.update(id, req);

        assertThat(resp.name()).isEqualTo("New Name");
        assertThat(resp.type()).isEqualTo(DeviceType.ASSET_TAG);
        assertThat(resp.status()).isEqualTo(DeviceStatus.LOST);
    }
}
