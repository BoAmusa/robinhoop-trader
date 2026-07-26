package com.robinhoop.trader.risk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A file-based emergency stop. Create the file (any content) to halt all new trading
 * immediately on the next scheduler tick; include the word "flatten" in its contents to
 * additionally request that all open positions be closed.
 */
public class KillSwitch {

    private final Path path;

    public KillSwitch() {
        this(Path.of("trading.kill-switch"));
    }

    public KillSwitch(Path path) {
        this.path = path;
    }

    public boolean isActive() {
        return Files.exists(path);
    }

    public boolean isFlattenRequested() {
        if (!isActive()) {
            return false;
        }
        try {
            return Files.readString(path).toLowerCase().contains("flatten");
        } catch (IOException e) {
            return false;
        }
    }

    public void activate(String reason) {
        try {
            Files.writeString(path, reason);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write kill switch file at " + path, e);
        }
    }

    public Path path() {
        return path;
    }
}
