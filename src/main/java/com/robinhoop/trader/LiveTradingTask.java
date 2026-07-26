package com.robinhoop.trader;

import com.robinhoop.trader.broker.RobinhoodApiClient;
import com.robinhoop.trader.execution.OrderExecutor;
import com.robinhoop.trader.execution.TradeProposal;
import com.robinhoop.trader.marketdata.MarketDataClient;
import com.robinhoop.trader.model.Account;
import com.robinhoop.trader.model.Bar;
import com.robinhoop.trader.model.OrderSide;
import com.robinhoop.trader.model.Position;
import com.robinhoop.trader.risk.RiskManager;
import com.robinhoop.trader.strategy.Signal;
import com.robinhoop.trader.strategy.SignalTracker;
import com.robinhoop.trader.strategy.SignalType;
import com.robinhoop.trader.strategy.Strategy;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * One scheduler tick: pull fresh price history, run the strategy, and route any new
 * signal through the risk manager and order executor. Runs during market hours only —
 * the strategy is evaluated against the still-forming current-day bar, so a signal can
 * in principle flicker intraday; SignalTracker dedupes so it's only acted on once per
 * calendar day per symbol regardless of how many ticks re-observe it.
 */
public class LiveTradingTask implements Runnable {

    private final List<String> watchlist;
    private final MarketDataClient marketDataClient;
    private final Strategy strategy;
    private final SignalTracker signalTracker;
    private final RobinhoodApiClient brokerClient;
    private final OrderExecutor orderExecutor;
    private final RiskManager riskManager;
    private final String accountNumber;
    private final int historyDays;

    public LiveTradingTask(List<String> watchlist, MarketDataClient marketDataClient, Strategy strategy,
                            SignalTracker signalTracker, RobinhoodApiClient brokerClient, OrderExecutor orderExecutor,
                            RiskManager riskManager, String accountNumber, int historyDays) {
        this.watchlist = watchlist;
        this.marketDataClient = marketDataClient;
        this.strategy = strategy;
        this.signalTracker = signalTracker;
        this.brokerClient = brokerClient;
        this.orderExecutor = orderExecutor;
        this.riskManager = riskManager;
        this.accountNumber = accountNumber;
        this.historyDays = historyDays;
    }

    @Override
    public void run() {
        Account account;
        try {
            account = brokerClient.getAccount(accountNumber);
        } catch (Exception e) {
            System.err.println("Failed to fetch account: " + e.getMessage());
            return;
        }

        if (riskManager.isKillSwitchActive()) {
            System.out.println("Kill switch active — no new trades will be evaluated.");
            if (riskManager.isFlattenRequested()) {
                flattenAllPositions(account);
            }
            return;
        }

        List<Position> positions;
        try {
            positions = brokerClient.getPositions(accountNumber);
        } catch (Exception e) {
            System.err.println("Failed to fetch positions: " + e.getMessage());
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate historyStart = today.minusDays(historyDays);

        for (String symbol : watchlist) {
            try {
                List<Bar> bars = marketDataClient.getDailyHistory(symbol, historyStart, today);
                List<Signal> signals = strategy.generateSignals(symbol, bars);
                if (signals.isEmpty()) {
                    continue;
                }

                Signal latest = signals.get(signals.size() - 1);
                if (!signalTracker.isNew(symbol, latest.date())) {
                    continue;
                }

                Optional<TradeProposal> proposal = buildProposal(symbol, latest, account, positions);
                proposal.ifPresent(p -> orderExecutor.execute(p, account));
                signalTracker.recordActed(symbol, latest.date());
            } catch (Exception e) {
                System.err.println("Signal check failed for " + symbol + ": " + e.getMessage());
            }
        }
    }

    private Optional<TradeProposal> buildProposal(String symbol, Signal signal, Account account, List<Position> positions) {
        if (signal.type() == SignalType.BUY) {
            double maxValue = riskManager.maxPositionValue(account);
            double quantity = Math.floor((maxValue / signal.price()) * 10_000) / 10_000.0;
            if (quantity <= 0) {
                return Optional.empty();
            }
            return Optional.of(new TradeProposal(symbol, OrderSide.BUY, quantity, signal.price(),
                    "MA crossover BUY signal on " + signal.date()));
        }

        return positions.stream()
                .filter(p -> p.symbol().equalsIgnoreCase(symbol))
                .findFirst()
                .map(p -> new TradeProposal(symbol, OrderSide.SELL, p.quantity(), signal.price(),
                        "MA crossover SELL signal on " + signal.date()));
    }

    private void flattenAllPositions(Account account) {
        List<Position> positions;
        try {
            positions = brokerClient.getPositions(accountNumber);
        } catch (Exception e) {
            System.err.println("Failed to fetch positions for flatten: " + e.getMessage());
            return;
        }

        for (Position position : positions) {
            // Uses the position's average buy price only to display an estimate; the
            // actual order is a market order and fills at the live price.
            TradeProposal proposal = new TradeProposal(position.symbol(), OrderSide.SELL, position.quantity(),
                    position.averageBuyPrice(), "Kill switch flatten request");
            orderExecutor.execute(proposal, account);
        }
    }
}
