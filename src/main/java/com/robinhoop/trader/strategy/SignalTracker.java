package com.robinhoop.trader.strategy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Persists the most recent signal date acted on per symbol, so a scheduler running
 * every 15-30 minutes doesn't re-propose the same crossover on every tick within the
 * same trading day.
 */
public class SignalTracker {

    private final Path path;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Map<String, LocalDate> lastActedDates;

    public SignalTracker() {
        this(Path.of("signal-tracker.session.json"));
    }

    public SignalTracker(Path path) {
        this.path = path;
        this.lastActedDates = load();
    }

    public boolean isNew(String symbol, LocalDate signalDate) {
        LocalDate lastActed = lastActedDates.get(symbol);
        return lastActed == null || signalDate.isAfter(lastActed);
    }

    public void recordActed(String symbol, LocalDate signalDate) {
        lastActedDates.put(symbol, signalDate);
        save();
    }

    private Map<String, LocalDate> load() {
        if (!Files.exists(path)) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(path.toFile(), new TypeReference<HashMap<String, LocalDate>>() {
            });
        } catch (IOException e) {
            return new HashMap<>();
        }
    }

    private void save() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), lastActedDates);
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist signal tracker to " + path, e);
        }
    }
}
