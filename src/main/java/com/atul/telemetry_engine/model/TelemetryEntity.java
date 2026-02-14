package com.atul.telemetry_engine.model;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "telemetry") // This matches the table name in the DB
@Data
@NoArgsConstructor
public class TelemetryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sensorId;
    private double voltage;
    private double current;
    private double temperature;

    // TimescaleDB requires a timestamp column
    @Column(nullable = false)
    private LocalDateTime timestamp;

    // specific constructor to convert DTO to Entity easily
    public TelemetryEntity(String sensorId, double voltage, double current, double temperature, long epochMillis) {
        this.sensorId = sensorId;
        this.voltage = voltage;
        this.current = current;
        this.temperature = temperature;
        // Convert Epoch millis to LocalDateTime
        this.timestamp = Instant.ofEpochMilli(epochMillis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDateTime();
    }
}