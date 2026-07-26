package com.robinhoop.trader.execution;

import com.robinhoop.trader.model.Account;
import com.robinhoop.trader.model.OrderSide;
import com.robinhoop.trader.risk.DailyEquityTracker;
import com.robinhoop.trader.risk.KillSwitch;
import com.robinhoop.trader.risk.RiskLimits;
import com.robinhoop.trader.risk.RiskManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderExecutorTest {

    private final Path killSwitchPath = Path.of("target/test-executor.kill-switch");
    private final Path equityPath = Path.of("target/test-executor-equity.session.json");

    @AfterEach
    void cleanup() throws Exception {
        Files.deleteIfExists(killSwitchPath);
        Files.deleteIfExists(equityPath);
    }

    private RiskManager newRiskManager() {
        return new RiskManager(new RiskLimits(0.05, 0.02), new KillSwitch(killSwitchPath), new DailyEquityTracker(equityPath));
    }

    @Test
    void skipsWithoutCallingBrokerWhenNotConfirmed() {
        // Passing a null broker client is safe here: the executor must never reach it
        // when the user declines to confirm.
        OrderExecutor executor = new OrderExecutor(null, newRiskManager(), message -> false, "acct", true);
        TradeProposal proposal = new TradeProposal("AAPL", OrderSide.BUY, 1, 100, "test signal");

        Optional<?> result = executor.execute(proposal, new Account("acct", 10_000, 10_000));

        assertTrue(result.isEmpty());
    }

    @Test
    void dryRunSkipsWithoutCallingBrokerEvenWhenConfirmed() {
        OrderExecutor executor = new OrderExecutor(null, newRiskManager(), message -> true, "acct", true);
        TradeProposal proposal = new TradeProposal("AAPL", OrderSide.BUY, 1, 100, "test signal");

        Optional<?> result = executor.execute(proposal, new Account("acct", 10_000, 10_000));

        assertTrue(result.isEmpty());
    }

    @Test
    void blocksOversizedBuyBeforePromptingForConfirmation() {
        OrderExecutor executor = new OrderExecutor(null, newRiskManager(),
                message -> {
                    throw new AssertionError("should never prompt when risk manager blocks the trade");
                }, "acct", true);
        // 5% cap on $10,000 equity is $500; this order is $5,000.
        TradeProposal proposal = new TradeProposal("AAPL", OrderSide.BUY, 50, 100, "test signal");

        Optional<?> result = executor.execute(proposal, new Account("acct", 10_000, 10_000));

        assertTrue(result.isEmpty());
    }
}
