package com.robinhoop.trader.execution;

import com.robinhoop.trader.broker.RobinhoodApiClient;
import com.robinhoop.trader.model.Account;
import com.robinhoop.trader.model.OrderResult;
import com.robinhoop.trader.model.OrderSide;
import com.robinhoop.trader.risk.RiskDecision;
import com.robinhoop.trader.risk.RiskManager;

import java.util.Optional;

/**
 * Turns a strategy signal into a live order, but never without: a risk-manager check
 * (BUY only — closing a position is never blocked), an explicit human "y" at the
 * console, and (unless dry-run is disabled) actually reaching the broker. Dry-run is
 * the default specifically because this order path has not been exercised against a
 * live Robinhood account in this environment.
 */
public class OrderExecutor {

    private final RobinhoodApiClient brokerClient;
    private final RiskManager riskManager;
    private final ConfirmationPrompt confirmationPrompt;
    private final String accountNumber;
    private final boolean dryRun;

    public OrderExecutor(RobinhoodApiClient brokerClient, RiskManager riskManager,
                          ConfirmationPrompt confirmationPrompt, String accountNumber, boolean dryRun) {
        this.brokerClient = brokerClient;
        this.riskManager = riskManager;
        this.confirmationPrompt = confirmationPrompt;
        this.accountNumber = accountNumber;
        this.dryRun = dryRun;
    }

    public Optional<OrderResult> execute(TradeProposal proposal, Account account) {
        if (proposal.side() == OrderSide.BUY) {
            RiskDecision decision = riskManager.evaluateNewTrade(account, proposal.estimatedValue());
            if (!decision.allowed()) {
                System.out.println("[BLOCKED] " + proposal.symbol() + " " + proposal.side() + ": " + decision.reason());
                return Optional.empty();
            }
        }

        System.out.printf("%nProposed trade: %s %s %.4f shares @ ~$%.2f (~$%.2f) — %s%n",
                proposal.side(), proposal.symbol(), proposal.quantity(), proposal.estimatedPrice(),
                proposal.estimatedValue(), proposal.reason());

        if (!confirmationPrompt.confirm("Submit this order?")) {
            System.out.println("Skipped — not confirmed.");
            return Optional.empty();
        }

        if (dryRun) {
            System.out.println("[DRY RUN] Order not sent. Set LIVE_TRADING_ENABLED=true to actually submit orders.");
            return Optional.empty();
        }

        OrderResult result = brokerClient.placeEquityMarketOrder(
                accountNumber, proposal.symbol(), proposal.side(), proposal.quantity());
        System.out.println("Order submitted: " + result);
        return Optional.of(result);
    }
}
