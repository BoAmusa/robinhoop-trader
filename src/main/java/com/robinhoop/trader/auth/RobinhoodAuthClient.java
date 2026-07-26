package com.robinhoop.trader.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Unauthenticated, unofficial OAuth2 password-grant login against Robinhood's private
 * API — the same approach used by open-source projects like robin_stocks. There is no
 * public Robinhood trading API; this reverse-engineers the mobile app's login flow.
 *
 * IMPORTANT: this is fragile by nature. Robinhood can change endpoints, payload shapes,
 * or the verification flow at any time without notice, and has moved some accounts to a
 * newer "verification_workflow" (in-app push approval) that this client does NOT
 * support — only the classic SMS/email challenge-response flow is implemented. If login
 * fails with an unrecognized response shape, that's the most likely cause; treat it as
 * a signal to inspect the raw response, not a bug in the crossover strategy or risk
 * logic layered on top of this class.
 */
public class RobinhoodAuthClient {

    // Public OAuth client_id used by Robinhood's official mobile app, reverse-engineered
    // and widely published by open-source Robinhood API clients (e.g. robin_stocks).
    // Not a secret issued to this project.
    private static final String CLIENT_ID = "c82SH0WZOsabOXGP2sxqcj34FxkvfnWRZBKlBjFS";
    private static final String TOKEN_URL = "https://api.robinhood.com/oauth2/token/";
    private static final String CHALLENGE_URL_TEMPLATE = "https://api.robinhood.com/challenge/%s/respond/";
    private static final long TOKEN_EXPIRES_IN_SECONDS = 86400;

    private final HttpClient httpClient;
    private final MfaPrompt mfaPrompt;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RobinhoodAuthClient(HttpClient httpClient, MfaPrompt mfaPrompt) {
        this.httpClient = httpClient;
        this.mfaPrompt = mfaPrompt;
    }

    public RobinhoodAuthClient(MfaPrompt mfaPrompt) {
        this(HttpClient.newHttpClient(), mfaPrompt);
    }

    public RobinhoodSession login(Credentials credentials, String deviceToken) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "password");
        form.put("username", credentials.username());
        form.put("password", credentials.password());
        form.put("client_id", CLIENT_ID);
        form.put("expires_in", String.valueOf(TOKEN_EXPIRES_IN_SECONDS));
        form.put("scope", "internal");
        form.put("device_token", deviceToken);

        JsonNode response = postForm(TOKEN_URL, form, null);

        if (response.has("access_token")) {
            return toSession(response, deviceToken);
        }

        if (response.has("challenge")) {
            return handleChallenge(response.get("challenge"), form, deviceToken);
        }

        if (response.has("verification_workflow")) {
            throw new AuthException(
                    "Robinhood is requiring the newer in-app device-approval workflow for this account, "
                            + "which this client does not implement. Approve the login in the Robinhood mobile "
                            + "app if prompted, or re-run once account settings revert to SMS-based verification.");
        }

        throw new AuthException("Unrecognized login response from Robinhood: " + response);
    }

    public RobinhoodSession refresh(RobinhoodSession session) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", session.refreshToken());
        form.put("client_id", CLIENT_ID);
        form.put("scope", "internal");
        form.put("device_token", session.deviceToken());

        JsonNode response = postForm(TOKEN_URL, form, null);
        if (!response.has("access_token")) {
            throw new AuthException("Failed to refresh Robinhood session: " + response);
        }
        return toSession(response, session.deviceToken());
    }

    private RobinhoodSession handleChallenge(JsonNode challenge, Map<String, String> originalForm, String deviceToken) {
        String challengeId = challenge.path("id").asText();
        String challengeType = challenge.path("type").asText("sms");

        String code = mfaPrompt.promptForCode(challengeType);

        Map<String, String> challengeResponse = Map.of("response", code);
        postForm(CHALLENGE_URL_TEMPLATE.formatted(challengeId), challengeResponse, null);

        JsonNode retried = postForm(TOKEN_URL, originalForm, challengeId);
        if (!retried.has("access_token")) {
            throw new AuthException("Robinhood rejected the verification code: " + retried);
        }
        return toSession(retried, deviceToken);
    }

    private RobinhoodSession toSession(JsonNode response, String deviceToken) {
        String accessToken = response.get("access_token").asText();
        String refreshToken = response.path("refresh_token").asText(null);
        long expiresIn = response.path("expires_in").asLong(TOKEN_EXPIRES_IN_SECONDS);
        return new RobinhoodSession(deviceToken, accessToken, refreshToken, Instant.now().plusSeconds(expiresIn));
    }

    private JsonNode postForm(String url, Map<String, String> form, String challengeResponseId) {
        String body = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "robinhoop-trader/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(body));

        if (challengeResponseId != null) {
            requestBuilder.header("X-ROBINHOOD-CHALLENGE-RESPONSE-ID", challengeResponseId);
        }

        try {
            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            return objectMapper.readTree(response.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new AuthException("Network error talking to Robinhood auth endpoint", e);
        }
    }
}
