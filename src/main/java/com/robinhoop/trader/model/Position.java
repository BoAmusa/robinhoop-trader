package com.robinhoop.trader.model;

public record Position(String symbol, double quantity, double averageBuyPrice) {
}
