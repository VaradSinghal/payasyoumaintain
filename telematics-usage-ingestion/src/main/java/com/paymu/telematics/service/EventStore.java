package com.paymu.telematics.service;

import com.paymu.telematics.model.TripAggregate;
import com.paymu.telematics.model.UsageEvent;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for raw telematics events and trip aggregates.
 * Backed by {@link ConcurrentHashMap} — suitable for development and demo;
 * will be replaced by a real feature store in Phase 4.
 */
@Service
public class EventStore {

    private final Map<String, List<UsageEvent>> eventsByVehicle = new ConcurrentHashMap<>();
    private final Map<String, List<TripAggregate>> aggregatesByVehicle = new ConcurrentHashMap<>();

    /**
     * Appends events to the store, keyed by vehicle ID.
     */
    public void storeEvents(List<UsageEvent> events) {
        for (UsageEvent event : events) {
            eventsByVehicle.computeIfAbsent(event.vehicleId(), k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(event);
        }
    }

    /**
     * Appends trip aggregates to the store, keyed by vehicle ID.
     */
    public void storeAggregates(List<TripAggregate> aggregates) {
        for (TripAggregate agg : aggregates) {
            aggregatesByVehicle.computeIfAbsent(agg.vehicleId(), k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(agg);
        }
    }

    /**
     * Returns all raw events for a given vehicle, or an empty list if none exist.
     */
    public List<UsageEvent> getEventsByVehicle(String vehicleId) {
        return eventsByVehicle.getOrDefault(vehicleId, List.of());
    }

    /**
     * Returns all trip aggregates for a given vehicle, or an empty list if none exist.
     */
    public List<TripAggregate> getAggregatesByVehicle(String vehicleId) {
        return aggregatesByVehicle.getOrDefault(vehicleId, List.of());
    }

    /**
     * Returns the set of all vehicle IDs that have stored data.
     */
    public Set<String> getAllVehicleIds() {
        Set<String> ids = new HashSet<>();
        ids.addAll(eventsByVehicle.keySet());
        ids.addAll(aggregatesByVehicle.keySet());
        return ids;
    }

    /**
     * Clears all stored data. Useful for tests.
     */
    public void clear() {
        eventsByVehicle.clear();
        aggregatesByVehicle.clear();
    }
}
