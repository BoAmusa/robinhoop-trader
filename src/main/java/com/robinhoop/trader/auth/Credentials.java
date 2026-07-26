package com.robinhoop.trader.auth;

/**
 * Loaded strictly from environment variables at runtime. Never persisted to disk by
 * this app — only the resulting session (access/refresh/device tokens) is cached.
 */
public record Credentials(String username, String password) {

    public static Credentials fromEnvironment() {
        String username = System.getenv("ROBINHOOD_USERNAME");
        String password = System.getenv("ROBINHOOD_PASSWORD");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new AuthException(
                    "Set ROBINHOOD_USERNAME and ROBINHOOD_PASSWORD environment variables before running live mode. "
                            + "Do not hardcode credentials in source or config files.");
        }
        return new Credentials(username, password);
    }
}
