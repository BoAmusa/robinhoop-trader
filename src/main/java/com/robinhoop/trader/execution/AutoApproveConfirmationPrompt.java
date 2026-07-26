package com.robinhoop.trader.execution;

/**
 * Approves every trade without blocking on a terminal — required for unattended
 * operation (e.g. a headless VPS with no one at a console). Safety no longer comes
 * from a human "y/N": it comes entirely from {@link com.robinhoop.trader.risk.RiskManager}
 * (position cap, daily loss halt) and the kill switch, both of which run before this
 * is ever consulted.
 */
public class AutoApproveConfirmationPrompt implements ConfirmationPrompt {

    @Override
    public boolean confirm(String message) {
        System.out.println("[AUTO-APPROVED] " + message);
        return true;
    }
}
