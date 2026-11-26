package screen;

import engine.*;
import engine.soundEngine.SoundManager;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import sun.misc.Unsafe;

/** Checks the real Quit/confirmation flow while the startup input delay is active. */
public final class CountdownQuitChecks {
    private static final int WIDTH = 434, HEIGHT = 497;
    private static int checks;
    private static DisplaySizeChecks.FakeFrame frame;

    private static Field field(Class<?> owner, String name) throws Exception {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
        checks++;
    }

    private static GameScreen game(int level, int remaining) throws Exception {
        GameSettings settings = (GameSettings) field(Core.class, "SETTINGS_LEVEL_" + level).get(null);
        GameScreen result = new GameScreen(new GameState(level, 100, 3, 2, 1, 0),
                settings, false, WIDTH, HEIGHT, 60);
        result.initialize();
        result.isRunning = true;
        long age = field(GameScreen.class, "INPUT_DELAY").getInt(null) - remaining * 1000L - 500;
        long start = System.currentTimeMillis() - age;
        field(Cooldown.class, "time").setLong(result.inputDelay, start);
        field(GameScreen.class, "gameStartTime").setLong(result, start);
        check(!result.inputDelay.checkFinished(), "test must retain the real countdown guard");
        return result;
    }

    private static boolean paused(GameScreen game) throws Exception {
        return field(GameScreen.class, "paused").getBoolean(game);
    }

    private static PauseConfirm confirmation(GameScreen game) throws Exception {
        return (PauseConfirm) field(GameScreen.class, "pauseConfirm").get(game);
    }

    private static void pointer(int id, int x, int y) throws Exception {
        Rectangle view = frame.panel.transform().bounds();
        int px = view.x + (int) ((x + .5) * view.width / WIDTH);
        int py = view.y + (int) ((y + .5) * view.height / HEIGHT);
        SwingUtilities.invokeAndWait(() -> frame.panel.dispatchEvent(new MouseEvent(frame.panel,
                id, 0, 0, px, py, 1, false, MouseEvent.BUTTON1)));
    }

    private static void click(Screen screen) throws Exception {
        Rectangle button = screen.navigationBounds();
        pointer(MouseEvent.MOUSE_PRESSED, button.x + button.width / 2, button.y + button.height / 2);
        screen.update();
        pointer(MouseEvent.MOUSE_RELEASED, button.x + button.width / 2, button.y + button.height / 2);
        screen.update();
    }

    private static void key(int code, boolean down) {
        KeyEvent event = new KeyEvent(frame.panel, down ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                0, 0, code, KeyEvent.CHAR_UNDEFINED);
        if (down) Core.getInputManager().keyPressed(event);
        else Core.getInputManager().keyReleased(event);
    }

    private static void quickTap(GameScreen game, int code) {
        key(code, true); key(code, false); game.update();
    }

    private static void runChecks(Path output) throws Exception {
        Files.createDirectories(output);
        System.setProperty("game.displaySettingsDir", Files.createTempDirectory(output, "prefs-").toString());
        Core.getLogger().setLevel(java.util.logging.Level.OFF);
        SoundManager.getInstance().setMuted(true);
        Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        frame = (DisplaySizeChecks.FakeFrame) unsafe.allocateInstance(DisplaySizeChecks.FakeFrame.class);
        frame.panel = new GameViewport(WIDTH, HEIGHT);
        frame.panel.addMouseListener(Core.getInputManager());
        frame.panel.addMouseMotionListener(Core.getInputManager());
        Core.getDrawManager().setFrame(frame);
        for (WindowSettings.Mode mode : WindowSettings.Mode.values()) {
            frame.applyWindowMode(mode);
            for (int level : new int[] {1, 10}) for (int remaining : new int[] {3, 2, 1}) {
                Core.getInputManager().resetInput();
                GameScreen game = game(level, remaining);
                int lives = field(GameScreen.class, "lives").getInt(game);
                pointer(MouseEvent.MOUSE_PRESSED, 1, HEIGHT - 1); game.update();
                pointer(MouseEvent.MOUSE_RELEASED, 1, HEIGHT - 1); game.update();
                check(!paused(game), "outside click must not open Quit");
                long countdownStart = field(Cooldown.class, "time").getLong(game.inputDelay);
                if (mode == WindowSettings.Mode.MEDIUM && level == 1 && remaining == 3)
                    ImageIO.write(frame.last, "png", output.resolve("countdown-before-quit.png").toFile());
                key(KeyEvent.VK_D, true); key(KeyEvent.VK_SPACE, true);
                click(game);
                PauseConfirm confirm = confirmation(game);
                check(paused(game) && confirm.isOpen(), "countdown Quit must open confirmation: "
                        + mode + ", level " + level + ", " + remaining);
                check(confirm.getPending() == PauseConfirm.Action.NORMAL_QUIT && !confirm.isYesSelected(),
                        "countdown Quit uses the existing action and defaults to No");
                check(field(Cooldown.class, "time").getLong(game.inputDelay) == countdownStart
                        && !game.inputDelay.checkFinished(), "Quit must not expire or reset the gameplay guard");
                game.update();
                check(confirm.isOpen() && !confirm.isYesSelected(), "held fire/movement cannot confirm Quit");
                check(field(GameScreen.class, "lives").getInt(game) == lives, "opening Quit cannot end the run");
                if (mode == WindowSettings.Mode.MEDIUM && level == 1 && remaining == 3)
                    ImageIO.write(frame.last, "png", output.resolve("countdown-quit-confirmation.png").toFile());
                quickTap(game, KeyEvent.VK_ESCAPE);
                check(!confirm.isOpen() && paused(game), "Esc cancels countdown confirmation while firing");
                key(KeyEvent.VK_D, false); key(KeyEvent.VK_SPACE, false); game.update();
                quickTap(game, KeyEvent.VK_P);
                check(!paused(game) && !game.inputDelay.checkFinished(), "P resumes without bypassing countdown");
                click(game);
                key(KeyEvent.VK_SPACE, true); game.update(); key(KeyEvent.VK_SPACE, false); game.update();
                check(!confirm.isOpen() && paused(game), "answering No preserves the paused run");
                quickTap(game, KeyEvent.VK_ESCAPE);
                click(game);
                quickTap(game, KeyEvent.VK_P);
                check(!confirm.isOpen() && paused(game), "P also cancels the confirmation");
                click(game);
                key(KeyEvent.VK_LEFT, true); game.update(); key(KeyEvent.VK_LEFT, false);
                key(KeyEvent.VK_SPACE, true); game.update(); key(KeyEvent.VK_SPACE, false);
                check(field(GameScreen.class, "lives").getInt(game) == 0 && !paused(game),
                        "Yes follows the existing results flow during countdown");
                check(field(GameScreen.class, "score").getInt(game) == 100, "Quit preserves score settlement");
            }
            Core.getInputManager().resetInput();
            Rectangle button = new Rectangle(WIDTH - 78, HEIGHT - 112, 66, 28);
            pointer(MouseEvent.MOUSE_PRESSED, button.x + 10, button.y + 10);
            GameScreen entered = game(1, 3);
            entered.update();
            check(!confirmation(entered).isOpen(), "a mouse press inherited from the previous page cannot quit");
            pointer(MouseEvent.MOUSE_RELEASED, button.x + 10, button.y + 10); entered.update();
            click(entered);
            check(confirmation(entered).isOpen(), "a fresh click after release works during countdown");
            Core.getInputManager().resetInput();
            ShopScreen shop = new ShopScreen(WIDTH, HEIGHT, 60);
            shop.isRunning = true;
            click(shop);
            check(shop.isRunning, "ordinary menu activation guards remain intact");
            field(Cooldown.class, "time").setLong(shop.inputDelay, 0);
            click(shop);
            check(!shop.isRunning, "ordinary Back works after its activation guard");
            Core.getInputManager().resetInput();
            GameScreen finished = game(1, 3);
            field(GameScreen.class, "levelFinished").setBoolean(finished, true);
            check(!finished.navigationVisible(), "completed levels keep Quit hidden");
            field(GameScreen.class, "levelFinished").setBoolean(finished, false);
            field(GameScreen.class, "lives").setInt(finished, 0);
            check(!finished.navigationVisible(), "ended runs keep Quit hidden");
        }
        System.out.println("PASS: " + checks + " countdown Quit checks across 3/2/1, 4 sizes and stages 1/10");
    }

    public static void main(String[] args) {
        try { runChecks(Path.of(args[0])); }
        catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
        System.exit(0);
    }
}
