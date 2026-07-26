package com.robinhoop.trader.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robinhoop.trader.model.Bar;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches daily OHLCV history from Yahoo Finance's unofficial but public chart API.
 * Requires no API key or authentication, so it's usable for backtesting independent
 * of any Robinhood credentials.
 */
public class YahooFinanceMarketDataClient implements MarketDataClient {

    private static final ZoneId EXCHANGE_ZONE = ZoneId.of("America/New_York");
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; robinhoop-trader/1.0)";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public YahooFinanceMarketDataClient() {
        this(HttpClient.newHttpClient());
    }

    public YahooFinanceMarketDataClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public List<Bar> getDailyHistory(String symbol, LocalDate from, LocalDate to) {
        long period1 = from.atStartOfDay(EXCHANGE_ZONE).toEpochSecond();
        long period2 = to.plusDays(1).atStartOfDay(EXCHANGE_ZONE).toEpochSecond();

        String url = "https://query1.finance.yahoo.com/v8/finance/chart/%s?period1=%d&period2=%d&interval=1d"
                .formatted(symbol.toUpperCase(), period1, period2);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        String body;
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new MarketDataException("Yahoo Finance request failed for " + symbol + " with status " + response.statusCode());
            }
            body = response.body();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new MarketDataException("Failed to fetch historical data for " + symbol, e);
        }

        return parse(symbol, body);
    }

    private List<Bar> parse(String symbol, String body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException e) {
            throw new MarketDataException("Failed to parse Yahoo Finance response for " + symbol, e);
        }

        JsonNode errorNode = root.path("chart").path("error");
        if (!errorNode.isNull() && !errorNode.isMissingNode()) {
            throw new MarketDataException("Yahoo Finance returned an error for " + symbol + ": " + errorNode);
        }

        JsonNode result = root.path("chart").path("result").path(0);
        if (result.isMissingNode()) {
            throw new MarketDataException("No chart data returned for " + symbol);
        }

        JsonNode timestamps = result.path("timestamp");
        JsonNode quote = result.path("indicators").path("quote").path(0);
        JsonNode opens = quote.path("open");
        JsonNode highs = quote.path("high");
        JsonNode lows = quote.path("low");
        JsonNode closes = quote.path("close");
        JsonNode volumes = quote.path("volume");

        List<Bar> bars = new ArrayList<>();
        for (int i = 0; i < timestamps.size(); i++) {
            if (closes.path(i).isNull() || opens.path(i).isNull()) {
                // Yahoo returns null OHLC for the current in-progress session sometimes; skip incomplete bars.
                continue;
            }
            LocalDate date = Instant.ofEpochSecond(timestamps.get(i).asLong()).atZone(EXCHANGE_ZONE).toLocalDate();
            bars.add(new Bar(
                    date,
                    opens.get(i).asDouble(),
                    highs.get(i).asDouble(),
                    lows.get(i).asDouble(),
                    closes.get(i).asDouble(),
                    volumes.path(i).asLong()
            ));
        }
        return bars;
    }
}
