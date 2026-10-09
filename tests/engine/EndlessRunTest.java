package engine;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import engine.EndlessRun.Body;
import engine.EndlessRun.Phase;

/** Dependency-free regression suite: java -ea -cp bin engine.EndlessRunTest. */
public final class EndlessRunTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        countdownAndPause();
        scoringAndDifficulty();
        bossTriggersAndRewards();
        collisionAndDeath();
        wavesAndReset();
        persistence();
        soak();
        shortKeyPress();
        System.out.println("PASS: " + checks + " endless regression checks");
    }

    private static EndlessRun fresh() { return new EndlessRun(432, 497, new Random(42)); }
    private static EndlessRun active() throws Exception {
        EndlessRun run = fresh();
        set(run, "phase", Phase.PLAYING);
        return run;
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
    @SuppressWarnings("unchecked")
    private static List<Body> bodies(EndlessRun run, String name) throws Exception {
        Field field = EndlessRun.class.getDeclaredField(name);
        field.setAccessible(true);
        return (List<Body>) field.get(run);
    }
    private static void advance(EndlessRun run, int frames, int move, boolean fire) {
        for (int i = 0; i < frames; i++) run.update(1.0 / 60, move, fire);
    }
    private static void countdownAndPause() throws Exception {
        EndlessRun run = fresh();
        advance(run, 120, 1, true);
        check(run.getPhase() == Phase.READY && run.getSeconds() == 0, "Countdown is not survival");
        check(run.getShots().isEmpty(), "No firing in countdown");
        run.togglePause();
        int countdown = run.getCountdown();
        advance(run, 600, -1, true);
        check(run.getCountdown() == countdown, "Paused countdown frozen");
        run.togglePause();
        advance(run, 70, 0, false);
        check(run.getPhase() == Phase.PLAYING, "Countdown resumes");
        run.update(0.05, 1, true);
        double x = run.getPlayer().x, y = run.getShots().get(0).y;
        long score = run.getScore();
        run.togglePause();
        advance(run, 600, 1, true);
        check(run.getPlayer().x == x && run.getShots().get(0).y == y, "Paused actors frozen");
        check(run.getScore() == score, "Paused score frozen");
        run.togglePause();
        run.update(0.05, 1, false);
        check(run.getPlayer().x > x, "Resume works");
    }
    private static void scoringAndDifficulty() throws Exception {
        EndlessRun run = active();
        set(run, "elapsed", 120.0);
        set(run, "kills", 20);
        check(run.getScore() == 3200, "KFC scoring example");
        check(run.getDifficulty() == 1.20, "5 percent per 30 seconds");
        run.update(Double.NaN, 0, false);
        run.update(-1, 0, false);
        check(run.getSeconds() == 120, "Invalid deltas ignored");
        run.update(60, 1, false);
        check(run.getSeconds() == 120, "Lag does not award idle survival time");
        advance(run, 200, -1, false);
        check(run.getPlayer().x >= 8, "Left boundary");
        advance(run, 300, 1, false);
        check(run.getPlayer().x + 26 <= 424, "Right boundary");
    }
    private static void bossTriggersAndRewards() throws Exception {
        EndlessRun run = active();
        set(run, "bossClock", 89.99);
        run.update(0.02, 0, false);
        check(run.getBoss() != null, "90 seconds triggers boss mid-wave");
        double formationX = run.getEnemies().get(0).x;
        advance(run, 60, 0, false);
        check(run.getEnemies().get(0).x == formationX, "Formation freezes during boss");
        long count = run.getEnemies().stream().filter(e -> e.boss).count();
        check(count == 1, "Only one boss active");
        run = active();
        set(run, "cycleKills", 30);
        run.update(0.01, 0, false);
        check(run.getBoss() != null, "30 kills triggers boss");
        Body boss = run.getBoss();
        run.update(0.01, 0, true);
        Body shot = run.getShots().get(0);
        shot.x = boss.x + 20;
        shot.y = boss.y + 15;
        boss.hp = 1;
        run.update(0.001, 0, false);
        check(run.getBoss() == null && run.getBosses() == 1, "Boss defeated once");
        check(run.getCycleKills() == 0 && run.getBossSecondsLeft() == 90, "Both triggers reset");
        check(run.getDamageUpgrades() + run.getSpeedUpgrades() + run.getProjectiles() - 1 == 1,
                "Exactly one gameplay upgrade");
        check(run.getShots().isEmpty() && run.getPhase() == Phase.INTERMISSION, "Safe reward break");
        check(run.getKills() == 1, "Boss counts as one kill");
        advance(run, 130, 0, false);
        check(run.getBosses() == 1 && run.getBoss() == null, "Reward cannot repeat");
    }
    private static void collisionAndDeath() throws Exception {
        EndlessRun run = active();
        Body target = run.getEnemies().get(0);
        Body second = run.getEnemies().get(1);
        second.x = target.x;
        second.y = target.y;
        run.update(0.001, 0, true);
        Body shot = run.getShots().get(0);
        shot.x = target.x;
        shot.y = target.y + 4;
        run.update(0.001, 0, false);
        check(run.getKills() == 1, "One bullet never kills two overlapping targets");
        run = active();
        set(run, "enemyShotClock", 0.0);
        run.update(0.001, 0, false);
        shot = run.getShots().get(0);
        shot.x = run.getPlayer().x;
        shot.y = run.getPlayer().y;
        set(run, "invulnerability", 0.0);
        run.update(0.001, 0, false);
        check(run.getLives() == 2 && run.isInvulnerable(), "Hit grants protection");
        set(run, "enemyShotClock", 0.0);
        run.update(0.001, 0, false);
        shot = run.getShots().get(0);
        shot.x = run.getPlayer().x;
        shot.y = run.getPlayer().y;
        run.update(0.001, 0, false);
        check(run.getLives() == 2, "Protection prevents chained hits");
        set(run, "lives", 1);
        set(run, "invulnerability", 0.0);
        set(run, "enemyShotClock", 0.0);
        run.update(0.001, 0, false);
        shot = run.getShots().get(0);
        shot.x = run.getPlayer().x;
        shot.y = run.getPlayer().y;
        run.update(0.001, 0, false);
        check(run.getPhase() == Phase.OVER && run.getLives() == 0, "Last hit ends run");
        long score = run.getScore();
        advance(run, 500, 0, true);
        check(run.getScore() == score, "No scoring after death");
        run = active();
        run.getEnemies().get(0).y = run.getPlayer().y;
        run.update(0.001, 0, false);
        check(run.getPhase() == Phase.OVER, "Formation breach ends run");
    }
    private static void wavesAndReset() throws Exception {
        EndlessRun run = active();
        set(run, "wave", 10);
        bodies(run, "enemies").clear();
        run.update(0.001, 0, false);
        check(run.getWave() == 11 && run.getPhase() == Phase.INTERMISSION, "Continues beyond campaign");
        check(run.getEnemies().size() <= 35, "Formation bounded");
        run = active();
        set(run, "wave", 3);
        set(run, "lives", 2);
        bodies(run, "enemies").clear();
        run.update(0.001, 0, false);
        check(run.getLives() == 3, "Repair after three cleared waves");
        run.finish("RUN ENDED");
        run = fresh();
        check(run.getWave() == 1 && run.getScore() == 0 && run.getLives() == 3
                && run.getProjectiles() == 1 && run.getBosses() == 0, "Retry starts clean");
    }
    private static void persistence() throws Exception {
        Path directory = Files.createTempDirectory("endless-tests-");
        Path file = directory.resolve("records");
        try {
            EndlessRecords records = new EndlessRecords(file);
            check(records.getScore() == 0, "Missing file defaults");
            EndlessRun run = active();
            set(run, "elapsed", 120.0);
            set(run, "kills", 20);
            check(records.save(run), "Record saved");
            records = new EndlessRecords(file);
            check(records.getScore() == 3200 && records.getSeconds() == 120 && records.getKills() == 20,
                    "Records survive restart");
            records.save(fresh());
            check(new EndlessRecords(file).getScore() == 3200, "Lower run cannot replace best");
            Files.write(file, "score=broken".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            check(new EndlessRecords(file).getScore() == 0, "Corrupt file does not crash");
            check(!new EndlessRecords(directory.resolve("absent/records")).save(run), "Save failure handled");
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(directory); }
    }
    private static void soak() throws Exception {
        EndlessRun run = active();
        for (int i = 0; i < 60000; i++) {
            if (run.getPhase() == Phase.OVER) run = active();
            run.update(1.0 / 60, (i / 90) % 2 == 0 ? 1 : -1, true);
            if (run.getEnemies().size() > 36 || run.getShots().size() > 200)
                throw new AssertionError("Unbounded actor growth");
        }
        check(true, "60,000 frame soak, no unbounded actors or exceptions");
    }

    private static void shortKeyPress() {
        InputManager input = Core.getInputManager();
        java.awt.Canvas source = new java.awt.Canvas();
        java.awt.event.KeyEvent press = new java.awt.event.KeyEvent(source,
                java.awt.event.KeyEvent.KEY_PRESSED, 0, 0,
                java.awt.event.KeyEvent.VK_P, 'p');
        java.awt.event.KeyEvent release = new java.awt.event.KeyEvent(source,
                java.awt.event.KeyEvent.KEY_RELEASED, 0, 0,
                java.awt.event.KeyEvent.VK_P, 'p');
        input.keyPressed(press);
        input.keyReleased(release);
        check(!input.isKeyDown(java.awt.event.KeyEvent.VK_P)
                && input.consumeKeyPress(java.awt.event.KeyEvent.VK_P), "Short tap survives between frames");
        check(!input.consumeKeyPress(java.awt.event.KeyEvent.VK_P), "Press consumed once");
        input.keyPressed(press);
        input.consumeKeyPress(java.awt.event.KeyEvent.VK_P);
        input.keyPressed(press);
        check(!input.consumeKeyPress(java.awt.event.KeyEvent.VK_P), "Held key repeat ignored");
        input.keyReleased(release);
    }
}
