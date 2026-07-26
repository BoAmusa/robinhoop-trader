package com.robinhoop.trader.risk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Records account equity at the start of each trading day so the daily loss limit has
 * a fixed baseline to compare against, regardless of how many times the scheduler ticks.
 */
public class DailyEquityTracker {

    private final Path path;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public DailyEquityTracker() {
        this(Path.of("daily-equity.session.json"));
    }

    public DailyEquityTracker(Path path) {
        this.path = path;
    }

    public double getOrRecordDayStartEquity(double currentEquity) {
        LocalDate today = LocalDate.now();
        Optional<DailyEquitySnapshot> existing = load();
        if (existing.isPresent() && existing.get().date().equals(today)) {
            return existing.get().equity();
        }
        save(new DailyEquitySnapshot(today, currentEquity));
        return currentEquity;
    }

    private Optional<DailyEquitySnapshot> load() {
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(path.toFile(), DailyEquitySnapshot.class));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private void save(DailyEquitySnapshot snapshot) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), snapshot);
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist daily equity snapshot to " + path, e);
        }
    }
}
