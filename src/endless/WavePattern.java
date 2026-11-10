package endless;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Endless-owned wave geometry and pacing; no campaign formation dependencies. */
public final class WavePattern {
    public enum Formation { GRID, TRIANGLE, V_SHAPE, DIAMOND, STAGGERED }
    public enum Movement { SWEEP, ZIGZAG, SINE_WAVE }
    public enum Attack { SINGLE, AIMED, FAN }

    public static final class Slot {
        public final int x, y;
        public final double phase;
        private Slot(int x, int y, double phase) {
            this.x = x;
            this.y = y;
            this.phase = phase;
        }
    }

    public final Formation formation;
    public final Movement movement;
    public final Attack attack;
    private final int columns;

    private WavePattern(Formation formation, Movement movement, Attack attack, int columns) {
        this.formation = formation;
        this.movement = movement;
        this.attack = attack;
        this.columns = columns;
    }

    /** Boss waves do not consume an entry in the ordinary formation rotation. */
    public static WavePattern forWave(int wave) {
        if (wave < 1 || wave % 5 == 0)
            throw new IllegalArgumentException("Expected a positive non-boss wave");
        int index = wave - 1 - (wave - 1) / 5;
        Attack attack = wave < 3 ? Attack.SINGLE : wave < 6 ? Attack.AIMED
                : Attack.values()[index % Attack.values().length];
        return new WavePattern(Formation.values()[index % Formation.values().length],
                Movement.values()[index % Movement.values().length], attack,
                Math.min(7, 4 + (wave - 1) / 3));
    }

    /** Keeps the existing 12..35 enemy progression, including very long runs. */
    public static int enemyCount(int wave) {
        return 12 + (int) Math.min(23L, Math.max(0L, ((long) wave - 1) * 2));
    }

    /** Centers the whole shape and keeps a gap above the player's starting row. */
    public List<Slot> slots(int count, int width, int height, int shipWidth, int shipHeight) {
        if (count < 1 || count > 35)
            throw new IllegalArgumentException("Expected 1..35 enemies");
        int[] rows;
        if (formation == Formation.TRIANGLE) {
            int[] weights = new int[(int) Math.ceil(Math.sqrt(count))];
            for (int i = 0; i < weights.length; i++) weights[i] = 2 * i + 1;
            rows = distribute(count, weights);
        } else if (formation == Formation.DIAMOND) {
            rows = distribute(count, count <= 24 ? new int[]{1, 3, 5, 3, 1}
                    : new int[]{1, 3, 5, 7, 5, 3, 1});
        } else {
            int rowWidth = formation == Formation.V_SHAPE ? Math.min(7, count) : columns;
            rows = new int[(count + rowWidth - 1) / rowWidth];
            for (int i = 0; i < rows.length; i++) rows[i] = Math.min(rowWidth, count - i * rowWidth);
        }
        int widest = 1;
        for (int row : rows) widest = Math.max(widest, row);
        double stagger = formation == Formation.STAGGERED ? .5 : 0;
        double gapX = Math.min(40, (width - 48.0 - shipWidth) / Math.max(1, widest - 1 + stagger));
        double gapY = Math.max(29, shipHeight + 10);
        if (gapX < shipWidth + 4)
            throw new IllegalArgumentException("Enemy sprites are too wide for this arena");
        List<double[]> raw = new ArrayList<double[]>();
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY, maxY = 0;
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < rows[row]; column++) {
                double center = (rows[row] - 1) / 2.0;
                double x = (column - center + (row % 2 == 0 ? 0 : stagger)) * gapX;
                double y = row * gapY;
                if (formation == Formation.V_SHAPE)
                    y += ((widest - 1) / 2.0 - Math.abs(column - center)) * 18;
                raw.add(new double[]{x, y});
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        double availableHeight = height - 52 - 70 - 150 - shipHeight;
        if (maxY > availableHeight)
            throw new IllegalArgumentException("Formation is too tall for this arena");
        List<Slot> result = new ArrayList<Slot>();
        double left = (width - shipWidth - (maxX - minX)) / 2.0;
        for (double[] position : raw)
            result.add(new Slot((int) Math.round(left + position[0] - minX),
                    150 + (int) Math.round(position[1]), position[0] / gapX * .65));
        return Collections.unmodifiableList(result);
    }

    private static int[] distribute(int count, int[] weights) {
        int sum = 0;
        for (int weight : weights) sum += weight;
        int[] rows = new int[weights.length];
        for (int allocated = 0; allocated < count; allocated++) {
            int best = 0;
            for (int i = 1; i < rows.length; i++)
                if (count * weights[i] / (double) sum - rows[i]
                        > count * weights[best] / (double) sum - rows[best]) best = i;
            rows[best]++;
        }
        return rows;
    }

    /** Offsets are absolute, so a sine/zigzag cycle cannot accumulate downward drift. */
    public int verticalOffset(double activeSeconds, double phase) {
        if (movement == Movement.ZIGZAG)
            return (int) Math.round(12 * (1 - Math.abs((activeSeconds % 4) / 2 - 1)));
        if (movement == Movement.SINE_WAVE)
            return (int) Math.round(6 * (Math.sin(activeSeconds * Math.PI / 2 + phase) - Math.sin(phase)));
        return 0;
    }

    public double shotDelayMultiplier() { return attack == Attack.FAN ? 1.6 : attack == Attack.AIMED ? 1.15 : 1; }

    public String label() {
        return formation.name().replace('_', ' ') + " / " + movement.name().replace('_', ' ')
                + " / " + attack.name();
    }
}
