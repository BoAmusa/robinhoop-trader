package com.robinhoop.trader.backtest;

import java.time.LocalDate;

public record Trade(String symbol, LocalDate entryDate, double entryPrice, LocalDate exitDate, double exitPrice) {

    public double returnPct() {
        return (exitPrice - entryPrice) / entryPrice;
    }
}
