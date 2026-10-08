package com.paymu.maintenance.service;

import com.paymu.maintenance.model.DtcReading;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DtcStore {
    private final Map<String, List<DtcReading>> readingsByVehicle = new ConcurrentHashMap<>();

    public void storeReading(DtcReading reading) {
        readingsByVehicle
                .computeIfAbsent(reading.vehicleId(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(reading);
    }

    public List<DtcReading> getHistory(String vehicleId) {
        List<DtcReading> readings = readingsByVehicle.get(vehicleId);
        if (readings == null) {
            return List.of();
        }
        synchronized (readings) {
            return readings.stream()
                    .sorted(Comparator.comparing(DtcReading::timestamp).reversed())
                    .toList();
        }
    }

    public DtcReading getLatest(String vehicleId) {
        List<DtcReading> history = getHistory(vehicleId);
        return history.isEmpty() ? null : history.getFirst();
    }

    public void clear() {
        readingsByVehicle.clear();
    }
}
