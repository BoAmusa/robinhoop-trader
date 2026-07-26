package com.robinhoop.trader.strategy;

import com.robinhoop.trader.model.Bar;

import java.util.List;

/**
 * A pluggable trade-signal generator. The moving average crossover is the first
 * implementation; other strategies (RSI, breakout, etc.) can implement this same
 * interface without touching the backtest engine, risk manager, or execution layer.
 */
public interface Strategy {

    List<Signal> generateSignals(String symbol, List<Bar> bars);

    String name();
}
