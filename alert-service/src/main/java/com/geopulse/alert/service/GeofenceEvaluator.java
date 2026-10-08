package com.geopulse.alert.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.geopulse.alert.domain.Geofence;
import com.geopulse.alert.domain.GeofenceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class GeofenceEvaluator {

    private static final Logger log = LoggerFactory.getLogger(GeofenceEvaluator.class);
    private static final double EARTH_RADIUS_METERS = 6371000.0;
    private final ObjectMapper objectMapper;

    public record Point(double lat, double lng) {}

    public GeofenceEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Determines whether the given coordinate constitutes a breach for this geofence.
     * - DANGER_ZONE: breached if point is inside.
     * - SAFE_ZONE: breached if point is outside.
     */
    public boolean isBreached(Geofence geofence, double lat, double lng) {
        boolean inside = isInside(geofence, lat, lng);
        if (geofence.getType() == GeofenceType.DANGER_ZONE) {
            return inside;
        } else if (geofence.getType() == GeofenceType.SAFE_ZONE) {
            return !inside;
        }
        return false;
    }

    public boolean isInside(Geofence geofence, double lat, double lng) {
        if (geofence.getRadiusMeters() != null && geofence.getCenterLat() != null && geofence.getCenterLng() != null) {
            double distance = haversineDistanceMeters(geofence.getCenterLat(), geofence.getCenterLng(), lat, lng);
            return distance <= geofence.getRadiusMeters();
        }

        if (geofence.getPolygonGeoJson() != null && !geofence.getPolygonGeoJson().isBlank()) {
            List<Point> vertices = parsePolygon(geofence.getPolygonGeoJson());
            return isPointInPolygon(lat, lng, vertices);
        }

        return false;
    }

    public static double haversineDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Standard Ray-Casting algorithm for point-in-polygon check.
     */
    public static boolean isPointInPolygon(double testLat, double testLng, List<Point> polygon) {
        if (polygon == null || polygon.size() < 3) {
            return false;
        }

        boolean inside = false;
        int n = polygon.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = polygon.get(i).lat();
            double yi = polygon.get(i).lng();
            double xj = polygon.get(j).lat();
            double yj = polygon.get(j).lng();

            // Point on vertex
            if (Double.compare(xi, testLat) == 0 && Double.compare(yi, testLng) == 0) {
                return true;
            }

            boolean intersect = ((yi > testLng) != (yj > testLng))
                    && (testLat < (xj - xi) * (testLng - yi) / (yj - yi) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    public List<Point> parsePolygon(String geoJson) {
        List<Point> points = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(geoJson);
            JsonNode coordsNode = root;
            if (root.has("coordinates")) {
                coordsNode = root.get("coordinates");
            }
            // If coordinates is 3D array: [[[lng, lat], [lng, lat], ...]] (Polygon standard)
            if (coordsNode.isArray() && coordsNode.size() > 0 && coordsNode.get(0).isArray() && coordsNode.get(0).size() > 0 && coordsNode.get(0).get(0).isArray()) {
                coordsNode = coordsNode.get(0);
            }
            if (coordsNode.isArray()) {
                for (JsonNode coord : coordsNode) {
                    if (coord.isArray() && coord.size() >= 2) {
                        double lng = coord.get(0).asDouble();
                        double lat = coord.get(1).asDouble();
                        points.add(new Point(lat, lng));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse geofence polygon JSON: {}", e.getMessage());
        }
        return points;
    }
}
