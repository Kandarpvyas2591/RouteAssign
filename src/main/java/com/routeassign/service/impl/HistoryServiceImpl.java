package com.routeassign.service.impl;

import com.routeassign.domain.entity.*;
import com.routeassign.domain.enums.HistoryStatus;
import com.routeassign.dto.request.RatingRequest;
import com.routeassign.dto.response.HistoryDeliveryPartnerResponse;
import com.routeassign.dto.response.HistoryVendorResponse;
import com.routeassign.exception.BadRequestException;
import com.routeassign.exception.ResourceNotFoundException;
import com.routeassign.repository.*;
import com.routeassign.service.HistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.OptionalDouble;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryServiceImpl implements HistoryService {

    private final HistoryDeliveryPartnerRepository historyDeliveryPartnerRepository;
    private final HistoryVendorRepository          historyVendorRepository;
    private final RatingRepository                 ratingRepository;
    private final UserDetailsRepository            userDetailsRepository;
    private final VendorDetailsRepository          vendorDetailsRepository;

    // ── Delivery partner history ──────────────────────────────────────────────

    @Override
    public List<HistoryDeliveryPartnerResponse> getPartnerHistory(Long partnerId) {
        return historyDeliveryPartnerRepository.findAllByDeliveryPartner_Id(partnerId)
                .stream().map(this::toPartnerHistoryResponse).toList();
    }

    @Override
    public HistoryDeliveryPartnerResponse getPartnerHistoryById(Long historyId) {
        return toPartnerHistoryResponse(findPartnerHistoryById(historyId));
    }

    @Override
    @Transactional
    public HistoryDeliveryPartnerResponse rateDelivery(Long historyId, RatingRequest request) {
        HistoryDeliveryPartner record = findPartnerHistoryById(historyId);

        // Only completed deliveries can be rated
        if (record.getStatus() != HistoryStatus.COMPLETED) {
            throw new BadRequestException(
                    "Only completed deliveries can be rated. Current status: " + record.getStatus());
        }
        if (record.getRating() != null) {
            throw new BadRequestException("This delivery has already been rated.");
        }

        // Persist the rating
        Rating rating = ratingRepository.save(Rating.builder().rating(request.getRating()).build());
        record.setRating(rating);
        historyDeliveryPartnerRepository.save(record);

        // Recalculate the partner's aggregated rating (average of all rated deliveries)
        UserDetails partner = record.getDeliveryPartner();
        double avgRating = computeAveragePartnerRating(partner.getId());
        partner.setRating(avgRating);
        userDetailsRepository.save(partner);

        log.info("Rated delivery historyId={} partnerId={} rating={} newAvg={}",
                historyId, partner.getId(), request.getRating(), avgRating);

        return toPartnerHistoryResponse(record);
    }

    // ── Vendor history ────────────────────────────────────────────────────────

    @Override
    public List<HistoryVendorResponse> getVendorHistory(Long vendorId) {
        return historyVendorRepository.findAllByVendor_Id(vendorId)
                .stream().map(this::toVendorHistoryResponse).toList();
    }

    @Override
    public HistoryVendorResponse getVendorHistoryById(Long historyId) {
        return toVendorHistoryResponse(findVendorHistoryById(historyId));
    }

    @Override
    @Transactional
    public HistoryVendorResponse rateVendor(Long historyId, RatingRequest request) {
        HistoryVendor record = findVendorHistoryById(historyId);

        if (record.getStatus() != HistoryStatus.COMPLETED) {
            throw new BadRequestException(
                    "Only completed orders can be rated. Current status: " + record.getStatus());
        }
        if (record.getRating() != null) {
            throw new BadRequestException("This vendor order has already been rated.");
        }

        // Persist the rating
        Rating rating = ratingRepository.save(Rating.builder().rating(request.getRating()).build());
        record.setRating(rating);
        historyVendorRepository.save(record);

        // Recalculate and persist vendor's aggregated rating
        VendorDetails vendor    = record.getVendor();
        double        avgRating = computeAverageVendorRating(vendor.getId());

        // Store aggregated rating as a new Rating entity linked to VendorDetails
        Rating aggregated = ratingRepository.save(
                Rating.builder().rating(avgRating).build());
        vendor.setRating(aggregated);
        vendorDetailsRepository.save(vendor);

        log.info("Rated vendor historyId={} vendorId={} rating={} newAvg={}",
                historyId, vendor.getId(), request.getRating(), avgRating);

        return toVendorHistoryResponse(record);
    }

    // ── Rating aggregation helpers ────────────────────────────────────────────

    /**
     * Computes the average of all ratings given to a delivery partner
     * across their completed history records.
     */
    private double computeAveragePartnerRating(Long partnerId) {
        List<HistoryDeliveryPartner> rated =
                historyDeliveryPartnerRepository.findAllByDeliveryPartner_IdAndStatus(
                        partnerId, HistoryStatus.COMPLETED);

        OptionalDouble avg = rated.stream()
                .filter(h -> h.getRating() != null)
                .mapToDouble(h -> h.getRating().getRating())
                .average();

        return avg.orElse(0.0);
    }

    /**
     * Computes the average of all ratings given to a vendor
     * across their completed history records.
     */
    private double computeAverageVendorRating(Long vendorId) {
        List<HistoryVendor> rated =
                historyVendorRepository.findAllByVendor_IdAndStatus(vendorId, HistoryStatus.COMPLETED);

        OptionalDouble avg = rated.stream()
                .filter(h -> h.getRating() != null)
                .mapToDouble(h -> h.getRating().getRating())
                .average();

        return avg.orElse(0.0);
    }

    // ── Finder helpers ────────────────────────────────────────────────────────

    private HistoryDeliveryPartner findPartnerHistoryById(Long historyId) {
        return historyDeliveryPartnerRepository.findById(historyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "HistoryDeliveryPartner", "id", historyId));
    }

    private HistoryVendor findVendorHistoryById(Long historyId) {
        return historyVendorRepository.findById(historyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "HistoryVendor", "id", historyId));
    }

    // ── Response mappers ──────────────────────────────────────────────────────

    private HistoryDeliveryPartnerResponse toPartnerHistoryResponse(HistoryDeliveryPartner h) {
        Double ratingValue = (h.getRating() != null) ? h.getRating().getRating() : null;
        return HistoryDeliveryPartnerResponse.builder()
                .id(h.getId())
                .orderId(h.getOrder().getOrderId())
                .deliveryPartnerId(h.getDeliveryPartner().getId())
                .deliveryPartnerName(h.getDeliveryPartner().getAuth().getUsername())
                .rating(ratingValue)
                .status(h.getStatus())
                .completedAt(h.getCompletedAt())
                .isActive(h.getIsActive())
                .build();
    }

    private HistoryVendorResponse toVendorHistoryResponse(HistoryVendor h) {
        Double ratingValue = (h.getRating() != null) ? h.getRating().getRating() : null;
        return HistoryVendorResponse.builder()
                .id(h.getId())
                .orderId(h.getOrder().getOrderId())
                .vendorId(h.getVendor().getId())
                .vendorName(h.getVendor().getAuth().getUsername())
                .rating(ratingValue)
                .status(h.getStatus())
                .isActive(h.getIsActive())
                .build();
    }
}
