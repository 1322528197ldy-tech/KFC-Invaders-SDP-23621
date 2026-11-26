import engine.Cooldown;
import engine.Core;
import engine.GameSettings;
import engine.GameSettings.MovementPattern;
import engine.GameState;
import engine.soundEngine.SoundManager;
import entity.Bullet;
import entity.EnemyShip;
import entity.EnemyShipFormation;
import entity.MidBoss;
import item.ItemAPI.Bounds;
import item.ItemAPI.GrantRequest;
import item.ItemAPI.GrantSource;
import item.ItemAPI.GrantTiming;
import item.ItemAPI.PlayerSnapshot;
import item.ItemAPI.UseResult;
import item.ItemManager;
import item.ItemSystem;
import screen.GameScreen;
import screen.Screen;
import sun.misc.Unsafe;

import java.awt.Canvas;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Standalone campaign checks. Run with tests/campaign/verify-movement.ps1. */
public final class CampaignMovementTest {
    private static final int WIDTH = 448;
    private static final int HEIGHT = 520;
    private static final Canvas INPUT = new Canvas();
    private static int checks;

    private record Position(int x, int y) {}
    private record Progress(int patrolFrame, int interval, int diagonalOffset,
                            int diagonalStep, int offsetX, int offsetY) {}

    /** Same offscreen adapter as the existing Endless UI checks; no window is opened. */
    public static final class Offscreen extends engine.Frame {
        private static final long serialVersionUID = 1L;
        public Offscreen() { super(WIDTH, HEIGHT); }
        @Override public Graphics getGraphics() {
            return new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB).getGraphics();
        }
        @Override public Insets getInsets() { return new Insets(0, 0, 0, 0); }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static Object get(Object target, String name) throws Exception {
        return field(target.getClass(), name).get(target);
    }

    private static void call(GameScreen game, String name) throws Exception {
        Method method = GameScreen.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(game);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static GameSettings settings(int stage) throws Exception {
        return (GameSettings) field(Core.class, "SETTINGS_LEVEL_" + stage).get(null);
    }

    private static EnemyShipFormation formation(int stage, int width, int height) throws Exception {
        EnemyShipFormation formation = new EnemyShipFormation(settings(stage));
        formation.attach(new Screen(width, height, 60));
        return formation;
    }

    private static Map<EnemyShip, Position> positions(EnemyShipFormation formation) {
        Map<EnemyShip, Position> result = new HashMap<>();
        for (EnemyShip enemy : formation)
            result.put(enemy, new Position(enemy.getPositionX(), enemy.getPositionY()));
        return result;
    }

    private static Position displacement(EnemyShipFormation formation,
            Map<EnemyShip, Position> starts, int width, int height) {
        Position result = null;
        int count = 0;
        for (EnemyShip enemy : formation) {
            Position start = starts.get(enemy);
            check(start != null, "Only surviving original enemies remain");
            Position current = new Position(enemy.getPositionX() - start.x(),
                    enemy.getPositionY() - start.y());
            if (result == null) result = current;
            check(result.equals(current), "Formation spacing is preserved");
            check(enemy.getPositionX() >= 20 && enemy.getPositionX() + enemy.getWidth() <= width - 20,
                    "Enemy stays inside horizontal screen bounds");
            check(enemy.getPositionY() >= 40 && enemy.getPositionY() + enemy.getHeight() <= height - 80,
                    "Enemy stays below the HUD and above the player area");
            count++;
        }
        check(count == starts.size() && count > 0, "Expected surviving enemy count");
        return result;
    }

    private static Progress progress(EnemyShipFormation formation) throws Exception {
        return new Progress((Integer) get(formation, "patrolFrame"),
                (Integer) get(formation, "movementInterval"),
                (Integer) get(formation, "diagonalPatrolOffset"),
                (Integer) get(formation, "diagonalPatrolStep"),
                (Integer) get(formation, "patrolOffsetX"),
                (Integer) get(formation, "patrolOffsetY"));
    }

    private static void removeEdge(EnemyShipFormation formation,
            Map<EnemyShip, Position> starts, int stage, int frame) throws Exception {
        int right = spawnX(stage, settings(stage).getFormationWidth() - 1);
        int top = starts.values().stream().mapToInt(Position::y).min().orElseThrow();
        List<EnemyShip> removed = starts.entrySet().stream()
                .filter(entry -> frame == 225 ? entry.getValue().x() == 20
                        : frame == 405 ? entry.getValue().y() == top
                        : entry.getValue().x() == right)
                .map(Map.Entry::getKey).toList();
        check(!removed.isEmpty(), "The selected edge contains enemies");
        for (EnemyShip enemy : removed) {
            formation.destroy(enemy);
            starts.remove(enemy);
        }
        formation.removeDestroyed();
    }

    private static int spawnX(int stage, int column) {
        int gap = switch (stage) {
            case 3 -> column >= 2 ? 40 : 0;
            case 4, 6, 7 -> column / 2 * 16;
            case 9 -> column >= 3 ? 40 : 0;
            default -> 0;
        };
        return 20 + 40 * column + gap;
    }

    private static int spawnY(int stage, int column, int row) {
        int depth = switch (stage) {
            case 2 -> column * 8;
            case 4 -> column == 2 || column == 3 ? 24 : 0;
            case 6 -> column / 2 * 16;
            case 7 -> column < 2 || column >= 4 ? 32 : 0;
            case 8 -> column % 2 * 24;
            case 9 -> column >= 3 ? 32 : 0;
            default -> 0;
        };
        return 100 + row * 40 + depth;
    }

    private static int span(int stage, int width) throws Exception {
        return width - 44 - spawnX(stage, settings(stage).getFormationWidth() - 1);
    }

    private static int travelY(int stage, int height, boolean centered) throws Exception {
        int bottom = 0;
        GameSettings config = settings(stage);
        for (int column = 0; column < config.getFormationWidth(); column++)
            bottom = Math.max(bottom, spawnY(stage, column, config.getFormationHeight() - 1) + 16);
        int available = height - 80 - bottom;
        return centered ? Math.min(120, 2 * Math.min(60, available)) : Math.min(120, available);
    }

    private static void checkShape(int stage, int frame, Position offset, int width, int height) throws Exception {
        if (stage == 1) {
            check(offset.x() == offset.y() && offset.x() >= 0 && offset.x() <= 120,
                    "Stage 1 follows its diagonal segment");
            if (frame % 600 == 300)
                check(offset.equals(new Position(120, 120)), "Stage 1 reaches the far endpoint");
            if (frame % 600 == 0)
                check(offset.equals(new Position(0, 0)), "Stage 1 completes its return trip");
            return;
        }
        if (frame % 720 == 0)
            check(offset.equals(new Position(0, 0)), "Stage " + stage + " closes the 720-update loop");
        int travelX = switch (stage) {
            case 2, 4 -> span(stage, width);
            default -> 160;
        };
        int depth = travelY(stage, height, stage != 4);
        if (stage == 2) {
            double halfWidth = travelX / 2.0;
            double diamond = Math.abs((offset.x() - halfWidth) / halfWidth) + Math.abs(offset.y() / 60.0);
            check(Math.abs(diamond - 1) < 0.02, "Stage 2 follows the diamond edges");
        } else if (stage == 3) {
            double ellipse = Math.pow((offset.x() - 80) / 80.0, 2)
                    + Math.pow(offset.y() / 60.0, 2);
            check(Math.abs(ellipse - 1) < 0.03, "Stage 3 follows the ellipse");
        } else {
            check(offset.x() == 0 || offset.x() == travelX || offset.y() == 0 || offset.y() == depth,
                    "Stage 4 follows the rectangle edges");
        }
        if (frame <= 720 && frame % 180 == 0) {
            Position[] quarters = switch (stage) {
                case 2 -> new Position[] {new Position(travelX / 2, -60), new Position(travelX, 0),
                        new Position(travelX / 2, 60), new Position(0, 0)};
                case 3 -> new Position[] {new Position(80, 60), new Position(160, 0),
                        new Position(80, -60), new Position(0, 0)};
                default -> new Position[] {new Position(travelX, 0), new Position(travelX, depth),
                        new Position(0, depth), new Position(0, 0)};
            };
            check(offset.equals(quarters[frame / 180 - 1]), "Stage " + stage + " quarter-loop waypoint");
        }
    }

    private static Map<Integer, Position> checkpoints(int stage, int width, int height) throws Exception {
        int across = span(stage, width);
        int depth = travelY(stage, height, stage == 7);
        return switch (stage) {
            case 6 -> Map.of(120, new Position(across, 30), 240, new Position(0, 90),
                    360, new Position(across, 120), 480, new Position(0, 90), 600, new Position(across, 30));
            case 7 -> Map.of(75, new Position((int) Math.round(across * (1 - Math.sqrt(.5)) / 2), depth / 2),
                    150, new Position(across / 2, 0),
                    225, new Position((int) Math.round(across * (1 + Math.sqrt(.5)) / 2), -depth / 2),
                    300, new Position(across, 0), 450, new Position(across / 2, 0));
            case 8 -> Map.of(90, new Position(72, 30), 180, new Position(144, 0),
                    270, new Position(144, 120), 360, new Position(72, 90), 450, new Position(0, 120));
            case 9 -> Map.of(120, new Position(across / 2, -60), 240, new Position(across, 0),
                    360, new Position(across / 2, 60), 560, new Position(across, 30), 720, new Position(across, 120),
                    1020, new Position((int) Math.round(across * (1 - Math.sqrt(.5)) / 2), 60),
                    1080, new Position(across / 2, 0), 1200, new Position(across, 0));
            default -> throw new IllegalArgumentException("Unexpected stage " + stage);
        };
    }

    private static void routeAndRemoval(int stage, int width, int height) throws Exception {
        EnemyShipFormation reference = formation(stage, width, height);
        EnemyShipFormation reduced = formation(stage, width, height);
        Map<EnemyShip, Position> original = positions(reference);
        Map<EnemyShip, Position> surviving = positions(reduced);
        GameSettings settings = settings(stage);
        check(original.size() == settings.getFormationWidth() * settings.getFormationHeight(),
                "Stage " + stage + " keeps its original enemy count");
        for (int column = 0; column < settings.getFormationWidth(); column++)
            for (int row = 0; row < settings.getFormationHeight(); row++)
                check(original.containsValue(new Position(spawnX(stage, column), spawnY(stage, column, row))),
                        "Stage " + stage + " uses its campaign layout");

        boolean wallPatrol = stage == 2 || stage == 4 || stage >= 6;
        boolean reachedNewLeft = false;
        boolean reachedNewRight = false;
        Position previous = new Position(0, 0);
        Position previousReduced = previous;
        int cycle = switch (stage) {
            case 1, 7 -> 600;
            case 8 -> 540;
            case 9 -> 1440; // Three successive 480-update routes.
            default -> 720;
        };
        Map<Integer, Position> landmarks = stage >= 6 ? checkpoints(stage, width, height) : Map.of();
        boolean leftRemoved = false;
        boolean rightRemoved = false;
        for (int frame = 1; frame <= cycle * 3; frame++) {
            reference.update();
            reduced.update();
            Position offset = displacement(reference, original, width, height);
            Position reducedOffset = displacement(reduced, surviving, width, height);
            if (!wallPatrol)
                check(offset.equals(reducedOffset), "Fixed patrol stays on course after edge removal");
            check(Math.abs(reducedOffset.x() - previousReduced.x()) <= 4
                            && Math.abs(reducedOffset.y() - previousReduced.y()) <= 4,
                    "No jump after edge removal at stage " + stage + ", frame " + frame);
            reachedNewLeft |= leftRemoved && surviving.entrySet().stream()
                    .anyMatch(entry -> entry.getKey().getPositionX() == 20);
            reachedNewRight |= rightRemoved && surviving.entrySet().stream()
                    .anyMatch(entry -> entry.getKey().getPositionX() + entry.getKey().getWidth() == width - 20);
            check(Math.abs(offset.x() - previous.x()) <= 4 && Math.abs(offset.y() - previous.y()) <= 4,
                    "No movement jump at stage " + stage + ", frame " + frame);
            if (stage <= 4) {
                checkShape(stage, frame, offset, width, height);
            } else {
                int phase = frame % cycle;
                if (phase == 0 || stage == 9 && phase % 480 == 0)
                    check(offset.equals(new Position(0, 0)), "Route closes at stage " + stage + ", frame " + frame);
                if (width == WIDTH && landmarks.containsKey(phase))
                    check(offset.equals(landmarks.get(phase)), "Wrong route at stage " + stage + ", frame " + frame);
            }
            // For wall patrols, remove the right column just before the old turning point.
            int rightRemovalFrame = switch (stage) {
                case 2 -> 359;
                case 4, 8 -> 179;
                case 6 -> 119;
                case 7 -> 299;
                default -> 239;
            };
            if (frame == 225 || frame == 405 || frame == (wallPatrol ? rightRemovalFrame : 585)) {
                removeEdge(reduced, surviving, stage, frame == rightRemovalFrame ? 585 : frame);
                leftRemoved |= frame == 225;
                rightRemoved |= frame == rightRemovalFrame;
                check(reducedOffset.equals(displacement(reduced, surviving, width, height)),
                        "Removing a whole edge does not move survivors immediately");
            }
            previous = offset;
            previousReduced = reducedOffset;
        }
        if (wallPatrol)
            check(reachedNewLeft && reachedNewRight,
                    "Survivors reach both newly available walls at stage " + stage);
        Cooldown shooting = (Cooldown) get(reduced, "shootingCooldown");
        field(Cooldown.class, "time").setLong(shooting, 0);
        Set<Bullet> bullets = new HashSet<>();
        reduced.shoot(bullets);
        check(bullets.size() == 1 && bullets.iterator().next().getSpeed() > 0,
                "Survivors can still shoot after route and edge cleanup");
    }

    private static void diamondKeepsDirectionAfterColumnRemoval(int width, int height,
            boolean movingRight, boolean approachingVertex, boolean clearLeft, boolean clearRight) throws Exception {
        EnemyShipFormation patrol = formation(2, width, height);
        Map<EnemyShip, Position> survivors = positions(patrol);
        int steps = movingRight ? 179 : 539;
        if (!approachingVertex)
            steps += 180;
        for (int frame = 0; frame < steps; frame++)
            patrol.update();

        // Destroy the selected columns together and let the normal update handle their explosions.
        for (EnemyShip enemy : List.copyOf(survivors.keySet())) {
            int x = survivors.get(enemy).x();
            if ((clearLeft && x == 20) || (clearRight && x == 180)) {
                patrol.destroy(enemy);
                survivors.remove(enemy);
            }
        }
        EnemyShip tracked = survivors.keySet().iterator().next();
        Position start = survivors.get(tracked);
        Position previous = new Position(tracked.getPositionX() - start.x(), tracked.getPositionY() - start.y());
        int wallOffset = movingRight ? width - 224 + (clearRight ? 40 : 0) : (clearLeft ? -40 : 0);
        boolean reachedVertex = false;
        for (int frame = 0; frame < 400; frame++) {
            patrol.update();
            Position current = new Position(tracked.getPositionX() - start.x(), tracked.getPositionY() - start.y());
            check(movingRight ? current.x() >= previous.x() : current.x() <= previous.x(),
                    "Clearing outer columns must not reverse the diamond before the wall");
            check(Math.abs(current.x() - previous.x()) <= 4 && Math.abs(current.y() - previous.y()) <= 4,
                    "The diamond stays continuous after clearing the trailing column");
            reachedVertex |= current.y() == (movingRight ? -60 : 60);
            if (current.x() == wallOffset && current.y() == 0) {
                check(!approachingVertex || reachedVertex, "The diamond still passes through the vertical vertex");
                return;
            }
            previous = current;
        }
        throw new AssertionError("The diamond did not continue to the wall");
    }

    private static void latePatrolReachesWallBeforeReversing(int stage, int width, int height,
            int clearFrame, boolean movingRight, boolean clearLeft, boolean clearRight) throws Exception {
        EnemyShipFormation patrol = formation(stage, width, height);
        Map<EnemyShip, Position> survivors = positions(patrol);
        for (int frame = 1; frame < clearFrame; frame++)
            patrol.update();

        int rightStart = spawnX(stage, settings(stage).getFormationWidth() - 1);
        for (EnemyShip enemy : List.copyOf(survivors.keySet())) {
            int x = survivors.get(enemy).x();
            if ((clearLeft && x == 20) || (clearRight && x == rightStart)) {
                patrol.destroy(enemy);
                survivors.remove(enemy);
            }
        }
        EnemyShip tracked = survivors.keySet().iterator().next();
        int start = survivors.get(tracked).x();
        int previousX = tracked.getPositionX() - start;
        int wall = movingRight ? width - 20 - survivors.entrySet().stream()
                .mapToInt(entry -> entry.getValue().x() + entry.getKey().getWidth()).max().orElseThrow()
                : 20 - survivors.values().stream().mapToInt(Position::x).min().orElseThrow();
        boolean reachedWall = false;
        for (int frame = 0; frame < 360; frame++) {
            patrol.update();
            int x = tracked.getPositionX() - start;
            int delta = x - previousX;
            check(Math.abs(delta) <= 4, "Clearing columns must not jump in stage " + stage);
            reachedWall |= x == wall;
            if (movingRight ? delta < 0 : delta > 0) {
                check(reachedWall, "Stage " + stage + " reversed before reaching the wall at frame " + clearFrame);
                return;
            }
            previousX = x;
        }
        throw new AssertionError("Stage " + stage + " did not turn at the wall at frame " + clearFrame);
    }

    private static void latePatrolWallTurns(int stage) throws Exception {
        int[] turns = switch (stage) {
            case 6 -> new int[] {120, 240};
            case 7 -> new int[] {300, 600};
            case 8 -> new int[] {180, 450};
            case 9 -> new int[] {240, 480, 560, 640, 1200, 1440};
            default -> throw new IllegalArgumentException("Expected stage 6–9");
        };
        for (int i = 0; i < turns.length; i++) {
            for (int edges = 1; edges <= 3; edges++) {
                boolean movingRight = i % 2 == 0;
                boolean clearLeft = (edges & 1) != 0;
                boolean clearRight = (edges & 2) != 0;
                latePatrolReachesWallBeforeReversing(stage, WIDTH, HEIGHT, turns[i], movingRight, clearLeft, clearRight);
                latePatrolReachesWallBeforeReversing(stage, 432, 497, turns[i], movingRight, clearLeft, clearRight);
            }
        }
        if (stage == 9) {
            // Repeat stage 2's reported case on the first (diamond) route of stage 9.
            latePatrolReachesWallBeforeReversing(stage, WIDTH, HEIGHT, 120, true, true, false);
            latePatrolReachesWallBeforeReversing(stage, 432, 497, 120, true, true, false);
        }
    }

    private static void togglePause(GameScreen game) throws Exception {
        field(Cooldown.class, "time").setLong(get(game, "pauseKeyCooldown"), 0);
        Core.getInputManager().keyPressed(new KeyEvent(INPUT, KeyEvent.KEY_PRESSED,
                0, 0, KeyEvent.VK_P, KeyEvent.CHAR_UNDEFINED));
        try {
            call(game, "update");
        } finally {
            Core.getInputManager().keyReleased(new KeyEvent(INPUT, KeyEvent.KEY_RELEASED,
                    0, 0, KeyEvent.VK_P, KeyEvent.CHAR_UNDEFINED));
        }
    }

    private static void matchesReference(EnemyShipFormation actual, Map<EnemyShip, Position> starts,
            EnemyShipFormation reference, Map<EnemyShip, Position> referenceStarts) throws Exception {
        check(displacement(actual, starts, WIDTH, HEIGHT).equals(
                displacement(reference, referenceStarts, WIDTH, HEIGHT)),
                "Resume continues at the next route position");
        check(progress(actual).equals(progress(reference)), "Resume continues at the next route phase");
    }

    @SuppressWarnings("unchecked")
    private static void screenLifecycle(int stage) throws Exception {
        GameScreen game = new GameScreen(new GameState(stage, 0, 3, 0, 0, 0),
                settings(stage), false, WIDTH, HEIGHT, 60);
        game.initialize();
        EnemyShipFormation actual = (EnemyShipFormation) get(game, "enemyShipFormation");
        EnemyShipFormation reference = formation(stage, WIDTH, HEIGHT);
        Map<EnemyShip, Position> starts = positions(actual);
        Map<EnemyShip, Position> referenceStarts = positions(reference);
        field(Cooldown.class, "time").setLong(field(Screen.class, "inputDelay").get(game), 0);
        // Freeze stage 9 one active update before the second route change.
        int warmup = stage == 9 ? 957 : 179;
        for (int frame = 0; frame < warmup; frame++) {
            actual.update();
            reference.update();
        }
        call(game, "update");
        reference.update();
        matchesReference(actual, starts, reference, referenceStarts);

        Map<EnemyShip, Position> stopped = positions(actual);
        Progress stoppedProgress = progress(actual);
        togglePause(game);
        check((Boolean) get(game, "paused"), "P opens the pause menu");
        for (int frame = 0; frame < 10; frame++) call(game, "update");
        check(stopped.equals(positions(actual)), "Pause preserves every enemy position");
        check(stoppedProgress.equals(progress(actual)), "Pause does not advance route progress");
        togglePause(game);
        check(!(Boolean) get(game, "paused"), "P resumes gameplay");
        reference.update();
        matchesReference(actual, starts, reference, referenceStarts);

        ItemSystem items = (ItemSystem) get(game, "items");
        check(items.api().tryGrant(new GrantRequest("campaign-freeze-" + stage, "freeze",
                GrantSource.REWARD, GrantTiming.NOW)).isSuccess(), "Freeze can be granted");
        check(items.useSlot(0) == UseResult.USED && items.enemiesFrozen(), "Freeze is active");
        stopped = positions(actual);
        stoppedProgress = progress(actual);
        for (int frame = 0; frame < 10; frame++) {
            // Control elapsed item time so this check does not depend on rendering speed.
            field(ItemSystem.class, "lastUpdateNanos").setLong(items, System.nanoTime());
            call(game, "update");
        }
        check(items.enemiesFrozen(), "Freeze remains active during the controlled updates");
        check(stopped.equals(positions(actual)), "Freeze preserves every enemy position");
        check(stoppedProgress.equals(progress(actual)), "Freeze does not advance route progress");
        ItemManager manager = (ItemManager) get(items, "manager");
        manager.update(items.api().getItemInfo("freeze").durationMillis + 1,
                new PlayerSnapshot(new Bounds(200, 480, 24, 16), false, false));
        check(!items.enemiesFrozen(), "Freeze expires through the real item manager");
        call(game, "update");
        reference.update();
        matchesReference(actual, starts, reference, referenceStarts);

        Set<Bullet> bullets = (Set<Bullet>) get(game, "bullets");
        bullets.clear();
        for (EnemyShip enemy : actual)
            for (int hit = 0; hit < enemy.getHealth(); hit++)
                bullets.add(new Bullet(enemy.getPositionX(), enemy.getPositionY(), -4));
        call(game, "update");
        check(actual.isEmpty(), "Player bullets clear the moving formation");
        check((Boolean) get(game, "levelFinished"), "Clearing the formation finishes the level");
        check(game.getGameState().getShipsDestroyed() == starts.size(), "Every enemy kill is counted once");
        call(game, "update");

        GameScreen next = new GameScreen(new GameState(stage + 1,
                game.getGameState().getScore(), 3, 0, 0, 0), settings(stage + 1), false, WIDTH, HEIGHT, 60);
        next.initialize();
        if (stage == 4 || stage == 9) {
            Class<?> bossType = stage == 4 ? MidBoss.class : entity.FinalBoss.class;
            check(bossType.isInstance(get(next, "midBoss")), "Expected boss after stage " + stage);
            check(get(next, "enemyShipFormation") == null, "Boss stage does not spawn an ordinary formation");
        } else {
            EnemyShipFormation nextFormation = (EnemyShipFormation) get(next, "enemyShipFormation");
            check(progress(nextFormation).patrolFrame() == 0, "Next stage starts with fresh route progress");
        }

        call(game, "restartLevel");
        EnemyShipFormation restarted = (EnemyShipFormation) get(game, "enemyShipFormation");
        check(restarted != actual && positions(restarted).size() == starts.size(),
                "Restart rebuilds the original formation");
        check(new HashSet<>(positions(restarted).values()).equals(new HashSet<>(starts.values())),
                "Restart restores the original spawn grid");
        check(progress(restarted).equals(progress(formation(stage, WIDTH, HEIGHT))),
                "Restart resets route progress");
        check(!(Boolean) get(game, "levelFinished") && !(Boolean) get(game, "paused"),
                "Restart returns to active gameplay");
        call(game, "update");
        check(progress(restarted).patrolFrame() == 0, "Restart countdown does not advance a patrol");
    }

    private static int fireColumn(EnemyShipFormation formation, Map<EnemyShip, Integer> columns) throws Exception {
        field(Cooldown.class, "time").setLong(get(formation, "shootingCooldown"), 0);
        Set<Bullet> bullets = new HashSet<>();
        formation.shoot(bullets);
        check(bullets.size() == 1, "Each campaign firing turn emits exactly one bullet");
        Bullet shot = bullets.iterator().next();
        for (EnemyShip enemy : formation) {
            if (!enemy.isDestroyed() && shot.getPositionY() == enemy.getPositionY()
                    && shot.getPositionX() + shot.getWidth() / 2 == enemy.getPositionX() + enemy.getWidth() / 2) {
                @SuppressWarnings("unchecked")
                List<EnemyShip> column = ((List<List<EnemyShip>>) get(formation, "enemyShips")).stream()
                        .filter(ships -> ships.contains(enemy)).findFirst().orElseThrow();
                check(formation.getNextShooter(column) == enemy, "Only an exposed enemy fires");
                formation.shoot(bullets);
                check(bullets.size() == 1, "Cooldown blocks a second shot in the same turn");
                return columns.get(enemy);
            }
        }
        throw new AssertionError("Shot must originate from a surviving enemy");
    }

    private static void firingOrders(int stage) throws Exception {
        EnemyShipFormation formation = formation(stage, WIDTH, HEIGHT);
        Map<EnemyShip, Integer> columns = new HashMap<>();
        for (EnemyShip enemy : formation) {
            for (int column = 0; column < settings(stage).getFormationWidth(); column++)
                if (enemy.getPositionX() == spawnX(stage, column)) columns.put(enemy, column);
            for (EnemyShip other : formation)
                if (other != enemy)
                    check(!new java.awt.Rectangle(enemy.getPositionX(), enemy.getPositionY(), enemy.getWidth(), enemy.getHeight())
                            .intersects(new java.awt.Rectangle(other.getPositionX(), other.getPositionY(), other.getWidth(), other.getHeight())),
                            "Campaign layout has no overlapping enemies");
        }
        formation.update();
        int[] order = switch (stage) {
            case 3 -> new int[] {0, 1, 0, 1};
            case 4 -> new int[] {0, 1, 0, 1};
            case 6 -> new int[] {0, 1, 2, 3, 4, 5};
            case 7 -> new int[] {0, 5, 1, 4, 2, 3};
            case 8 -> new int[] {3, 2, 4, 1, 5, 0, 6};
            case 9 -> new int[] {0, 1, 2, 3, 4, 5};
            default -> new int[0];
        };
        for (int expected : order) {
            int column = fireColumn(formation, columns);
            int actual = switch (stage) {
                case 3 -> column < 2 ? 0 : 1;
                case 4 -> column == 2 || column == 3 ? 0 : 1;
                default -> column;
            };
            check(actual == expected, "Stage " + stage + " follows its firing sequence");
        }
        if (stage == 9) {
            field(EnemyShipFormation.class, "patrolFrame").setInt(formation, 480);
            check(fireColumn(formation, columns) < 3, "Stage 9 switches to left wing at the second route");
            check(fireColumn(formation, columns) >= 3, "Stage 9 alternates to the right wing");
            field(EnemyShipFormation.class, "patrolFrame").setInt(formation, 960);
            int center = fireColumn(formation, columns);
            check(center >= 2 && center < 4, "Stage 9 switches to the center at the third route");
            int wing = fireColumn(formation, columns);
            check(wing < 2 || wing >= 4, "Stage 9 follows with a wing shot");
        }
        int lastColumn = settings(stage).getFormationWidth() - 1;
        for (EnemyShip enemy : formation)
            if (columns.get(enemy) != lastColumn) formation.destroy(enemy);
        formation.removeDestroyed();
        formation.update();
        for (int turn = 0; turn < 10; turn++)
            check(fireColumn(formation, columns) == lastColumn, "Missing groups are skipped without relabeling survivors");
        for (EnemyShip enemy : formation) formation.destroy(enemy);
        formation.removeDestroyed();
        formation.update();
        Set<Bullet> bullets = new HashSet<>();
        formation.shoot(bullets);
        check(formation.isEmpty() && bullets.isEmpty(), "An empty formation cannot fire");
    }

    private static void screenshots(java.nio.file.Path directory) throws Exception {
        java.nio.file.Files.createDirectories(directory);
        BufferedImage sheet = new BufferedImage(WIDTH * 4, (HEIGHT + 28) * 2, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = sheet.createGraphics();
        int index = 0;
        for (int stage : List.of(1, 2, 3, 4, 6, 7, 8, 9)) {
            GameScreen game = new GameScreen(new GameState(stage, 0, 3, 0, 0, 0),
                    settings(stage), false, WIDTH, HEIGHT, 60);
            game.initialize();
            field(Cooldown.class, "time").setLong(field(Screen.class, "inputDelay").get(game), 0);
            call(game, "draw");
            BufferedImage image = (BufferedImage) field(engine.DrawManager.class, "backBuffer").get(null);
            int x = index % 4 * WIDTH;
            int y = index / 4 * (HEIGHT + 28);
            graphics.drawImage(image, x, y, WIDTH, HEIGHT, null);
            graphics.setColor(java.awt.Color.WHITE);
            graphics.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 14));
            graphics.drawString("STAGE " + stage + " / " + settings(stage).getFormationLayout(), x + 10, y + HEIGHT + 20);
            index++;
        }
        graphics.dispose();
        javax.imageio.ImageIO.write(sheet, "png", directory.resolve("campaign-formations.png").toFile());
    }

    public static void main(String[] args) throws Exception {
        Core.getLogger().setUseParentHandlers(false);
        SoundManager.getInstance().setMuted(true);
        Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        Core.getDrawManager().setFrame((engine.Frame) unsafe.allocateInstance(Offscreen.class));
        Map<Integer, MovementPattern> expected = Map.of(
                1, MovementPattern.DIAGONAL_PATROL, 2, MovementPattern.DIAMOND_PATROL,
                3, MovementPattern.ELLIPSE_PATROL, 4, MovementPattern.RECTANGLE_PATROL,
                6, MovementPattern.SERPENTINE_PATROL, 7, MovementPattern.FIGURE_EIGHT_PATROL,
                8, MovementPattern.SIX_POINT_PATROL, 9, MovementPattern.MIXED_PATROL);
        for (int stage : List.of(1, 2, 3, 4, 6, 7, 8, 9)) {
            check(settings(stage).getMovementPattern() == expected.get(stage), "Campaign pattern placement");
            routeAndRemoval(stage, WIDTH, HEIGHT);
            routeAndRemoval(stage, 432, 497);
            if (stage >= 6) latePatrolWallTurns(stage);
            if (stage == 2) {
                for (boolean movingRight : new boolean[] {true, false}) {
                    for (boolean approachingVertex : new boolean[] {true, false}) {
                        // Clear the left edge, right edge, or both at the same time.
                        for (int edges = 1; edges <= 3; edges++) {
                            boolean clearLeft = (edges & 1) != 0;
                            boolean clearRight = (edges & 2) != 0;
                            diamondKeepsDirectionAfterColumnRemoval(WIDTH, HEIGHT, movingRight,
                                    approachingVertex, clearLeft, clearRight);
                            diamondKeepsDirectionAfterColumnRemoval(432, 497, movingRight,
                                    approachingVertex, clearLeft, clearRight);
                        }
                    }
                }
            }
            System.out.println("PASS: stage " + stage + " full routes, bounds, spacing, edge cleanup and shooting");
            screenLifecycle(stage);
            System.out.println("PASS: stage " + stage + " pause/freeze/resume, collisions, clear, next stage and restart");
            firingOrders(stage);
            System.out.println("PASS: stage " + stage + " layout overlap, firing order, cooldown and cleared groups");
        }
        if (args.length > 0) screenshots(java.nio.file.Path.of(args[0]));
        System.out.println("PASS: " + checks + " campaign movement checks");
    }
}
