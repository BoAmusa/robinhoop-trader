package com.robinhoop.trader.auth;

/** Ties together login, session caching, and transparent refresh so callers just ask for a valid access token. */
public class SessionManager {

    private final RobinhoodAuthClient authClient;
    private final SessionStore sessionStore;
    private RobinhoodSession session;

    public SessionManager(RobinhoodAuthClient authClient, SessionStore sessionStore) {
        this.authClient = authClient;
        this.sessionStore = sessionStore;
    }

    public String getValidAccessToken(Credentials credentials) {
        if (session == null) {
            session = sessionStore.load().orElse(null);
        }

        if (session == null) {
            String deviceToken = sessionStore.loadOrCreateDeviceToken();
            session = authClient.login(credentials, deviceToken);
            sessionStore.save(session);
        } else if (session.isAccessTokenExpired()) {
            session = authClient.refresh(session);
            sessionStore.save(session);
        }

        return session.accessToken();
    }
}
