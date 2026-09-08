package com.routeassign.service.algorithm;

/**
 * Algorithm 1 — Distance Calculation.
 *
 * Calculates geographical distance between two coordinate points
 * using the Haversine formula.
 */
public interface DistanceAlgorithmService {

    /**
     * Calculates the great-circle distance between two points on Earth.
     *
     * @param lat1 latitude  of point 1 (degrees)
     * @param lon1 longitude of point 1 (degrees)
     * @param lat2 latitude  of point 2 (degrees)
     * @param lon2 longitude of point 2 (degrees)
     * @return distance in kilometres
     */
    double calculateDistance(double lat1, double lon1, double lat2, double lon2);

    /**
     * Calculates the total assignment distance:
     *   Partner Home → Vendor  +  Vendor → Customer
     *
     * @param partnerLat  delivery partner home latitude
     * @param partnerLon  delivery partner home longitude
     * @param vendorLat   vendor latitude
     * @param vendorLon   vendor longitude
     * @param customerLat customer delivery latitude
     * @param customerLon customer delivery longitude
     * @return total distance in kilometres
     */
    double calculateTotalAssignmentDistance(
            double partnerLat, double partnerLon,
            double vendorLat,  double vendorLon,
            double customerLat, double customerLon);
}
