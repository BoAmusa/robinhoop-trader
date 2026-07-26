package com.robinhoop.trader.marketdata;

import com.robinhoop.trader.model.Bar;

import java.time.LocalDate;
import java.util.List;

public interface MarketDataClient {

    List<Bar> getDailyHistory(String symbol, LocalDate from, LocalDate to);
}
