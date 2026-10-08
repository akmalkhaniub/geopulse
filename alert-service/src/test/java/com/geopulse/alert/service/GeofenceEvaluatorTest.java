package com.geopulse.alert.service;

import tools.jackson.databind.json.JsonMapper;
import com.geopulse.alert.domain.Geofence;
import com.geopulse.alert.domain.GeofenceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeofenceEvaluatorTest {

    private GeofenceEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new GeofenceEvaluator(JsonMapper.builder().build());
    }

    @Test
    void pointInPolygon_insideAndOutside() {
        // Polygon vertices in lat, lng
        List<GeofenceEvaluator.Point> polygon = List.of(
                new GeofenceEvaluator.Point(0.0, 0.0),
                new GeofenceEvaluator.Point(10.0, 0.0),
                new GeofenceEvaluator.Point(10.0, 10.0),
                new GeofenceEvaluator.Point(0.0, 10.0)
        );

        assertThat(GeofenceEvaluator.isPointInPolygon(5.0, 5.0, polygon)).isTrue();
        assertThat(GeofenceEvaluator.isPointInPolygon(15.0, 5.0, polygon)).isFalse();
        assertThat(GeofenceEvaluator.isPointInPolygon(-2.0, 5.0, polygon)).isFalse();
    }

    @Test
    void circularGeofence_distanceEvaluation() {
        double centerLat = 37.7749;
        double centerLng = -122.4194;
        double radiusMeters = 1000.0;

        Geofence safeZone = Geofence.builder()
                .name("SF Base Safe Zone")
                .type(GeofenceType.SAFE_ZONE)
                .centerLat(centerLat)
                .centerLng(centerLng)
                .radiusMeters(radiusMeters)
                .build();

        // Very close point (~36m away)
        assertThat(evaluator.isInside(safeZone, 37.7750, -122.4190)).isTrue();
        // For SAFE_ZONE, inside means NOT breached
        assertThat(evaluator.isBreached(safeZone, 37.7750, -122.4190)).isFalse();

        // Far away point (~3.2km away)
        assertThat(evaluator.isInside(safeZone, 37.8000, -122.4000)).isFalse();
        // For SAFE_ZONE, outside means breached!
        assertThat(evaluator.isBreached(safeZone, 37.8000, -122.4000)).isTrue();
    }

    @Test
    void dangerZone_polygonBreach() {
        // GeoJSON polygon coordinates: [[[lng, lat], ...]]
        String geoJson = "{\"type\":\"Polygon\",\"coordinates\":[[[0.0, 0.0], [10.0, 0.0], [10.0, 10.0], [0.0, 10.0], [0.0, 0.0]]]}";
        Geofence dangerZone = Geofence.builder()
                .name("No Entry Zone")
                .type(GeofenceType.DANGER_ZONE)
                .polygonGeoJson(geoJson)
                .build();

        // Inside danger zone -> breached
        assertThat(evaluator.isBreached(dangerZone, 5.0, 5.0)).isTrue();
        // Outside danger zone -> not breached
        assertThat(evaluator.isBreached(dangerZone, 15.0, 15.0)).isFalse();
    }
}
