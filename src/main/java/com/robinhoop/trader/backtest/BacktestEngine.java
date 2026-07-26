package com.robinhoop.trader.backtest;

import com.robinhoop.trader.model.Bar;
import com.robinhoop.trader.strategy.Signal;
import com.robinhoop.trader.strategy.SignalType;
import com.robinhoop.trader.strategy.Strategy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Simulates a strategy against historical bars for a single symbol: goes long on BUY
 * signals, exits on the following SELL signal, one position at a time. Reports total
 * return, buy-and-hold return for comparison, win rate, and max drawdown so a strategy
 * can be judged against doing nothing before it ever touches live capital.
 */
public class BacktestEngine {

    private final Strategy strategy;

    public BacktestEngine(Strategy strategy) {
        this.strategy = strategy;
    }

    public BacktestResult run(String symbol, List<Bar> bars) {
        if (bars.isEmpty()) {
            throw new IllegalArgumentException("No historical bars for " + symbol);
        }

        List<Signal> signals = strategy.generateSignals(symbol, bars);
        List<Trade> trades = buildTrades(symbol, signals, bars);

        double totalReturn = trades.stream()
                .mapToDouble(t -> 1 + t.returnPct())
                .reduce(1.0, (a, b) -> a * b) - 1.0;

        double buyAndHold = (bars.get(bars.size() - 1).close() - bars.get(0).close()) / bars.get(0).close();

        long wins = trades.stream().filter(t -> t.returnPct() > 0).count();
        double winRate = trades.isEmpty() ? 0.0 : (100.0 * wins / trades.size());

        return new BacktestResult(
                symbol,
                bars.get(0).date(),
                bars.get(bars.size() - 1).date(),
                trades.size(),
                totalReturn * 100.0,
                buyAndHold * 100.0,
                winRate,
                computeMaxDrawdownPct(trades),
                trades
        );
    }

    private List<Trade> buildTrades(String symbol, List<Signal> signals, List<Bar> bars) {
        List<Trade> trades = new ArrayList<>();

        Double entryPrice = null;
        LocalDate entryDate = null;

        for (Signal signal : signals) {
            if (signal.type() == SignalType.BUY && entryPrice == null) {
                entryPrice = signal.price();
                entryDate = signal.date();
            } else if (signal.type() == SignalType.SELL && entryPrice != null) {
                trades.add(new Trade(symbol, entryDate, entryPrice, signal.date(), signal.price()));
                entryPrice = null;
                entryDate = null;
            }
        }

        // Mark an still-open position to market at the final bar so it counts toward
        // the result instead of silently vanishing from the backtest.
        if (entryPrice != null) {
            Bar last = bars.get(bars.size() - 1);
            trades.add(new Trade(symbol, entryDate, entryPrice, last.date(), last.close()));
        }

        return trades;
    }

    private double computeMaxDrawdownPct(List<Trade> trades) {
        double equity = 1.0;
        double peak = 1.0;
        double maxDrawdown = 0.0;
        for (Trade t : trades) {
            equity *= (1 + t.returnPct());
            peak = Math.max(peak, equity);
            maxDrawdown = Math.max(maxDrawdown, (peak - equity) / peak);
        }
        return maxDrawdown * 100.0;
    }
}
