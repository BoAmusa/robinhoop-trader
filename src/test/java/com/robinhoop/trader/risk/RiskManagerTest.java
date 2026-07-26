package com.robinhoop.trader.risk;

import com.robinhoop.trader.model.Account;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskManagerTest {

    private final Path killSwitchPath = Path.of("target/test-risk.kill-switch");
    private final Path equityPath = Path.of("target/test-daily-equity.session.json");

    @AfterEach
    void cleanup() throws Exception {
        Files.deleteIfExists(killSwitchPath);
        Files.deleteIfExists(equityPath);
    }

    private RiskManager newManager(double maxPositionPct, double maxDailyLossPct) {
        RiskLimits limits = new RiskLimits(maxPositionPct, maxDailyLossPct);
        return new RiskManager(limits, new KillSwitch(killSwitchPath), new DailyEquityTracker(equityPath));
    }

    @Test
    void blocksTradeWhenKillSwitchActive() throws Exception {
        Files.writeString(killSwitchPath, "manual halt");
        RiskManager riskManager = newManager(0.05, 0.02);

        RiskDecision decision = riskManager.evaluateNewTrade(new Account("acct", 10_000, 10_000), 100);

        assertFalse(decision.allowed());
    }

    @Test
    void blocksTradeExceedingPositionCap() {
        RiskManager riskManager = newManager(0.05, 0.02);
        Account account = new Account("acct", 10_000, 10_000);

        RiskDecision decision = riskManager.evaluateNewTrade(account, 600); // cap is 5% of 10k = 500

        assertFalse(decision.allowed());
    }

    @Test
    void allowsTradeWithinPositionCap() {
        RiskManager riskManager = newManager(0.05, 0.02);
        Account account = new Account("acct", 10_000, 10_000);

        RiskDecision decision = riskManager.evaluateNewTrade(account, 400); // within 5% of 10k = 500

        assertTrue(decision.allowed());
    }

    @Test
    void blocksTradeAfterDailyLossLimitBreached() throws Exception {
        DailyEquitySnapshot snapshot = new DailyEquitySnapshot(LocalDate.now(), 10_000);
        Files.writeString(equityPath, """
                {"date":"%s","equity":10000.0}
                """.formatted(snapshot.date()));

        RiskManager riskManager = newManager(0.50, 0.02); // position cap wide open, only loss limit should bind
        Account account = new Account("acct", 9_700, 9_700); // down 3%, limit is 2%

        RiskDecision decision = riskManager.evaluateNewTrade(account, 100);

        assertFalse(decision.allowed());
    }
}
