package com.atul.telemetry_engine.service;

import org.springframework.stereotype.Service;
import com.atul.telemetry_engine.model.SensorData;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DspFilterService {

    // How many past readings we look at to calculate the average
    private static final int WINDOW_SIZE = 5;

    // Thread-safe map to store the history of each sensor independently
    private final Map<String, Queue<Double>> voltageHistory = new ConcurrentHashMap<>();

    public SensorData processAndClean(SensorData rawData) {
        String sensorId = rawData.getSensorId();
        double currentVoltage = rawData.getVoltage();

        // 1. Get or create the history queue for this specific sensor
        Queue<Double> history = voltageHistory.computeIfAbsent(sensorId, k -> new LinkedList<>());

        // 2. Add the new reading to our window
        history.add(currentVoltage);

        // 3. If our window gets too big, remove the oldest reading
        if (history.size() > WINDOW_SIZE) {
            history.poll();
        }

        // 4. Calculate the Moving Average
        double sum = 0;
        for (double v : history) {
            sum += v;
        }
        double movingAverage = sum / history.size();

        // 5. Anomaly Detection & Cleaning Logic
        // If the current reading deviates by more than 15% from our smoothed average...
        if (history.size() == WINDOW_SIZE && Math.abs(currentVoltage - movingAverage) > (movingAverage * 0.05)) {
            System.out.println("⚠️ [DSP ALERT] Voltage Spike Detected! Raw: " + String.format("%.2f", currentVoltage)
                    + "V, Expected: ~" + String.format("%.2f", movingAverage) + "V");

            // "Clean" the data by replacing the noisy spike with our calculated average
            rawData.setVoltage(movingAverage);
        } else {
            // Even if it's not a massive spike, we apply the smoothed average to remove
            // micro-jitter
            rawData.setVoltage(movingAverage);
        }

        return rawData;
    }
}