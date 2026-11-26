package screen;

import engine.*;
import engine.soundEngine.SoundManager;
import endless.GameplayExtension;
import endless.GameplaySession;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.SwingUtilities;
import sun.misc.Unsafe;

/** Countdown availability and phase/settlement preservation through the real Endless UI. */
public final class EndlessQuitPhaseChecks {
    private static int checks;
    private static DisplaySizeChecks.FakeFrame frame;
    private static Object controls;

    public static final class FinishProbe implements GameplayExtension {
        static int finishes;
        @Override public void onEvent(GameplaySession session, Event event, Object value) {
            if (event == Event.FINISH) finishes++;
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field value = type.getDeclaredField(name); value.setAccessible(true); return value;
    }
    private static void invoke(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(target);
    }
    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
        checks++;
    }
    private static void click(EndlessMode screen) throws Exception {
        Rectangle button = screen.navigationBounds(), view = frame.panel.transform().bounds();
        int x = view.x + (int) ((button.getCenterX() + .5) * view.width / 434);
        int y = view.y + (int) ((button.getCenterY() + .5) * view.height / 497);
        for (int event : new int[] {MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED}) {
            SwingUtilities.invokeAndWait(() -> frame.panel.dispatchEvent(new MouseEvent(frame.panel,
                    event, 0, 0, x, y, 1, false, MouseEvent.BUTTON1)));
            screen.update();
        }
    }
    private static void key(int code, boolean down) {
        KeyEvent event = new KeyEvent(frame.panel, down ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                0, 0, code, KeyEvent.CHAR_UNDEFINED);
        if (down) { Core.getInputManager().keyPressed(event); ((KeyListener) controls).keyPressed(event); }
        else { Core.getInputManager().keyReleased(event); ((KeyListener) controls).keyReleased(event); }
    }
    private static void tap(EndlessMode screen, int code) {
        key(code, true); key(code, false); screen.update();
    }
    private static void scenario(EndlessMode.Run.Phase phase, int remaining, boolean rerolled) throws Exception {
        Core.getInputManager().resetInput(); ((ResettableInput) controls).resetInput();
        var constructor = EndlessMode.class.getDeclaredConstructor(int.class, int.class, boolean.class);
        constructor.setAccessible(true);
        EndlessMode screen = constructor.newInstance(434, 497, true);
        screen.initialize(); screen.isRunning = true;
        EndlessMode.Run run = (EndlessMode.Run) field(EndlessMode.class, "run").get(screen);
        GameplaySession systems = (GameplaySession) field(EndlessMode.class, "systems").get(screen);
        if (phase == EndlessMode.Run.Phase.BOSS_INTRO) {
            field(EndlessMode.Run.class, "wave").setInt(run, 5);
            ((List<?>) field(EndlessMode.Run.class, "enemies").get(run)).clear();
            invoke(run, "spawnWave");
            check(run.getBoss() != null, "boss-intro test uses an actual native boss");
        } else if (phase == EndlessMode.Run.Phase.REWARD) {
            field(EndlessMode.Run.class, "phase").set(run, phase);
            invoke(run, "rollRewards");
            if (rerolled) check(run.refreshRewards(), "reward test starts with the reroll already used");
        }
        field(EndlessMode.Run.class, "countdown").setDouble(run, remaining - .25);
        field(GameplaySession.class, "pendingDiamonds").setInt(systems, 7);
        List<?> offers = (List<?>) field(EndlessMode.Run.class, "offers").get(run);
        double countdown = field(EndlessMode.Run.class, "countdown").getDouble(run);
        int wave = run.getWave(), finishes = FinishProbe.finishes;
        screen.inputDelay.reset();
        check(!screen.inputDelay.checkFinished(), "Endless entry activation guard must still be active");
        key(KeyEvent.VK_D, true); key(KeyEvent.VK_SPACE, true);
        click(screen);
        PauseConfirm confirm = (PauseConfirm) field(EndlessMode.class, "pauseConfirm").get(screen);
        check(run.getPhase() == EndlessMode.Run.Phase.PAUSED && confirm.isOpen(),
                "Quit opens during " + phase + " / " + remaining);
        check(confirm.getPending() == PauseConfirm.Action.ENDLESS_QUIT && !confirm.isYesSelected(),
                "Endless Quit retains its existing cash-out confirmation and default No");
        screen.update();
        check(confirm.isOpen() && !confirm.isYesSelected(), "held combat inputs cannot answer the dialog");
        tap(screen, KeyEvent.VK_ESCAPE);
        check(!confirm.isOpen() && run.getPhase() == EndlessMode.Run.Phase.PAUSED,
                "Esc cancels the dialog without resuming while fire is held");
        key(KeyEvent.VK_D, false); key(KeyEvent.VK_SPACE, false); screen.update();
        tap(screen, KeyEvent.VK_P);
        check(run.getPhase() == phase && run.getWave() == wave, "resume restores the exact starting phase/wave");
        check(field(EndlessMode.Run.class, "countdown").getDouble(run) == countdown,
                "Quit/cancel/resume preserves the ready or boss-intro countdown");
        check(field(EndlessMode.Run.class, "offers").get(run) == offers
                && field(EndlessMode.Run.class, "refreshed").getBoolean(run) == rerolled,
                "reward offers and reroll availability survive unchanged");
        check(FinishProbe.finishes == finishes, "cancelling does not settle the run");
        click(screen); tap(screen, KeyEvent.VK_SPACE);
        check(!confirm.isOpen() && run.getPhase() == EndlessMode.Run.Phase.PAUSED,
                "answering No does not choose a reward or advance a phase");
        tap(screen, KeyEvent.VK_ESCAPE);
        check(run.getPhase() == phase, "Esc resumes the same phase after No");
        click(screen); tap(screen, KeyEvent.VK_LEFT); tap(screen, KeyEvent.VK_SPACE);
        check(run.getPhase() == EndlessMode.Run.Phase.OVER && "CASHED OUT".equals(run.getEndReason()),
                "Yes follows existing Endless settlement");
        check(FinishProbe.finishes == finishes + 1, "confirmed Quit emits exactly one settlement event");
        check(field(GameplaySession.class, "bankedDiamonds").getInt(systems) == 7
                && field(GameplaySession.class, "pendingDiamonds").getInt(systems) == 0,
                "the pending pot is settled exactly once");
        run.finish("CASHED OUT"); tap(screen, KeyEvent.VK_SPACE);
        check(FinishProbe.finishes == finishes + 1, "repeated confirmation/finish cannot settle again");
        screen.inputDelay.reset();
        check(!screen.navigationReady(), "the existing post-run Back activation guard remains intact");
    }
    private static void runChecks(Path output) throws Exception {
        Files.createDirectories(output);
        System.setProperty("game.displaySettingsDir", Files.createTempDirectory(output, "prefs-").toString());
        Core.getLogger().setLevel(java.util.logging.Level.OFF); SoundManager.getInstance().setMuted(true);
        Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        frame = (DisplaySizeChecks.FakeFrame) unsafe.allocateInstance(DisplaySizeChecks.FakeFrame.class);
        frame.panel = new GameViewport(434, 497); Core.getDrawManager().setFrame(frame);
        field(EndlessMode.class, "boundFrame").set(null, frame);
        controls = field(EndlessMode.class, "controls").get(null);
        frame.panel.addMouseListener(Core.getInputManager()); frame.panel.addMouseMotionListener(Core.getInputManager());
        frame.panel.addMouseListener((MouseListener) controls); frame.panel.addMouseMotionListener((MouseMotionListener) controls);
        for (WindowSettings.Mode mode : WindowSettings.Mode.values()) {
            frame.applyWindowMode(mode);
            for (int remaining : new int[] {3, 2, 1}) {
                scenario(EndlessMode.Run.Phase.READY, remaining, false);
                scenario(EndlessMode.Run.Phase.BOSS_INTRO, remaining, false);
            }
            scenario(EndlessMode.Run.Phase.REWARD, 3, false);
            scenario(EndlessMode.Run.Phase.REWARD, 3, true);
        }
        System.out.println("PASS: " + checks + " Endless READY/BOSS_INTRO/REWARD Quit and settlement checks");
    }
    public static void main(String[] args) {
        try { runChecks(Path.of(args[0])); }
        catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
        System.exit(0);
    }
}
