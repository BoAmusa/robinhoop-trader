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
import com.robinhoop.trader.execution.AutoApproveConfirmationPrompt;
import com.robinhoop.trader.execution.ConfirmationPrompt;
import com.robinhoop.trader.execution.ConsoleConfirmationPrompt;
import com.robinhoop.trader.execution.OrderExecutor;
import com.robinhoop.trader.marketdata.MarketDataClient;
import com.robinhoop.trader.marketdata.YahooFinanceMarketDataClient;
import com.robinhoop.trader.model.Bar;
import com.robinhoop.trader.risk.DailyEquityTracker;
import com.robinhoop.trader.risk.KillSwitch;
import com.robinhoop.trader.risk.RiskLimits;
import com.robinhoop.trader.risk.RiskManager;
import com.robinhoop.trader.scheduler.MarketHours;
import com.robinhoop.trader.scheduler.MarketHoursScheduler;
import com.robinhoop.trader.strategy.MovingAverageCrossoverStrategy;
import com.robinhoop.trader.strategy.Signal;
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
            case "login-test" -> runLoginTest();
            case "signal-check" -> runSignalCheck();
            default -> System.out.println(
                    "Unknown mode: " + mode + ". Supported modes: backtest, live, login-test, signal-check");
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

        String approvalMode = System.getenv().getOrDefault("TRADE_APPROVAL_MODE", "manual");
        ConfirmationPrompt confirmationPrompt = resolveConfirmationPrompt(approvalMode);

        System.out.println(dryRun
                ? "Starting in DRY RUN mode — no orders will be sent. Set LIVE_TRADING_ENABLED=true to go live."
                : "Starting in LIVE mode — orders WILL be submitted to Robinhood after confirmation.");
        if ("auto".equalsIgnoreCase(approvalMode)) {
            System.out.println("TRADE_APPROVAL_MODE=auto — trades within risk limits will execute with NO human "
                    + "confirmation step. Safety relies entirely on MAX_POSITION_PCT, MAX_DAILY_LOSS_PCT, and the kill switch.");
        }

        HttpClient httpClient = HttpClient.newHttpClient();
        RobinhoodAuthClient authClient = new RobinhoodAuthClient(httpClient, new ConsoleMfaPrompt());
        SessionManager sessionManager = new SessionManager(authClient, new SessionStore());
        RobinhoodApiClient brokerClient = new RobinhoodApiClient(httpClient, sessionManager, credentials);

        RiskManager riskManager = new RiskManager(RiskLimits.fromEnvironment(), new KillSwitch(), new DailyEquityTracker());
        OrderExecutor orderExecutor = new OrderExecutor(
                brokerClient, riskManager, confirmationPrompt, accountNumber, dryRun);

        Strategy strategy = new MovingAverageCrossoverStrategy(20, 50);
        MarketDataClient marketDataClient = new YahooFinanceMarketDataClient();
        SignalTracker signalTracker = new SignalTracker();

        LiveTradingTask task = new LiveTradingTask(WATCHLIST, marketDataClient, strategy, signalTracker,
                brokerClient, orderExecutor, riskManager, accountNumber, historyDays);

        MarketHoursScheduler scheduler = new MarketHoursScheduler(Duration.ofMinutes(intervalMinutes), task);
        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::stop));
        scheduler.start();
    }

    /**
     * Read-only connectivity check: logs in (prompting for an SMS code if this is the
     * first login) and fetches account equity, then exits. Doesn't touch positions or
     * orders. Useful to validate credentials/MFA/session caching independent of market
     * hours — "live" mode never calls the broker at all outside 9:30-16:00 ET.
     */
    private static void runLoginTest() {
        String accountNumber = requireEnv("ROBINHOOD_ACCOUNT_NUMBER");
        Credentials credentials = Credentials.fromEnvironment();

        HttpClient httpClient = HttpClient.newHttpClient();
        RobinhoodAuthClient authClient = new RobinhoodAuthClient(httpClient, new ConsoleMfaPrompt());
        SessionManager sessionManager = new SessionManager(authClient, new SessionStore());
        RobinhoodApiClient brokerClient = new RobinhoodApiClient(httpClient, sessionManager, credentials);

        System.out.println("Logging in to Robinhood (you may be prompted for a verification code)...");
        var account = brokerClient.getAccount(accountNumber);
        System.out.printf("Login succeeded. Account %s — equity: $%.2f, buying power: $%.2f%n",
                account.accountNumber(), account.equity(), account.buyingPower());
        System.out.println("Session cached to robinhood.session.json for reuse by future runs, including the systemd service.");
    }

    /**
     * Credential-free, deterministic signal computation — no Robinhood auth involved.
     * Prints one "SIGNAL SYMBOL BUY|SELL PRICE" line per symbol with a fresh crossover
     * dated today, "NO_SIGNALS" if none, or "MARKET_CLOSED" and exits immediately if
     * outside regular US market hours. Designed to be run by an orchestrator (e.g. a
     * scheduled agent) that then decides what, if anything, to actually trade via a
     * separate execution channel (e.g. the Robinhood MCP connector) — this command
     * itself never places an order.
     */
    private static void runSignalCheck() {
        if (!MarketHours.isOpen()) {
            System.out.println("MARKET_CLOSED");
            return;
        }

        MarketDataClient marketDataClient = new YahooFinanceMarketDataClient();
        Strategy strategy = new MovingAverageCrossoverStrategy(20, 50);
        LocalDate today = LocalDate.now();
        LocalDate historyStart = today.minusDays(120);

        boolean anySignal = false;
        for (String symbol : WATCHLIST) {
            try {
                List<Bar> bars = marketDataClient.getDailyHistory(symbol, historyStart, today);
                List<Signal> signals = strategy.generateSignals(symbol, bars);
                if (signals.isEmpty()) {
                    continue;
                }
                Signal latest = signals.get(signals.size() - 1);
                if (latest.date().equals(today)) {
                    System.out.printf("SIGNAL %s %s %.2f%n", symbol, latest.type(), latest.price());
                    anySignal = true;
                }
            } catch (Exception e) {
                System.out.println("ERROR " + symbol + " " + e.getMessage());
            }
        }

        if (!anySignal) {
            System.out.println("NO_SIGNALS");
        }
    }

    static ConfirmationPrompt resolveConfirmationPrompt(String approvalMode) {
        return "auto".equalsIgnoreCase(approvalMode)
                ? new AutoApproveConfirmationPrompt()
                : new ConsoleConfirmationPrompt();
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
