package com.atul.telemetry_engine.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.atul.telemetry_engine.model.TelemetryEntity;

@Repository
public interface TelemetryRepository extends JpaRepository<TelemetryEntity, Long> {
    
    // save() is built-in


    // 1. Fetch the most recent reading for a specific sensor
    // We use "nativeQuery = true" to leverage TimescaleDB's speed if needed, 
    // but standard SQL works fine here too.
    @Query(value = "SELECT * FROM telemetry WHERE sensor_id = :sensorId ORDER BY timestamp DESC LIMIT 1", nativeQuery = true)
    TelemetryEntity findLatestBySensorId(@Param("sensorId") String sensorId);

    // 2. Calculate the Average Voltage for a sensor in the last X minutes
    // This is where the database does the heavy lifting, not Java.
    @Query("SELECT AVG(t.voltage) FROM TelemetryEntity t WHERE t.sensorId = :sensorId AND t.timestamp > :startTime")
    Double findAverageVoltage(@Param("sensorId") String sensorId, @Param("startTime") LocalDateTime startTime);

    // 3. Fetch history for charts (e.g., last 100 readings)
    List<TelemetryEntity> findTop100BySensorIdOrderByTimestampDesc(String sensorId);
}
