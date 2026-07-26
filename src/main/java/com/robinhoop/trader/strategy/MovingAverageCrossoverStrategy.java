package com.robinhoop.trader.strategy;

import com.robinhoop.trader.model.Bar;

import java.util.ArrayList;
import java.util.List;

/**
 * Classic dual simple-moving-average crossover: emits BUY when the short-period SMA
 * crosses above the long-period SMA, and SELL on the reverse cross. Deterministic and
 * easy to sanity-check by eye, which matters most while validating the execution,
 * risk-control, and kill-switch machinery around it.
 */
public class MovingAverageCrossoverStrategy implements Strategy {

    private final int shortPeriod;
    private final int longPeriod;

    public MovingAverageCrossoverStrategy(int shortPeriod, int longPeriod) {
        if (shortPeriod <= 0 || longPeriod <= 0) {
            throw new IllegalArgumentException("Periods must be positive");
        }
        if (shortPeriod >= longPeriod) {
            throw new IllegalArgumentException("shortPeriod must be less than longPeriod");
        }
        this.shortPeriod = shortPeriod;
        this.longPeriod = longPeriod;
    }

    @Override
    public List<Signal> generateSignals(String symbol, List<Bar> bars) {
        List<Signal> signals = new ArrayList<>();
        if (bars.size() < longPeriod) {
            return signals;
        }

        Boolean shortWasAboveLong = null;

        for (int i = longPeriod - 1; i < bars.size(); i++) {
            double shortMa = simpleMovingAverage(bars, i, shortPeriod);
            double longMa = simpleMovingAverage(bars, i, longPeriod);
            boolean shortIsAboveLong = shortMa > longMa;

            if (shortWasAboveLong != null && shortIsAboveLong != shortWasAboveLong) {
                Bar bar = bars.get(i);
                SignalType type = shortIsAboveLong ? SignalType.BUY : SignalType.SELL;
                signals.add(new Signal(symbol, bar.date(), type, bar.close()));
            }
            shortWasAboveLong = shortIsAboveLong;
        }

        return signals;
    }

    @Override
    public String name() {
        return "SMA(%d/%d) Crossover".formatted(shortPeriod, longPeriod);
    }

    private double simpleMovingAverage(List<Bar> bars, int endInclusive, int period) {
        double sum = 0;
        for (int i = endInclusive - period + 1; i <= endInclusive; i++) {
            sum += bars.get(i).close();
        }
        return sum / period;
    }
}
