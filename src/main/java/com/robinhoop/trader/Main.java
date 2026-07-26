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
import com.robinhoop.trader.execution.OrderPlanner;
import com.robinhoop.trader.execution.PlanLine;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            case "order-plan" -> runOrderPlan(Arrays.copyOfRange(args, 1, args.length));
            default -> System.out.println(
                    "Unknown mode: " + mode + ". Supported modes: backtest, live, login-test, signal-check, order-plan");
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

        List<Signal> signals = fetchTodaysSignals();
        if (signals.isEmpty()) {
            System.out.println("NO_SIGNALS");
            return;
        }
        for (Signal signal : signals) {
            System.out.printf("SIGNAL %s %s %.2f%n", signal.symbol(), signal.type(), signal.price());
        }
    }

    /**
     * Full order-sizing pipeline: computes today's signals, then runs them through
     * {@link OrderPlanner} — the position-cap and aggregate-cap money math — so the
     * caller (a scheduled agent) only ever executes an exact, pre-computed plan rather
     * than doing that arithmetic itself. Never calls Robinhood; the caller supplies
     * current account value and positions and is responsible for actually placing
     * whatever ORDER lines this prints.
     *
     * Usage: order-plan &lt;account_value&gt;, with current positions piped via stdin as
     * one "SYMBOL QUANTITY" line per held position.
     */
    private static void runOrderPlan(String[] args) {
        if (args.length < 1) {
            System.out.println("ERROR missing account_value argument");
            return;
        }
        double accountValue;
        try {
            accountValue = Double.parseDouble(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("ERROR invalid account_value: " + args[0]);
            return;
        }

        Map<String, Double> positions = readPositionsFromStdin();

        if (!MarketHours.isOpen()) {
            System.out.println("MARKET_CLOSED");
            return;
        }

        List<Signal> signals = fetchTodaysSignals();
        if (signals.isEmpty()) {
            System.out.println("NO_SIGNALS");
            return;
        }

        double positionCapPct = envDoubleOrDefault("POSITION_CAP_PCT", 0.20);
        double aggregateCapPct = envDoubleOrDefault("AGGREGATE_CAP_PCT", 0.40);
        OrderPlanner planner = new OrderPlanner(positionCapPct, aggregateCapPct);
        for (PlanLine line : planner.plan(signals, accountValue, positions)) {
            System.out.println(line.toOutputLine());
        }
    }

    private static List<Signal> fetchTodaysSignals() {
        MarketDataClient marketDataClient = new YahooFinanceMarketDataClient();
        Strategy strategy = new MovingAverageCrossoverStrategy(20, 50);
        LocalDate today = LocalDate.now();
        LocalDate historyStart = today.minusDays(120);

        List<Signal> todaysSignals = new ArrayList<>();
        for (String symbol : WATCHLIST) {
            try {
                List<Bar> bars = marketDataClient.getDailyHistory(symbol, historyStart, today);
                List<Signal> signals = strategy.generateSignals(symbol, bars);
                if (signals.isEmpty()) {
                    continue;
                }
                Signal latest = signals.get(signals.size() - 1);
                if (latest.date().equals(today)) {
                    todaysSignals.add(latest);
                }
            } catch (Exception e) {
                System.out.println("ERROR " + symbol + " " + e.getMessage());
            }
        }
        return todaysSignals;
    }

    private static Map<String, Double> readPositionsFromStdin() {
        Map<String, Double> positions = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] parts = line.split("\\s+");
                if (parts.length != 2) {
                    continue;
                }
                positions.put(parts[0].toUpperCase(), Double.parseDouble(parts[1]));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read positions from stdin", e);
        }
        return positions;
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

    private static double envDoubleOrDefault(String key, double defaultValue) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? defaultValue : Double.parseDouble(value);
    }
}
