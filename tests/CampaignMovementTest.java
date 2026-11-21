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
        int right = 20 + 40 * (settings(stage).getFormationWidth() - 1);
        List<EnemyShip> removed = starts.entrySet().stream()
                .filter(entry -> frame == 225 ? entry.getValue().x() == 20
                        : frame == 405 ? entry.getValue().y() == 100
                        : entry.getValue().x() == right)
                .map(Map.Entry::getKey).toList();
        check(!removed.isEmpty(), "The selected edge contains enemies");
        for (EnemyShip enemy : removed) {
            formation.destroy(enemy);
            starts.remove(enemy);
        }
        formation.removeDestroyed();
    }

    private static void checkShape(int stage, int frame, Position offset, int width) {
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
            case 2 -> width - 224;
            case 4 -> width - 264;
            default -> 160;
        };
        if (stage == 2) {
            double halfWidth = travelX / 2.0;
            double diamond = Math.abs((offset.x() - halfWidth) / halfWidth) + Math.abs(offset.y() / 60.0);
            check(Math.abs(diamond - 1) < 0.02, "Stage 2 follows the diamond edges");
        } else if (stage == 3) {
            double ellipse = Math.pow((offset.x() - 80) / 80.0, 2)
                    + Math.pow(offset.y() / 60.0, 2);
            check(Math.abs(ellipse - 1) < 0.03, "Stage 3 follows the ellipse");
        } else {
            check(offset.x() == 0 || offset.x() == travelX || offset.y() == 0 || offset.y() == 120,
                    "Stage 4 follows the rectangle edges");
        }
        if (frame <= 720 && frame % 180 == 0) {
            Position[] quarters = switch (stage) {
                case 2 -> new Position[] {new Position(travelX / 2, -60), new Position(travelX, 0),
                        new Position(travelX / 2, 60), new Position(0, 0)};
                case 3 -> new Position[] {new Position(80, 60), new Position(160, 0),
                        new Position(80, -60), new Position(0, 0)};
                default -> new Position[] {new Position(travelX, 0), new Position(travelX, 120),
                        new Position(0, 120), new Position(0, 0)};
            };
            check(offset.equals(quarters[frame / 180 - 1]), "Stage " + stage + " quarter-loop waypoint");
        }
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
                check(original.containsValue(new Position(20 + column * 40, 100 + row * 40)),
                        "Stage " + stage + " keeps its original spawn grid");

        boolean wallPatrol = stage == 2 || stage == 4;
        boolean reachedNewLeft = false;
        boolean reachedNewRight = false;
        Position previous = new Position(0, 0);
        Position previousReduced = previous;
        for (int frame = 1; frame <= 2160; frame++) {
            reference.update();
            reduced.update();
            Position offset = displacement(reference, original, width, height);
            Position reducedOffset = displacement(reduced, surviving, width, height);
            if (!wallPatrol)
                check(offset.equals(reducedOffset), "Fixed patrol stays on course after edge removal");
            check(Math.abs(reducedOffset.x() - previousReduced.x()) <= 4
                            && Math.abs(reducedOffset.y() - previousReduced.y()) <= 4,
                    "No jump after edge removal at stage " + stage + ", frame " + frame);
            reachedNewLeft |= reducedOffset.x() == -40;
            reachedNewRight |= reducedOffset.x() == width - (stage == 2 ? 224 : 264) + 40;
            check(Math.abs(offset.x() - previous.x()) <= 4 && Math.abs(offset.y() - previous.y()) <= 4,
                    "No movement jump at stage " + stage + ", frame " + frame);
            checkShape(stage, frame, offset, width);
            // For wall patrols, remove the right column just before the old turning point.
            int rightRemovalFrame = stage == 2 ? 359 : 179;
            if (frame == 225 || frame == 405 || frame == (wallPatrol ? rightRemovalFrame : 585)) {
                removeEdge(reduced, surviving, stage, frame == rightRemovalFrame ? 585 : frame);
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
        for (int frame = 0; frame < 179; frame++) {
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
            bullets.add(new Bullet(enemy.getPositionX(), enemy.getPositionY(), -4));
        call(game, "update");
        check(actual.isEmpty(), "Player bullets clear the moving formation");
        check((Boolean) get(game, "levelFinished"), "Clearing the formation finishes the level");
        check(game.getGameState().getShipsDestroyed() == starts.size(), "Every enemy kill is counted once");
        call(game, "update");

        GameScreen next = new GameScreen(new GameState(stage + 1,
                game.getGameState().getScore(), 3, 0, 0, 0), settings(stage + 1), false, WIDTH, HEIGHT, 60);
        next.initialize();
        if (stage == 4) {
            check(get(next, "midBoss") instanceof MidBoss, "Stage 4 still leads to the stage 5 boss");
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

    public static void main(String[] args) throws Exception {
        Core.getLogger().setUseParentHandlers(false);
        SoundManager.getInstance().setMuted(true);
        Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        Core.getDrawManager().setFrame((engine.Frame) unsafe.allocateInstance(Offscreen.class));
        MovementPattern[] expected = {MovementPattern.DIAGONAL_PATROL, MovementPattern.DIAMOND_PATROL,
                MovementPattern.ELLIPSE_PATROL, MovementPattern.RECTANGLE_PATROL};
        for (int stage = 1; stage <= 4; stage++) {
            check(settings(stage).getMovementPattern() == expected[stage - 1], "Campaign pattern placement");
            routeAndRemoval(stage, WIDTH, HEIGHT);
            routeAndRemoval(stage, 432, 497);
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
        }
        System.out.println("PASS: " + checks + " campaign movement checks");
    }
}
