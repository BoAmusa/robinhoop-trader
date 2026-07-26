package com.robinhoop.trader.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/** Persists the Robinhood session (tokens + device token) to a local, gitignored file. */
public class SessionStore {

    private final Path path;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public SessionStore() {
        this(Path.of("robinhood.session.json"));
    }

    public SessionStore(Path path) {
        this.path = path;
    }

    public Optional<RobinhoodSession> load() {
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(path.toFile(), RobinhoodSession.class));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public void save(RobinhoodSession session) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), session);
        } catch (IOException e) {
            throw new AuthException("Failed to persist Robinhood session to " + path, e);
        }
    }

    /** Reuses a previously generated device_token if present, otherwise mints a new one. */
    public String loadOrCreateDeviceToken() {
        return load().map(RobinhoodSession::deviceToken).orElseGet(() -> UUID.randomUUID().toString());
    }
}
