package com.robinhoop.trader.risk;

import com.robinhoop.trader.model.Account;

public class RiskManager {

    private final RiskLimits limits;
    private final KillSwitch killSwitch;
    private final DailyEquityTracker equityTracker;

    public RiskManager(RiskLimits limits, KillSwitch killSwitch, DailyEquityTracker equityTracker) {
        this.limits = limits;
        this.killSwitch = killSwitch;
        this.equityTracker = equityTracker;
    }

    /** Checked before any new BUY. Blocks on kill switch, daily loss breach, or position size. */
    public RiskDecision evaluateNewTrade(Account account, double proposedOrderValue) {
        if (killSwitch.isActive()) {
            return RiskDecision.blocked("Kill switch is active (" + killSwitch.path() + "); no new trades will be placed.");
        }

        double dayStartEquity = equityTracker.getOrRecordDayStartEquity(account.equity());
        double dailyChangePct = (account.equity() - dayStartEquity) / dayStartEquity;
        if (dailyChangePct <= -limits.maxDailyLossPct()) {
            return RiskDecision.blocked(
                    "Daily loss limit breached: %.2f%% (limit -%.2f%%). Halting new trades for today."
                            .formatted(dailyChangePct * 100, limits.maxDailyLossPct() * 100));
        }

        double maxPositionValue = maxPositionValue(account);
        if (proposedOrderValue > maxPositionValue) {
            return RiskDecision.blocked(
                    "Proposed order value $%.2f exceeds max position size $%.2f (%.1f%% of equity)."
                            .formatted(proposedOrderValue, maxPositionValue, limits.maxPositionPct() * 100));
        }

        return RiskDecision.approve();
    }

    /** SELL/flatten orders are always allowed through risk checks — closing risk is never blocked. */
    public double maxPositionValue(Account account) {
        return account.equity() * limits.maxPositionPct();
    }

    public boolean isKillSwitchActive() {
        return killSwitch.isActive();
    }

    public boolean isFlattenRequested() {
        return killSwitch.isFlattenRequested();
    }
}
