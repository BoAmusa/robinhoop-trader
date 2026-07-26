package com.robinhoop.trader.model;

import java.time.LocalDate;

public record Bar(LocalDate date, double open, double high, double low, double close, long volume) {
}
