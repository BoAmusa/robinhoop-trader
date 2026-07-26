# robinhoop-trader

A moving-average-crossover trading bot for a Robinhood account, built as an exercise in
getting the *engineering* right (strategy validation, risk limits, safe execution) —
not as a strategy with a proven expectation of profit. See [Strategy caveats](#strategy-caveats).

## How it actually runs

Trading is **not** done by this repo running as a server somewhere. It's driven by a
**scheduled Claude cloud agent ("routine")** that:

1. Wakes up on a cron schedule (currently `35 20 * * 1-5` — 4:35pm ET, weekdays, shortly
   after the market close; needs shifting to `35 21 * * 1-5` when Daylight Saving ends).
   This strategy operates on daily bars, so one run shortly after close — evaluated
   against the day's final, settled price — is more correct than checking intraday
   against Yahoo's still-forming current-day bar, which can flicker before the close
   actually settles it. Any resulting order is placed after-hours and executes at the
   next session's open — standard, well-supported brokerage behavior, though this
   specific path (after-hours submission via the Robinhood MCP tools) hasn't yet been
   exercised with a real signal.
2. Clones this repo into a fresh, isolated cloud sandbox (no state carried over between runs).
3. Builds the Java app and runs `signal-check` — deterministic, credential-free crossover
   signal computation (see below).
4. If `signal-check` reports the market's closed or there's nothing new, it stops there.
5. If there's a fresh signal, it uses the **Robinhood Agentic Trading MCP connector**
   (`https://agent.robinhood.com/mcp/trading`) to check current account equity/positions,
   size the trade against a hard 5% position cap, run Robinhood's own pre-trade order
   review, and place the order if that review is clean.

No credentials are stored or handled anywhere in this flow — the Robinhood MCP connection
authenticates via OAuth once, outside this repo entirely, the same way connecting Robinhood
to Claude/ChatGPT/Cursor/Grok does for any user.

Since each run is a stateless, fresh sandbox, "did I already act on this signal today" is
answered by checking Robinhood's own current positions (already holding it → skip a BUY;
nothing to sell → skip a SELL) rather than any local tracker file.

**The kill switch is disabling the routine** — there's no local process or file to touch.

## The Java app (`com.robinhoop.trader`)

| Mode | Needs Robinhood creds? | What it does |
|---|---|---|
| `backtest` | No | Runs the strategy against historical data (free Yahoo Finance API) across several market regimes and prints performance vs. buy-and-hold. |
| `signal-check` | No | The mode the cloud routine actually calls. Checks real market hours, then prints today's fresh crossover signals (or `MARKET_CLOSED` / `NO_SIGNALS`) and exits. Never touches a broker. |
| `login-test` | Yes | Read-only connectivity check for the *legacy* direct-login path (see below). Not used by the current routine. |
| `live` | Yes | A **legacy**, self-contained live-trading loop that logs into Robinhood directly (unofficial, reverse-engineered API) and manages its own risk/session state on local disk. Superseded by the MCP routine — kept for reference, not currently deployed anywhere. |

Run a backtest locally:
```
mvn package
java -jar target/robinhoop-trader-1.0-SNAPSHOT.jar backtest
```

## Strategy

20/50-day simple moving average crossover (`MovingAverageCrossoverStrategy`): BUY when the
short MA crosses above the long MA, SELL on the reverse cross. Watchlist is fixed in `Main.java`:
SPY, QQQ, AAPL, MSFT, NVDA, AMZN, GOOGL, META, AMD, TSLA.

### Strategy caveats

Backtesting (`BacktestEngineTest`, and running `backtest` mode across 2020-2026) showed this
crossover **consistently underperforms buy-and-hold** in trending bull markets — it gives back
significant upside because it lags entries/exits (e.g. 5yr NVDA: 263% strategy vs. 972%
buy-and-hold). Its one real value is trimming losses in a sustained decline (2022: smaller
losses than buy-and-hold in 8/10 watchlist names, though still negative in all 10). Treat this
as validated *machinery*, not a strategy with real alpha — a 20/50 MA crossover is one of the
most well-known setups there is, and any edge it may have had is thoroughly arbitraged away.

## Risk controls

- **Position cap**: max 20% of current account equity per trade (hard-coded in the routine's prompt; raised from an initial 5% once the account was funded beyond trivial test money).
- **Aggregate per-run cap**: max 40% of current account equity in *new* BUY orders within a single run, tracked via a running `deployed_this_run` counter the agent maintains for that run only. This exists because the watchlist has correlated names (SPY/QQQ plus several mega-cap tech stocks) — without it, a single broad market move could trigger BUY signals on several symbols at once and commit 80%+ of the account in one run. SELL orders don't count against this cap.
- **No daily loss halt**: dropped for now — Robinhood's MCP tools don't expose a day-over-day
  equity change, and the account has no trading history to derive a baseline from. Revisit if
  the account is funded more meaningfully.
- **Kill switch**: disable the routine (`https://claude.ai/code/routines`).
- **Robinhood's own pre-trade review** (`review_equity_order`) is called before every order;
  the routine will not place an order if the review flags a warning.
- Currently running against a small (~$33) balance in a dedicated Robinhood "Agentic" account,
  intentionally, to validate the whole flow with minimal real money at stake.

## Other files in this repo

`Dockerfile`, `render.yaml`, and `deploy/` (systemd unit, `.env.example`) are from earlier,
**abandoned** hosting approaches (Render Background Worker, a self-hosted VPS running the
`live` mode's direct Robinhood login). They're kept for reference but aren't part of how this
actually runs today — see "How it actually runs" above. The direct-login approach in particular
was abandoned because Robinhood's fraud detection flags automated logins from datacenter IPs
using an unofficial/reverse-engineered client — the MCP-based routine avoids this entirely by
using Robinhood's own sanctioned agentic-trading integration instead.
