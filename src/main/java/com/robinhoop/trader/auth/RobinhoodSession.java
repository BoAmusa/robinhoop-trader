package com.robinhoop.trader.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Cached locally (gitignored, see *.session.json) so repeat runs can reuse the
 * device_token and refresh_token instead of re-triggering an SMS challenge every time.
 */
public record RobinhoodSession(
        @JsonProperty("deviceToken") String deviceToken,
        @JsonProperty("accessToken") String accessToken,
        @JsonProperty("refreshToken") String refreshToken,
        @JsonProperty("expiresAt") Instant expiresAt
) {
    @JsonCreator
    public RobinhoodSession {
    }

    public boolean isAccessTokenExpired() {
        return Instant.now().isAfter(expiresAt.minusSeconds(60));
    }
}
