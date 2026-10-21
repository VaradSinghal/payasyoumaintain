package com.paymu.maintenance.service;

import com.paymu.maintenance.client.VehicleRegistrationResponse;
import com.paymu.maintenance.model.ConsistencyCheck;
import com.paymu.maintenance.model.MaintenanceEvent;
import com.paymu.maintenance.model.ServiceRecordRequest;
import com.paymu.maintenance.model.VerificationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ConsistencyCheckService {

    private final Clock clock;
    private final int maxKmPerDay;
    private final int maxPerDay;

    public ConsistencyCheckService(
            Clock clock,
            @Value("${maintenance.self-record.max-km-per-day:1000}") int maxKmPerDay,
            @Value("${maintenance.self-record.max-per-day:5}") int maxPerDay) {
        this.clock = clock;
        this.maxKmPerDay = maxKmPerDay;
        this.maxPerDay = maxPerDay;
    }

    public VerificationResult evaluate(
            ServiceRecordRequest request,
            VehicleRegistrationResponse registration,
            List<MaintenanceEvent> storedEvents) {

        List<ConsistencyCheck> checks = new ArrayList<>(6);

        // 1. date_not_future (hard)
        LocalDate today = LocalDate.now(clock);
        boolean dateNotFuturePassed = !request.serviceDate().isAfter(today);
        String dateNotFutureMsg = dateNotFuturePassed
                ? "Service date is not in the future."
                : "Service date " + request.serviceDate() + " is in the future (today is " + today + ").";
        checks.add(new ConsistencyCheck("date_not_future", dateNotFuturePassed, dateNotFutureMsg));

        // 2. date_after_registration (hard)
        LocalDate regDate = registration != null ? registration.registrationDate() : null;
        boolean dateAfterRegPassed;
        String dateAfterRegMsg;
        if (regDate == null) {
            dateAfterRegPassed = true;
            dateAfterRegMsg = "No registration date available to compare.";
        } else if (request.serviceDate().isBefore(regDate)) {
            dateAfterRegPassed = false;
            dateAfterRegMsg = "Service date " + request.serviceDate() + " is before registration date " + regDate + ".";
        } else {
            dateAfterRegPassed = true;
            dateAfterRegMsg = "Service date is on or after registration date (" + regDate + ").";
        }
        checks.add(new ConsistencyCheck("date_after_registration", dateAfterRegPassed, dateAfterRegMsg));

        // Filter stored events to accepted self-reported and all trusted readings
        List<MaintenanceEvent> baselineReadings = storedEvents.stream()
                .filter(e -> isTrustedOrAccepted(e))
                .sorted(Comparator.comparing((MaintenanceEvent e) -> LocalDate.parse(e.serviceDate()))
                        .thenComparingInt(MaintenanceEvent::odometerKm))
                .toList();

        MaintenanceEvent prevReading = null;
        MaintenanceEvent nextReading = null;

        for (MaintenanceEvent e : baselineReadings) {
            LocalDate d = LocalDate.parse(e.serviceDate());
            if (!d.isAfter(request.serviceDate())) {
                prevReading = e;
            } else {
                if (nextReading == null) {
                    nextReading = e;
                }
            }
        }

        // 3. odometer_monotonic (soft)
        boolean monotonicPassed;
        String monotonicMsg;
        if (prevReading == null && nextReading == null) {
            monotonicPassed = true;
            monotonicMsg = "No neighbouring readings to compare for monotonicity.";
        } else {
            boolean prevOk = (prevReading == null) || (request.odometerKm() >= prevReading.odometerKm());
            boolean nextOk = (nextReading == null) || (request.odometerKm() <= nextReading.odometerKm());
            monotonicPassed = prevOk && nextOk;
            if (monotonicPassed) {
                monotonicMsg = "Odometer reading is monotonic with neighbouring readings.";
            } else {
                monotonicMsg = "Odometer reading " + request.odometerKm() + " violates monotonicity (expected >= "
                        + (prevReading != null ? prevReading.odometerKm() : 0)
                        + (nextReading != null ? " and <= " + nextReading.odometerKm() : "") + ").";
            }
        }
        checks.add(new ConsistencyCheck("odometer_monotonic", monotonicPassed, monotonicMsg));

        // 4. odometer_rate_plausible (soft)
        boolean ratePassed;
        String rateMsg;
        if (prevReading == null && nextReading == null) {
            ratePassed = true;
            rateMsg = "No neighbouring readings to compare for rate plausibility.";
        } else {
            boolean prevRateOk = true;
            if (prevReading != null) {
                long days = Math.max(1, Math.abs(ChronoUnit.DAYS.between(LocalDate.parse(prevReading.serviceDate()), request.serviceDate())));
                long deltaKm = Math.abs(request.odometerKm() - prevReading.odometerKm());
                double rate = (double) deltaKm / days;
                prevRateOk = (rate <= maxKmPerDay);
            }
            boolean nextRateOk = true;
            if (nextReading != null) {
                long days = Math.max(1, Math.abs(ChronoUnit.DAYS.between(request.serviceDate(), LocalDate.parse(nextReading.serviceDate()))));
                long deltaKm = Math.abs(nextReading.odometerKm() - request.odometerKm());
                double rate = (double) deltaKm / days;
                nextRateOk = (rate <= maxKmPerDay);
            }
            ratePassed = prevRateOk && nextRateOk;
            if (ratePassed) {
                rateMsg = "Odometer rate is plausible (<= " + maxKmPerDay + " km/day).";
            } else {
                rateMsg = "Odometer rate of change exceeds maximum allowed " + maxKmPerDay + " km/day against neighbouring reading.";
            }
        }
        checks.add(new ConsistencyCheck("odometer_rate_plausible", ratePassed, rateMsg));

        // 5. not_duplicate (soft)
        boolean duplicateFound = storedEvents.stream().anyMatch(e -> {
            boolean sameEvent = e.serviceDate().equals(request.serviceDate().toString())
                    && e.serviceType().equalsIgnoreCase(request.serviceType())
                    && e.odometerKm() == request.odometerKm();
            boolean sameReceipt = request.receiptSha256() != null
                    && !request.receiptSha256().isBlank()
                    && request.receiptSha256().equalsIgnoreCase(e.receiptSha256());
            return sameEvent || sameReceipt;
        });
        boolean notDuplicatePassed = !duplicateFound;
        String notDuplicateMsg;
        if (notDuplicatePassed) {
            notDuplicateMsg = storedEvents.isEmpty()
                    ? "No stored records found to compare for duplicates."
                    : "No duplicate service record found.";
        } else {
            notDuplicateMsg = "Duplicate service record detected for vehicle with matching date/type/odometer or receipt SHA-256.";
        }
        checks.add(new ConsistencyCheck("not_duplicate", notDuplicatePassed, notDuplicateMsg));

        // 6. daily_limit (soft)
        long todayCount = storedEvents.stream()
                .filter(e -> "self_upload".equalsIgnoreCase(e.source()))
                .filter(e -> e.recordedAt() != null && e.recordedAt().atZone(clock.getZone()).toLocalDate().equals(today))
                .count();
        boolean dailyLimitPassed = todayCount < maxPerDay;
        String dailyLimitMsg = dailyLimitPassed
                ? "Daily self-upload limit not exceeded (" + todayCount + " of " + maxPerDay + " records stored today)."
                : "Daily self-upload limit exceeded (" + todayCount + " of " + maxPerDay + " records stored today).";
        checks.add(new ConsistencyCheck("daily_limit", dailyLimitPassed, dailyLimitMsg));

        // Status mapping:
        // Any hard failure -> status rejected
        // Else any soft failure -> flagged
        // Else -> accepted
        boolean hardFailure = !dateNotFuturePassed || !dateAfterRegPassed;
        boolean softFailure = !monotonicPassed || !ratePassed || !notDuplicatePassed || !dailyLimitPassed;

        String status;
        if (hardFailure) {
            status = "rejected";
        } else if (softFailure) {
            status = "flagged";
        } else {
            status = "accepted";
        }

        return new VerificationResult(status, checks);
    }

    private boolean isTrustedOrAccepted(MaintenanceEvent event) {
        String source = event.source();
        if ("oem_api".equalsIgnoreCase(source) || "inspection".equalsIgnoreCase(source)) {
            return true;
        }
        if ("self_upload".equalsIgnoreCase(source) && "accepted".equalsIgnoreCase(event.verificationStatus())) {
            return true;
        }
        return false;
    }
}
