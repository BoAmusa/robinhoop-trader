package com.robinhoop.trader.risk;

import java.time.LocalDate;

public record DailyEquitySnapshot(LocalDate date, double equity) {
}
