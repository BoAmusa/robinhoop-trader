package com.robinhoop.trader.backtest;

import java.time.LocalDate;
import java.util.List;

public record BacktestResult(
        String symbol,
        LocalDate from,
        LocalDate to,
        int numTrades,
        double totalReturnPct,
        double buyAndHoldReturnPct,
        double winRatePct,
        double maxDrawdownPct,
        List<Trade> trades
) {
}
