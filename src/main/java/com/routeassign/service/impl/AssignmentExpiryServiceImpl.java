package com.routeassign.service.impl;

import com.routeassign.domain.entity.DeliveryAssignment;
import com.routeassign.domain.enums.AssignmentRuleKey;
import com.routeassign.repository.DeliveryAssignmentRepository;
import com.routeassign.service.AssignmentExpiryService;
import com.routeassign.service.AssignmentLifecycleService;
import com.routeassign.service.AssignmentRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Implements {@link AssignmentExpiryService}.
 *
 * Runs on a fixed delay (default: every 60 seconds via {@code @Scheduled}).
 * The delay is intentionally longer than the configured acceptance timeout so
 * expiry is detected within one scheduling cycle of the deadline passing.
 *
 * Each expired assignment is processed one at a time in its own transaction
 * scope (via {@link AssignmentLifecycleService#expireAssignment(Long)}) so
 * that a failure on one record does not roll back the others.
 *
 * <b>Phase 7 scope</b>
 * This implementation runs within a single application instance.
 * A distributed lock (Redis or DB advisory lock) is NOT required at this stage.
 * When RouteAssign scales to multiple instances, a distributed lock must be
 * added around the query + expiry loop to prevent duplicate expiry processing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentExpiryServiceImpl implements AssignmentExpiryService {

    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final AssignmentRuleService        assignmentRuleService;
    private final AssignmentLifecycleService   assignmentLifecycleService;

    /**
     * Fixed-delay scheduled job — fires every 60 seconds after the previous
     * execution completes.  Uses fixed delay (not fixed rate) so that if
     * processing a large batch of expired assignments takes time, the next
     * scan does not start until the current one finishes.
     *
     * Requires {@code @EnableScheduling} on a {@code @Configuration} class.
     */
    @Scheduled(fixedDelayString = "${routeassign.expiry.check-interval-ms:60000}")
    @Override
    public int detectAndExpire() {
        int timeoutMinutes = assignmentRuleService
                .getIntegerRule(AssignmentRuleKey.ASSIGNMENT_ACCEPTANCE_TIMEOUT_MINUTES);

        LocalDateTime threshold = LocalDateTime.now().minusMinutes(timeoutMinutes);

        List<DeliveryAssignment> expired =
                deliveryAssignmentRepository.findExpiredAssignments(threshold);

        if (expired.isEmpty()) {
            return 0;
        }

        log.info("Expiry scan: found {} assignment(s) past timeout of {} minutes",
                expired.size(), timeoutMinutes);

        int processed = 0;
        for (DeliveryAssignment da : expired) {
            try {
                assignmentLifecycleService.expireAssignment(da.getId());
                processed++;
                log.info("Expired assignment id={} orderId={} partnerId={}",
                        da.getId(), da.getOrder().getOrderId(),
                        da.getDeliveryPartner().getId());
            } catch (Exception ex) {
                // Log and continue — don't let one bad record block the rest
                log.error("Failed to expire assignment id={}: {}",
                        da.getId(), ex.getMessage(), ex);
            }
        }

        log.info("Expiry scan complete: {}/{} assignments expired", processed, expired.size());
        return processed;
    }
}
