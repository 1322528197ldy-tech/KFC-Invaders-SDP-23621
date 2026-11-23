package engine;

/** Session-only access to the game's test profile. */
public final class TesterMode {
    private static volatile boolean enabled;

    private TesterMode() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean enable(String code) {
        if (!"000".equals(code))
            return false;
        enabled = true;
        return true;
    }

    public static void disable() {
        enabled = false;
    }
}
