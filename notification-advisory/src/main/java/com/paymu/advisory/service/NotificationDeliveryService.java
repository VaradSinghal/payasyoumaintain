package com.paymu.advisory.service;

import com.paymu.advisory.model.AdvisoryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Stub delivery channel — logs advisory messages to the console as a stand-in
 * for real SMS / push-notification infrastructure.
 *
 * <p>In Phase 4 this will be replaced by a real implementation backed by
 * Firebase Cloud Messaging (push) or AWS SNS (SMS), following the same
 * interface contract.</p>
 */
@Service
public class NotificationDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);

    /**
     * "Delivers" each advisory message for the given vehicle.
     * Currently logs at INFO level; Phase 4 will push to FCM / SNS.
     *
     * @param vehicleId  recipient vehicle / owner identifier
     * @param advisories list produced by {@link AdvisoryGeneratorService}
     */
    public void deliver(String vehicleId, List<AdvisoryMessage> advisories) {
        if (advisories.isEmpty()) {
            log.info("[DELIVERY STUB] vehicle={} — No advisories to deliver.", vehicleId);
            return;
        }

        log.info("[DELIVERY STUB] vehicle={} — Delivering {} advisory message(s):",
                vehicleId, advisories.size());

        advisories.forEach(msg ->
                log.info("[DELIVERY STUB]  [{}] {} — {}",
                        msg.priority(), msg.title(), msg.message())
        );

        // Phase 4 hook: iterate advisories and call FCM / SNS per message priority
    }
}
