package com.robinhoop.trader.risk;

public record RiskDecision(boolean allowed, String reason) {

    public static RiskDecision approve() {
        return new RiskDecision(true, "within risk limits");
    }

    public static RiskDecision blocked(String reason) {
        return new RiskDecision(false, reason);
    }
}
