package com.routeassign.service.algorithm.impl;

import com.routeassign.config.AppConstants;
import com.routeassign.service.algorithm.DistanceAlgorithmService;
import org.springframework.stereotype.Service;

/**
 * Algorithm 1 — Haversine distance calculation.
 *
 * The Haversine formula computes the great-circle distance between two points
 * on a sphere (Earth) given their latitudes and longitudes in degrees.
 *
 *   a = sin²(Δlat/2) + cos(lat1) * cos(lat2) * sin²(Δlon/2)
 *   c = 2 * atan2(√a, √(1−a))
 *   d = R * c          where R = 6371 km
 */
@Service
public class DistanceAlgorithmServiceImpl implements DistanceAlgorithmService {

    @Override
    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.pow(Math.sin(deltaLon / 2), 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return AppConstants.EARTH_RADIUS_KM * c;
    }

    @Override
    public double calculateTotalAssignmentDistance(
            double partnerLat, double partnerLon,
            double vendorLat,  double vendorLon,
            double customerLat, double customerLon) {

        double partnerToVendor  = calculateDistance(partnerLat, partnerLon, vendorLat,  vendorLon);
        double vendorToCustomer = calculateDistance(vendorLat,  vendorLon,  customerLat, customerLon);
        return partnerToVendor + vendorToCustomer;
    }
}
