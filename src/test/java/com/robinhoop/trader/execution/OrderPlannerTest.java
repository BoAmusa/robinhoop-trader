package com.robinhoop.trader.execution;

import com.robinhoop.trader.strategy.Signal;
import com.robinhoop.trader.strategy.SignalType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderPlannerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 27);

    private OrderPlanner planner() {
        return new OrderPlanner(0.20, 0.40);
    }

    @Test
    void buySignalNotHoldingProducesCorrectlyCappedOrder() {
        // $10,000 account, 20% cap = $2000 max. Price $100 -> 20.0000 shares exactly.
        Signal signal = new Signal("AAPL", TODAY, SignalType.BUY, 100.0);

        List<PlanLine> plan = planner().plan(List.of(signal), 10_000, Map.of());

        assertEquals(1, plan.size());
        PlanLine line = plan.get(0);
        assertTrue(line.isOrder());
        assertEquals(20.0, line.quantity(), 0.0001);
        assertEquals(2000.0, line.orderValue(), 0.01);
    }

    @Test
    void buyQuantityFloorsToFourDecimalPlaces() {
        // 20% of 133.29 = 26.658; price 233.45 -> 26.658/233.45 = 0.114183...
        // floor to 4dp = 0.1141
        Signal signal = new Signal("NVDA", TODAY, SignalType.BUY, 233.45);

        List<PlanLine> plan = planner().plan(List.of(signal), 133.29, Map.of());

        assertEquals(0.1141, plan.get(0).quantity(), 0.00001);
    }

    @Test
    void buySkippedWhenAlreadyHolding() {
        Signal signal = new Signal("AAPL", TODAY, SignalType.BUY, 100.0);

        List<PlanLine> plan = planner().plan(List.of(signal), 10_000, Map.of("AAPL", 5.0));

        assertEquals(1, plan.size());
        assertTrue(!plan.get(0).isOrder());
        assertEquals("already_holding", plan.get(0).skipReason());
    }

    @Test
    void sellSkippedWhenNotHolding() {
        Signal signal = new Signal("AAPL", TODAY, SignalType.SELL, 100.0);

        List<PlanLine> plan = planner().plan(List.of(signal), 10_000, Map.of());

        assertEquals(1, plan.size());
        assertTrue(!plan.get(0).isOrder());
        assertEquals("not_holding", plan.get(0).skipReason());
    }

    @Test
    void sellOrdersFullHeldQuantity() {
        Signal signal = new Signal("AAPL", TODAY, SignalType.SELL, 150.0);

        List<PlanLine> plan = planner().plan(List.of(signal), 10_000, Map.of("AAPL", 3.25));

        assertEquals(1, plan.size());
        PlanLine line = plan.get(0);
        assertTrue(line.isOrder());
        assertEquals(3.25, line.quantity(), 0.0001);
    }

    @Test
    void secondBuyBlockedByAggregateCapWhileFirstStillOrders() {
        // $1000 account: 20% per-trade = $200, 40% aggregate = $400.
        // Two BUY signals at $50 each would each want $200 -> together exactly $400, fits.
        // A third would push to $600 and must be skipped.
        Signal first = new Signal("AAA", TODAY, SignalType.BUY, 50.0);
        Signal second = new Signal("BBB", TODAY, SignalType.BUY, 50.0);
        Signal third = new Signal("CCC", TODAY, SignalType.BUY, 50.0);

        List<PlanLine> plan = planner().plan(List.of(first, second, third), 1000, Map.of());

        assertEquals(3, plan.size());
        assertTrue(plan.get(0).isOrder());
        assertTrue(plan.get(1).isOrder());
        assertTrue(!plan.get(2).isOrder());
        assertEquals("aggregate_cap_reached", plan.get(2).skipReason());
    }

    @Test
    void laterSmallerBuyCanStillFitInRemainingAggregateHeadroom() {
        // $1000 account: aggregate cap $400. First BUY at a high price only fits a
        // small quantity, leaving headroom for a second, cheaper-priced BUY to still fit
        // even though a third of similar size would not.
        Signal expensive = new Signal("AAA", TODAY, SignalType.BUY, 190.0); // 20% = $200 -> qty ~1.0526, value ~$200
        Signal cheap = new Signal("BBB", TODAY, SignalType.BUY, 10.0);      // 20% = $200 -> qty 20.0, value $200

        List<PlanLine> plan = planner().plan(List.of(expensive, cheap), 1000, Map.of());

        assertEquals(2, plan.size());
        assertTrue(plan.get(0).isOrder());
        assertTrue(plan.get(1).isOrder());
    }

    @Test
    void zeroQuantitySkippedWhenPriceExceedsCap() {
        // Tiny account, high price symbol: 20% of $1 = $0.20; at $500/share that's still
        // 0.0004 shares (non-zero at 4dp precision), so use a price high enough that even
        // 4dp floors to zero: 0.20 / 500,000 * 10000 = 0.004 -> floors to 0.
        Signal signal = new Signal("BRK.A", TODAY, SignalType.BUY, 500_000.0);

        List<PlanLine> plan = planner().plan(List.of(signal), 1.0, Map.of());

        assertEquals(1, plan.size());
        assertTrue(!plan.get(0).isOrder());
        assertEquals("zero_quantity", plan.get(0).skipReason());
    }

    @Test
    void outputLineFormatIsMachineParseable() {
        PlanLine order = PlanLine.order("AAPL", SignalType.BUY, 0.1141, 233.45, 26.64);
        PlanLine skip = PlanLine.skip("MSFT", SignalType.BUY, "already_holding");

        assertEquals("ORDER AAPL BUY 0.1141 price_reference=233.45 order_value=26.64", order.toOutputLine());
        assertEquals("SKIP MSFT BUY reason=already_holding", skip.toOutputLine());
    }
}
