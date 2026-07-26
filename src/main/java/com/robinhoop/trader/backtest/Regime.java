package com.robinhoop.trader.backtest;

import java.time.LocalDate;

/** A named historical date range used to stress-test a strategy across different market conditions. */
public record Regime(String label, LocalDate from, LocalDate to) {
}
