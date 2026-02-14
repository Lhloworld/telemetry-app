package com.atul.telemetry_engine.controller;



import com.atul.telemetry_engine.model.TelemetryEntity;
import com.atul.telemetry_engine.repository.TelemetryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/telemetry")
@CrossOrigin(origins = "*") // Allow frontend apps (React/Angular) to call this API
public class DashboardController {

    @Autowired
    private TelemetryRepository telemetryRepository;

    
    // URL: GET http://localhost:8080/api/telemetry/latest?sensorId=device-001
    @GetMapping("/latest")
    public TelemetryEntity getLatestReading(@RequestParam String sensorId) {
        return telemetryRepository.findLatestBySensorId(sensorId);
    }

    
    // URL: GET http://localhost:8080/api/telemetry/average?sensorId=device-001&minutes=5
    @GetMapping("/average")
    public Double getAverageVoltage(@RequestParam String sensorId, @RequestParam(defaultValue = "5") int minutes) {
        LocalDateTime startTime = LocalDateTime.now().minusMinutes(minutes);
        return telemetryRepository.findAverageVoltage(sensorId, startTime);
    }

    
    // URL: GET http://localhost:8080/api/telemetry/history?sensorId=device-001
    @GetMapping("/history")
    public List<TelemetryEntity> getHistory(@RequestParam String sensorId) {
        return telemetryRepository.findTop100BySensorIdOrderByTimestampDesc(sensorId);
    }
}