package engine.soundEngine;

/**
 * Sounds played by the menus. Screens call this instead of talking to
 * the SoundManager, so the sound can be changed in one place.
 */
public final class MenuSounds {

    /** Minimum time between two move sounds, in milliseconds. */
    private static final long MIN_INTERVAL_MS = 60;
    /** When the move sound last played. */
    private static long lastPlayed;
    /** At most one move sound may be pending; old hover sounds must never queue up. */
    private static final java.util.concurrent.atomic.AtomicBoolean pending =
            new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.ExecutorService playback =
            java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task,"menu-move-audio");
                thread.setDaemon(true);
                return thread;
            });

    private MenuSounds() {
    }

    /** Plays the short sound heard when the menu cursor moves. */
    public static synchronized void playMove() {
        final long now = System.currentTimeMillis();
        if (now - lastPlayed < MIN_INTERVAL_MS || !pending.compareAndSet(false,true))
            return;
        lastPlayed = now;
        playback.execute(() -> {
            try {
                SoundManager.getInstance().playSfx(Sound.MENU_MOVE);
            } catch (RuntimeException error) {
                engine.Core.getLogger().warning("Menu move sound unavailable: " + error.getMessage());
            } finally {
                pending.set(false);
            }
        });
    }
}
