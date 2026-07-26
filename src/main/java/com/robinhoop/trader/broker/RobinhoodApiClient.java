package com.robinhoop.trader.broker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robinhoop.trader.auth.Credentials;
import com.robinhoop.trader.auth.SessionManager;
import com.robinhoop.trader.model.Account;
import com.robinhoop.trader.model.OrderResult;
import com.robinhoop.trader.model.OrderSide;
import com.robinhoop.trader.model.Position;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin wrapper over Robinhood's private (unofficial, undocumented) REST API: account
 * equity/buying power, open positions, and equity order placement. Endpoint shapes are
 * reverse-engineered from public knowledge of the same API used by the Robinhood mobile
 * app; they are not guaranteed stable. This has not been exercised against a live
 * account in this environment — verify against your own account with a trivial,
 * manually-reviewed order before trusting it with real size.
 */
public class RobinhoodApiClient {

    private static final String BASE_URL = "https://api.robinhood.com";

    private final HttpClient httpClient;
    private final SessionManager sessionManager;
    private final Credentials credentials;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RobinhoodApiClient(HttpClient httpClient, SessionManager sessionManager, Credentials credentials) {
        this.httpClient = httpClient;
        this.sessionManager = sessionManager;
        this.credentials = credentials;
    }

    public Account getAccount(String accountNumber) {
        JsonNode accountNode = get(BASE_URL + "/accounts/" + accountNumber + "/");
        JsonNode portfolioNode = get(BASE_URL + "/accounts/" + accountNumber + "/portfolio/");

        double buyingPower = accountNode.path("buying_power").asDouble();
        double equity = portfolioNode.path("equity").asDouble();

        return new Account(accountNumber, equity, buyingPower);
    }

    public List<Position> getPositions(String accountNumber) {
        JsonNode results = get(BASE_URL + "/positions/?nonzero=true").path("results");

        List<Position> positions = new ArrayList<>();
        for (JsonNode result : results) {
            double quantity = result.path("quantity").asDouble();
            if (quantity == 0) {
                continue;
            }
            String instrumentUrl = result.path("instrument").asText();
            String symbol = get(instrumentUrl).path("symbol").asText();
            double averageBuyPrice = result.path("average_buy_price").asDouble();
            positions.add(new Position(symbol, quantity, averageBuyPrice));
        }
        return positions;
    }

    public OrderResult placeEquityMarketOrder(String accountNumber, String symbol, OrderSide side, double quantity) {
        String instrumentUrl = resolveInstrumentUrl(symbol);

        String body = """
                {
                  "account": "%s/accounts/%s/",
                  "instrument": "%s",
                  "symbol": "%s",
                  "type": "market",
                  "time_in_force": "gfd",
                  "trigger": "immediate",
                  "side": "%s",
                  "quantity": "%s"
                }
                """.formatted(BASE_URL, accountNumber, instrumentUrl, symbol, side.name().toLowerCase(), trimTrailingZeros(quantity));

        JsonNode response = post(BASE_URL + "/orders/", body);

        if (!response.has("id")) {
            throw new BrokerException("Robinhood rejected the order for " + symbol + ": " + response);
        }

        return new OrderResult(response.get("id").asText(), response.path("state").asText("unknown"), symbol, side, quantity);
    }

    private String resolveInstrumentUrl(String symbol) {
        JsonNode results = get(BASE_URL + "/instruments/?symbol=" + URLEncoder.encode(symbol, StandardCharsets.UTF_8)).path("results");
        if (!results.isArray() || results.isEmpty()) {
            throw new BrokerException("Could not resolve Robinhood instrument for symbol " + symbol);
        }
        return results.get(0).path("url").asText();
    }

    private static String trimTrailingZeros(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private JsonNode get(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + sessionManager.getValidAccessToken(credentials))
                .header("User-Agent", "robinhoop-trader/1.0")
                .GET()
                .build();
        return send(request, url);
    }

    private JsonNode post(String url, String jsonBody) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + sessionManager.getValidAccessToken(credentials))
                .header("Content-Type", "application/json")
                .header("User-Agent", "robinhoop-trader/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return send(request, url);
    }

    private JsonNode send(HttpRequest request, String url) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return objectMapper.readTree(response.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BrokerException("Network error calling Robinhood API at " + url, e);
        }
    }
}
