package screen;

import engine.soundEngine.Sound;
import engine.soundEngine.SoundManager;
import java.awt.event.KeyEvent;
import java.util.function.LongSupplier;

/** One-time opening screen; always returns to the existing main menu. */
public final class StartupScreen extends Screen {
    private final LongSupplier clock;
    private long startedAt;

    public StartupScreen(int width, int height, int fps) {
        this(width, height, fps, System::nanoTime);
    }
    StartupScreen(int width, int height, int fps, LongSupplier clock) {
        super(width, height, fps);
        this.clock = clock;
    }
    @Override public void initialize() {
        super.initialize();
        SoundManager.getInstance().playBgm(Sound.BGM_TITLE);
        startedAt = clock.getAsLong();
        returnCode = 1;
    }
    @Override public int run() { super.run(); return returnCode; }
    @Override protected void update() {
        long elapsed = Math.max(0, (clock.getAsLong() - startedAt) / 1_000_000L);
        if (elapsed >= StartupAnimation.DURATION_MS || inputManager.isKeyDown(KeyEvent.VK_SPACE)
                || inputManager.isKeyDown(KeyEvent.VK_ENTER) || inputManager.isKeyDown(KeyEvent.VK_ESCAPE)) {
            isRunning = false;
            return;
        }
        drawManager.initDrawing(this);
        drawManager.drawStartup(this, elapsed);
        drawManager.completeDrawing(this);
    }
}
