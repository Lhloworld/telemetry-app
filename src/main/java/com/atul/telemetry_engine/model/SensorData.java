package com.atul.telemetry_engine.model;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class SensorData {
    private String sensorId;
    private double voltage;
    private double current;
    private double temperature;
    private long timestamp;
}
