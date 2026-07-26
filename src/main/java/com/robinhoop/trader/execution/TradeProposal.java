package com.robinhoop.trader.execution;

import com.robinhoop.trader.model.OrderSide;

public record TradeProposal(String symbol, OrderSide side, double quantity, double estimatedPrice, String reason) {

    public double estimatedValue() {
        return quantity * estimatedPrice;
    }
}
