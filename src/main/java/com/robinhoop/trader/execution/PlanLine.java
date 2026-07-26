package com.robinhoop.trader.execution;

import com.robinhoop.trader.strategy.SignalType;

/** One line of an order plan: either a fully-specified order to execute, or a skip with a reason. */
public record PlanLine(
        String symbol,
        SignalType type,
        boolean isOrder,
        double quantity,
        double referencePrice,
        double orderValue,
        String skipReason
) {

    public static PlanLine order(String symbol, SignalType type, double quantity, double referencePrice, double orderValue) {
        return new PlanLine(symbol, type, true, quantity, referencePrice, orderValue, null);
    }

    public static PlanLine skip(String symbol, SignalType type, String reason) {
        return new PlanLine(symbol, type, false, 0, 0, 0, reason);
    }

    /** Machine-parseable output line for the routine to consume verbatim. */
    public String toOutputLine() {
        if (isOrder) {
            return "ORDER %s %s %.4f price_reference=%.2f order_value=%.2f"
                    .formatted(symbol, type, quantity, referencePrice, orderValue);
        }
        return "SKIP %s %s reason=%s".formatted(symbol, type, skipReason);
    }
}
