package com.robinhoop.trader.execution;

import com.robinhoop.trader.strategy.Signal;
import com.robinhoop.trader.strategy.SignalType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns today's signals into a fully-specified, capped order plan — deterministic,
 * unit-tested code. This is the money-math that used to live in the routine's
 * natural-language prompt (an LLM computing quantities and tracking a running
 * aggregate-cap counter itself); moving it here means the agent only ever executes
 * an exact, pre-computed plan instead of doing the arithmetic.
 */
public class OrderPlanner {

    private final double positionCapPct;
    private final double aggregateCapPct;

    public OrderPlanner(double positionCapPct, double aggregateCapPct) {
        if (positionCapPct <= 0 || positionCapPct > 1) {
            throw new IllegalArgumentException("positionCapPct must be in (0, 1]");
        }
        if (aggregateCapPct <= 0 || aggregateCapPct > 1) {
            throw new IllegalArgumentException("aggregateCapPct must be in (0, 1]");
        }
        this.positionCapPct = positionCapPct;
        this.aggregateCapPct = aggregateCapPct;
    }

    /**
     * @param signals   today's fresh signals, in the order they should be considered
     * @param accountValue current total account value
     * @param positions symbol -> currently held quantity (absent or non-positive means no position)
     */
    public List<PlanLine> plan(List<Signal> signals, double accountValue, Map<String, Double> positions) {
        List<PlanLine> lines = new ArrayList<>();
        double maxPerTrade = positionCapPct * accountValue;
        double maxAggregate = aggregateCapPct * accountValue;
        double deployedThisRun = 0.0;

        for (Signal signal : signals) {
            String symbol = signal.symbol();
            boolean holding = positions.getOrDefault(symbol, 0.0) > 0;

            if (signal.type() == SignalType.BUY) {
                if (holding) {
                    lines.add(PlanLine.skip(symbol, SignalType.BUY, "already_holding"));
                    continue;
                }

                double quantity = Math.floor((maxPerTrade / signal.price()) * 10_000) / 10_000.0;
                if (quantity <= 0) {
                    lines.add(PlanLine.skip(symbol, SignalType.BUY, "zero_quantity"));
                    continue;
                }

                double orderValue = quantity * signal.price();
                if (deployedThisRun + orderValue > maxAggregate) {
                    lines.add(PlanLine.skip(symbol, SignalType.BUY, "aggregate_cap_reached"));
                    continue;
                }

                lines.add(PlanLine.order(symbol, SignalType.BUY, quantity, signal.price(), orderValue));
                deployedThisRun += orderValue;
            } else {
                if (!holding) {
                    lines.add(PlanLine.skip(symbol, SignalType.SELL, "not_holding"));
                    continue;
                }
                double quantity = positions.get(symbol);
                lines.add(PlanLine.order(symbol, SignalType.SELL, quantity, signal.price(), quantity * signal.price()));
            }
        }

        return lines;
    }
}
