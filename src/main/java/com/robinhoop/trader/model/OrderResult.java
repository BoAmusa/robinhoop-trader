package com.robinhoop.trader.model;

public record OrderResult(String orderId, String status, String symbol, OrderSide side, double quantity) {
}
