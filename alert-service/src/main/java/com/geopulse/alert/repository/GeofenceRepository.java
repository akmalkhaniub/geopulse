package com.geopulse.alert.repository;

import com.geopulse.alert.domain.Geofence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GeofenceRepository extends JpaRepository<Geofence, Long> {

    List<Geofence> findByActiveTrue();
}
