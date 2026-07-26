package com.robinhoop.trader.strategy;

import java.time.LocalDate;

public record Signal(String symbol, LocalDate date, SignalType type, double price) {
}
