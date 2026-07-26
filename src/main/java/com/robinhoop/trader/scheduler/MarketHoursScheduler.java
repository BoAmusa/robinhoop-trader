package com.robinhoop.trader.scheduler;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Runs a task on a fixed interval, but only while the US equity market is open. */
public class MarketHoursScheduler {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final Duration interval;
    private final Runnable task;

    public MarketHoursScheduler(Duration interval, Runnable task) {
        this.interval = interval;
        this.task = task;
    }

    public void start() {
        executor.scheduleAtFixedRate(this::tick, 0, interval.getSeconds(), TimeUnit.SECONDS);
        System.out.printf("Scheduler started: checking every %d minutes during US market hours (9:30-16:00 ET, weekdays).%n",
                interval.toMinutes());
    }

    public void stop() {
        executor.shutdown();
    }

    private void tick() {
        if (!MarketHours.isOpen()) {
            return;
        }
        try {
            task.run();
        } catch (Exception e) {
            System.err.println("Scheduled trading check failed: " + e.getMessage());
        }
    }
}
