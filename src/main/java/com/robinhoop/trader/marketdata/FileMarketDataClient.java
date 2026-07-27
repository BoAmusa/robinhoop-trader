package com.robinhoop.trader.marketdata;

import com.robinhoop.trader.model.Bar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads daily OHLCV history from a flat, headerless CSV file:
 * {@code SYMBOL,DATE,OPEN,HIGH,LOW,CLOSE,VOLUME} (DATE as ISO yyyy-MM-dd), one bar per
 * line, in any order. Intended for environments where the orchestrating caller has
 * already fetched historicals from elsewhere (e.g. the Robinhood MCP connector) because
 * this process's own outbound network access to a market-data API is unavailable.
 */
public class FileMarketDataClient implements MarketDataClient {

    private static final Logger log = LoggerFactory.getLogger(FileMarketDataClient.class);

    private final Map<String, List<Bar>> barsBySymbol = new HashMap<>();

    public FileMarketDataClient(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            throw new MarketDataException("Failed to read market data file " + file, e);
        }

        Map<String, List<Bar>> parsed = new HashMap<>();
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split(",");
            if (parts.length != 7) {
                log.warn("Skipping malformed market data line (expected 7 fields): {}", rawLine);
                continue;
            }
            try {
                String symbol = parts[0].trim().toUpperCase();
                LocalDate date = LocalDate.parse(parts[1].trim());
                double open = Double.parseDouble(parts[2].trim());
                double high = Double.parseDouble(parts[3].trim());
                double low = Double.parseDouble(parts[4].trim());
                double close = Double.parseDouble(parts[5].trim());
                long volume = Long.parseLong(parts[6].trim());
                parsed.computeIfAbsent(symbol, s -> new ArrayList<>())
                        .add(new Bar(date, open, high, low, close, volume));
            } catch (RuntimeException e) {
                log.warn("Skipping malformed market data line ({}): {}", e.getMessage(), rawLine);
            }
        }

        for (Map.Entry<String, List<Bar>> entry : parsed.entrySet()) {
            entry.getValue().sort(Comparator.comparing(Bar::date));
            barsBySymbol.put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public List<Bar> getDailyHistory(String symbol, LocalDate from, LocalDate to) {
        List<Bar> bars = barsBySymbol.get(symbol.toUpperCase());
        if (bars == null || bars.isEmpty()) {
            throw new MarketDataException("No market data file entries found for " + symbol);
        }

        List<Bar> inRange = new ArrayList<>();
        for (Bar bar : bars) {
            if (!bar.date().isBefore(from) && !bar.date().isAfter(to)) {
                inRange.add(bar);
            }
        }
        return inRange;
    }
}
