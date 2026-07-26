package com.robinhoop.trader;

import com.robinhoop.trader.auth.ConsoleMfaPrompt;
import com.robinhoop.trader.auth.Credentials;
import com.robinhoop.trader.auth.RobinhoodAuthClient;
import com.robinhoop.trader.auth.SessionManager;
import com.robinhoop.trader.auth.SessionStore;
import com.robinhoop.trader.backtest.BacktestEngine;
import com.robinhoop.trader.backtest.BacktestResult;
import com.robinhoop.trader.backtest.Regime;
import com.robinhoop.trader.broker.RobinhoodApiClient;
import com.robinhoop.trader.execution.ConsoleConfirmationPrompt;
import com.robinhoop.trader.execution.OrderExecutor;
import com.robinhoop.trader.marketdata.MarketDataClient;
import com.robinhoop.trader.marketdata.YahooFinanceMarketDataClient;
import com.robinhoop.trader.model.Bar;
import com.robinhoop.trader.risk.DailyEquityTracker;
import com.robinhoop.trader.risk.KillSwitch;
import com.robinhoop.trader.risk.RiskLimits;
import com.robinhoop.trader.risk.RiskManager;
import com.robinhoop.trader.scheduler.MarketHoursScheduler;
import com.robinhoop.trader.strategy.MovingAverageCrossoverStrategy;
import com.robinhoop.trader.strategy.SignalTracker;
import com.robinhoop.trader.strategy.Strategy;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

public class Main {

    private static final List<String> WATCHLIST = List.of(
            "SPY", "QQQ", "AAPL", "MSFT", "NVDA", "AMZN", "GOOGL", "META", "AMD", "TSLA"
    );

    private static final List<Regime> REGIMES = List.of(
            new Regime("Last 2 years", LocalDate.of(2024, 7, 25), LocalDate.of(2026, 7, 25)),
            new Regime("2022 bear market", LocalDate.of(2022, 1, 1), LocalDate.of(2022, 12, 31)),
            new Regime("2020-2021 COVID crash & recovery", LocalDate.of(2020, 1, 1), LocalDate.of(2021, 12, 31)),
            new Regime("Full 5 years", LocalDate.of(2021, 7, 25), LocalDate.of(2026, 7, 25))
    );

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "backtest";

        switch (mode) {
            case "backtest" -> runBacktest();
            case "live" -> runLive();
            default -> System.out.println("Unknown mode: " + mode + ". Supported modes: backtest, live");
        }
    }

    private static void runBacktest() {
        MarketDataClient marketDataClient = new YahooFinanceMarketDataClient();
        Strategy strategy = new MovingAverageCrossoverStrategy(20, 50);
        BacktestEngine engine = new BacktestEngine(strategy);

        System.out.println("Strategy: " + strategy.name());

        for (Regime regime : REGIMES) {
            System.out.println();
            System.out.println("=== " + regime.label() + " (" + regime.from() + " to " + regime.to() + ") ===");
            System.out.printf("%-7s %8s %12s %14s %10s %10s%n",
                    "Symbol", "Trades", "Strategy%", "BuyHold%", "Win%", "MaxDD%");

            for (String symbol : WATCHLIST) {
                try {
                    List<Bar> bars = marketDataClient.getDailyHistory(symbol, regime.from(), regime.to());
                    BacktestResult result = engine.run(symbol, bars);
                    System.out.printf("%-7s %8d %12.2f %14.2f %10.2f %10.2f%n",
                            symbol, result.numTrades(), result.totalReturnPct(),
                            result.buyAndHoldReturnPct(), result.winRatePct(), result.maxDrawdownPct());
                } catch (Exception e) {
                    System.out.printf("%-7s ERROR: %s%n", symbol, e.getMessage());
                }
            }
        }
    }

    private static void runLive() {
        String accountNumber = requireEnv("ROBINHOOD_ACCOUNT_NUMBER");
        Credentials credentials = Credentials.fromEnvironment();
        boolean dryRun = !"true".equalsIgnoreCase(System.getenv("LIVE_TRADING_ENABLED"));
        int intervalMinutes = envIntOrDefault("CHECK_INTERVAL_MINUTES", 15);
        int historyDays = envIntOrDefault("HISTORY_DAYS", 120);

        System.out.println(dryRun
                ? "Starting in DRY RUN mode — no orders will be sent. Set LIVE_TRADING_ENABLED=true to go live."
                : "Starting in LIVE mode — orders WILL be submitted to Robinhood after confirmation.");

        HttpClient httpClient = HttpClient.newHttpClient();
        RobinhoodAuthClient authClient = new RobinhoodAuthClient(httpClient, new ConsoleMfaPrompt());
        SessionManager sessionManager = new SessionManager(authClient, new SessionStore());
        RobinhoodApiClient brokerClient = new RobinhoodApiClient(httpClient, sessionManager, credentials);

        RiskManager riskManager = new RiskManager(RiskLimits.fromEnvironment(), new KillSwitch(), new DailyEquityTracker());
        OrderExecutor orderExecutor = new OrderExecutor(
                brokerClient, riskManager, new ConsoleConfirmationPrompt(), accountNumber, dryRun);

        Strategy strategy = new MovingAverageCrossoverStrategy(20, 50);
        MarketDataClient marketDataClient = new YahooFinanceMarketDataClient();
        SignalTracker signalTracker = new SignalTracker();

        LiveTradingTask task = new LiveTradingTask(WATCHLIST, marketDataClient, strategy, signalTracker,
                brokerClient, orderExecutor, riskManager, accountNumber, historyDays);

        MarketHoursScheduler scheduler = new MarketHoursScheduler(Duration.ofMinutes(intervalMinutes), task);
        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::stop));
        scheduler.start();
    }

    private static String requireEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return value;
    }

    private static int envIntOrDefault(String key, int defaultValue) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? defaultValue : Integer.parseInt(value);
    }
}
