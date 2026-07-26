package com.robinhoop.trader.backtest;

import com.robinhoop.trader.model.Bar;
import com.robinhoop.trader.strategy.MovingAverageCrossoverStrategy;
import com.robinhoop.trader.strategy.Strategy;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacktestEngineTest {

    @Test
    void ridesAnUptrendToAPositiveReturn() {
        List<Bar> bars = closesToBars(rampUp());
        Strategy strategy = new MovingAverageCrossoverStrategy(2, 3);
        BacktestEngine engine = new BacktestEngine(strategy);

        BacktestResult result = engine.run("TEST", bars);

        assertTrue(result.totalReturnPct() > 0, "expected a positive return riding a steady uptrend");
    }

    @Test
    void marksOpenPositionToMarketAtLastBar() {
        // Uptrend only, so a BUY fires but no SELL ever follows.
        List<Bar> bars = closesToBars(10, 10, 10, 12, 14, 16, 18);
        Strategy strategy = new MovingAverageCrossoverStrategy(2, 3);
        BacktestEngine engine = new BacktestEngine(strategy);

        BacktestResult result = engine.run("TEST", bars);

        assertEquals(1, result.numTrades());
        assertEquals(bars.get(bars.size() - 1).date(), result.trades().get(0).exitDate());
    }

    private static double[] rampUp() {
        // Flat first (so the crossover starts in a known "not above" state), then a
        // steady ramp so a genuine BUY crossover transition occurs mid-series.
        double[] closes = new double[40];
        closes[0] = 100;
        closes[1] = 100;
        closes[2] = 100;
        for (int i = 3; i < closes.length; i++) {
            closes[i] = 100 + (i - 2);
        }
        return closes;
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
