package endless;

/**
 * When a boss-only wave replaces the next ordinary endless wave. Chosen on the entry panel.
 *
 * To add or retune a rule, edit the constants below: each one names what it counts and how
 * many of it separate two bosses. Everything else (entry panel text, HUD gauge, scheduling)
 * follows from those two values.
 */
public enum BossRule {
    /** Waves 5, 10, 15, ... are boss waves. */
    EVERY_FIVE_WAVES(Counter.WAVES, 5, "5 WAVES"),
    /** The wave after 90 active seconds since the last boss fell. */
    EVERY_90_SECONDS(Counter.SECONDS, 90, "90 SECONDS"),
    /** The wave after 30 kills since the last boss fell. */
    EVERY_30_KILLS(Counter.KILLS, 30, "30 KILLS");

    /** What a rule counts towards its next boss. */
    public enum Counter { WAVES, SECONDS, KILLS }

    public final Counter counter;
    /** How many waves, seconds or kills separate two bosses. */
    public final int interval;
    /** Text shown on the entry panel. */
    public final String label;

    BossRule(Counter counter, int interval, String label) {
        this.counter = counter;
        this.interval = interval;
        this.label = label;
    }

    /** The rule the entry panel switches to next. */
    public BossRule next() { return values()[(ordinal() + 1) % values().length]; }

    /** Fresh per-run bookkeeping for this rule. */
    public Schedule schedule() { return new Schedule(this); }

    /**
     * One run's boss timing. The run reports its own wave number, active seconds and kills;
     * this class owns no clock, so pauses and reward screens cannot advance it.
     *
     * Wave rules place bosses on fixed wave numbers. Second and kill rules make the wave
     * after the threshold a boss wave (never mid-wave) and start counting again when that
     * boss is defeated, so the fight itself does not count towards the next one.
     */
    public static final class Schedule {
        private final BossRule rule;
        private boolean bossWave;
        /** Second/kill rules only: the counter's value when the last boss fell. */
        private double mark;
        /** Ordinary (non-boss) waves started so far. */
        private int ordinaryWaves;

        private Schedule(BossRule rule) { this.rule = rule; }

        private boolean fixedWaves() { return rule.counter == Counter.WAVES; }

        private double counted(double seconds, int kills) {
            return rule.counter == Counter.SECONDS ? seconds : kills;
        }

        /** Seconds or kills gathered since the last boss fell. */
        private double gathered(double seconds, int kills) { return counted(seconds, kills) - mark; }

        private boolean due(double seconds, int kills) { return gathered(seconds, kills) >= rule.interval; }

        /** Call once when a wave starts. Returns whether it is a boss wave. */
        public boolean beginWave(int wave, double seconds, int kills) {
            bossWave = fixedWaves() ? wave % rule.interval == 0 : due(seconds, kills);
            if (!bossWave) ordinaryWaves++;
            return bossWave;
        }

        /** Call when a wave is cleared, before the next one begins. */
        public void waveCleared(double seconds, int kills) {
            if (bossWave && !fixedWaves()) mark = counted(seconds, kills);
        }

        /**
         * Wave number to give WavePattern for the current ordinary wave. WavePattern was
         * written for a boss on every fifth wave and rejects multiples of five, so any other
         * spacing walks the same non-boss sequence (1-4, 6-9, 11-14, ...) without gaps.
         */
        public int patternWave(int wave) {
            if (fixedWaves() && rule.interval == 5) return wave;
            return ordinaryWaves + (ordinaryWaves - 1) / 4;
        }

        /**
         * Stage passed to the boss factory, which builds the final boss on multiples of ten
         * and the mid-boss otherwise: every rule alternates mid, final, mid, final, ...
         */
        public int bossStage(int wave, int bossesDefeated) {
            return (fixedWaves() ? wave / rule.interval : bossesDefeated + 1) * 5;
        }

        /** The next boss wave, or 0 while a second/kill rule has not reached its threshold. */
        public int nextBossWave(int wave, double seconds, int kills) {
            if (fixedWaves()) return (wave + rule.interval - 1) / rule.interval * rule.interval;
            return bossWave ? wave : due(seconds, kills) ? wave + 1 : 0;
        }

        /** HUD gauge towards the next boss, 0..1. */
        public double progress(int wave, double seconds, int kills) {
            if (fixedWaves()) return ((wave - 1) % rule.interval) / (double) Math.max(1, rule.interval - 1);
            return Math.max(0, Math.min(1, gathered(seconds, kills) / rule.interval));
        }

        /** Short HUD label: the boss wave, or what is still missing before the next boss. */
        public String status(int wave, double seconds, int kills) {
            if (fixedWaves()) return "BOSS W" + nextBossWave(wave, seconds, kills);
            if (due(seconds, kills)) return "BOSS NEXT";
            int missing = (int) Math.ceil(rule.interval - gathered(seconds, kills));
            return "BOSS " + missing + (rule.counter == Counter.SECONDS ? "S" : " KILLS");
        }
    }
}
