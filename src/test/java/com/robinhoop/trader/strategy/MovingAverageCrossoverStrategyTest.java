package com.robinhoop.trader.strategy;

import com.robinhoop.trader.model.Bar;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovingAverageCrossoverStrategyTest {

    @Test
    void emitsBuyWhenShortMaCrossesAboveLongMa() {
        // 3 flat bars at 10 (long MA anchor), then a ramp up so the 2-period SMA
        // crosses above the 3-period SMA partway through.
        List<Bar> bars = closesToBars(10, 10, 10, 12, 14, 16, 18);
        Strategy strategy = new MovingAverageCrossoverStrategy(2, 3);

        List<Signal> signals = strategy.generateSignals("TEST", bars);

        assertTrue(signals.stream().anyMatch(s -> s.type() == SignalType.BUY));
    }

    @Test
    void emitsSellWhenShortMaCrossesBelowLongMa() {
        // Flat, then ramp up (establishes short MA above long MA), then ramp down
        // hard enough to cross back below.
        List<Bar> bars = closesToBars(10, 10, 10, 12, 14, 16, 18, 16, 14, 12, 10, 8, 6, 4);
        Strategy strategy = new MovingAverageCrossoverStrategy(2, 3);

        List<Signal> signals = strategy.generateSignals("TEST", bars);

        assertTrue(signals.stream().anyMatch(s -> s.type() == SignalType.SELL));
    }

    @Test
    void noSignalsWhenNotEnoughHistory() {
        List<Bar> bars = closesToBars(10, 11, 12);
        Strategy strategy = new MovingAverageCrossoverStrategy(20, 50);

        assertEquals(0, strategy.generateSignals("TEST", bars).size());
    }

    @Test
    void rejectsInvalidPeriods() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new MovingAverageCrossoverStrategy(50, 20));
    }

    private static List<Bar> closesToBars(double... closes) {
        List<Bar> bars = new ArrayList<>();
        LocalDate date = LocalDate.of(2024, 1, 1);
        for (double close : closes) {
            bars.add(new Bar(date, close, close, close, close, 1_000_000));
            date = date.plusDays(1);
        }
        return bars;
    }
}
