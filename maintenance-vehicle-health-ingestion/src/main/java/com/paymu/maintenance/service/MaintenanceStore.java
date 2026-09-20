package com.paymu.maintenance.service;

import com.paymu.maintenance.model.MaintenanceEvent;
import com.paymu.maintenance.model.ServiceTimeline;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for maintenance events, indexed by vehicle ID.
 * Provides a per-vehicle service timeline view ordered by service date.
 *
 * <p>Backed by {@link ConcurrentHashMap} — suitable for development and demo.
 * Will be replaced by a real persistence layer in Phase 4.</p>
 */
@Service
public class MaintenanceStore {

    private final Map<String, List<MaintenanceEvent>> eventsByVehicle = new ConcurrentHashMap<>();

    /**
     * Stores a list of maintenance events, grouping by vehicle ID.
     */
    public void storeEvents(List<MaintenanceEvent> events) {
        for (MaintenanceEvent event : events) {
            eventsByVehicle
                    .computeIfAbsent(event.vehicleId(), k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(event);
        }
    }

    /**
     * Stores a single maintenance event.
     */
    public void storeEvent(MaintenanceEvent event) {
        storeEvents(List.of(event));
    }

    /**
     * Returns the full service timeline for a vehicle, ordered by service date
     * (oldest first).  Returns {@code null} if no events exist for this vehicle.
     */
    public ServiceTimeline getTimeline(String vehicleId) {
        List<MaintenanceEvent> events = eventsByVehicle.get(vehicleId);
        if (events == null || events.isEmpty()) {
            return null;
        }

        List<MaintenanceEvent> sorted;
        synchronized (events) {
            sorted = events.stream()
                    .sorted(Comparator.comparing(MaintenanceEvent::serviceDate))
                    .toList();
        }

        MaintenanceEvent latest = sorted.getLast();

        return new ServiceTimeline(
                vehicleId,
                sorted.size(),
                latest.serviceDate(),
                latest.odometerKm(),
                sorted);
    }

    /**
     * Returns all raw events for a vehicle, or an empty list.
     */
    public List<MaintenanceEvent> getEventsByVehicle(String vehicleId) {
        List<MaintenanceEvent> events = eventsByVehicle.get(vehicleId);
        if (events == null) {
            return List.of();
        }
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    /**
     * Returns the set of all vehicle IDs that have stored data.
     */
    public Set<String> getAllVehicleIds() {
        return Set.copyOf(eventsByVehicle.keySet());
    }

    /**
     * Clears all stored data. Useful for tests.
     */
    public void clear() {
        eventsByVehicle.clear();
    }
}
