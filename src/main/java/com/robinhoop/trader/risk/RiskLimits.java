package com.robinhoop.trader.risk;

/** Position and daily-loss caps expressed as a fraction of account equity (0.05 = 5%). */
public record RiskLimits(double maxPositionPct, double maxDailyLossPct) {

    private static final double DEFAULT_MAX_POSITION_PCT = 0.05;
    private static final double DEFAULT_MAX_DAILY_LOSS_PCT = 0.02;

    public RiskLimits {
        if (maxPositionPct <= 0 || maxPositionPct > 1) {
            throw new IllegalArgumentException("maxPositionPct must be in (0, 1]");
        }
        if (maxDailyLossPct <= 0 || maxDailyLossPct > 1) {
            throw new IllegalArgumentException("maxDailyLossPct must be in (0, 1]");
        }
    }

    public static RiskLimits fromEnvironment() {
        return new RiskLimits(
                envDoubleOrDefault("MAX_POSITION_PCT", DEFAULT_MAX_POSITION_PCT),
                envDoubleOrDefault("MAX_DAILY_LOSS_PCT", DEFAULT_MAX_DAILY_LOSS_PCT)
        );
    }

    private static double envDoubleOrDefault(String key, double defaultValue) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? defaultValue : Double.parseDouble(value);
    }
}
