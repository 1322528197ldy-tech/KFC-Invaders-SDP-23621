package engine;

import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import javax.imageio.ImageIO;
import screen.CashOutScreen;
import screen.EndlessMode;
import screen.GameScreen;
import screen.Screen;
import screen.SettingsScreen;

/** Desktop geometry checks and an optional native-window render and input check. */
public final class DisplayChecks {
    private static final int WIDTH = 432;
    private static final int HEIGHT = 497;
    private static int checks;

    private static void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
        checks++;
    }

    private static void geometry() {
        check(Frame.windowSize(WIDTH, HEIGHT, 1900, 1030, 1, 1)
                .equals(new Dimension(864, 994)), "HD window at 100% DPI");
        check(Frame.windowSize(WIDTH, HEIGHT, 950, 510, 2, 2)
                .equals(new Dimension(432, 497)), "Windows DPI is applied once at 200%");
        Dimension small = Frame.windowSize(WIDTH, HEIGHT, 650, 350, 1.5, 1.5);
        check(small.width <= 650 && small.height <= 350, "Window fits a smaller display");
        check(Math.abs(small.width * HEIGHT - small.height * WIDTH) < HEIGHT,
                "Default window keeps the original aspect ratio");
        Rectangle wide = Frame.viewport(1184, 994, WIDTH, HEIGHT);
        check(wide.equals(new Rectangle(160, 0, 864, 994)), "Wide window has side borders");
        Rectangle tall = Frame.viewport(864, 1154, WIDTH, HEIGHT);
        check(tall.equals(new Rectangle(0, 80, 864, 994)), "Tall window has top and bottom borders");
        check(Frame.mapPoint(new Point(160, 0), wide, WIDTH, HEIGHT).equals(new Point(0, 0)),
                "Viewport origin maps to the logical origin");
        check(Frame.mapPoint(new Point(1023, 993), wide, WIDTH, HEIGHT).equals(new Point(431, 496)),
                "Bottom right maps to the last logical pixel");
        check(Frame.mapPoint(new Point(159, 80), wide, WIDTH, HEIGHT).x < 0,
                "A point in the border stays outside the playfield");
        check(Frame.rasterSize(new Rectangle(0, 0, 432, 497), WIDTH, HEIGHT, 2, 2)
                .equals(new Dimension(864, 994)), "HD raster at 200% DPI");
        check(Frame.rasterSize(new Rectangle(0, 0, 1728, 1988), WIDTH, HEIGHT, 2, 2)
                .equals(new Dimension(864, 994)), "Maximizing cannot allocate a giant raster");
        check(Frame.rasterSize(new Rectangle(0, 0, 576, 662), WIDTH, HEIGHT, 1.5, 1.5)
                .equals(new Dimension(864, 993)), "Fractional DPI gets a matching raster");
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters)
            throws ReflectiveOperationException {
        Method method = type.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static void click(Canvas surface, Point point, boolean pressed) {
        MouseEvent event = new MouseEvent(surface,
                pressed ? MouseEvent.MOUSE_PRESSED : MouseEvent.MOUSE_RELEASED, 0, 0,
                point.x, point.y, 1, false, MouseEvent.BUTTON1);
        if (pressed)
            Core.getInputManager().mousePressed(event);
        else
            Core.getInputManager().mouseReleased(event);
    }

    private static Point windowPoint(Canvas surface, Rectangle control) {
        Rectangle display = Frame.viewport(surface.getWidth(), surface.getHeight(), WIDTH, HEIGHT);
        return new Point(display.x + (int) Math.round(control.getCenterX() * display.width / WIDTH),
                display.y + (int) Math.round(control.getCenterY() * display.height / HEIGHT));
    }

    private static void resize(Frame frame, Dimension size) throws Exception {
        EventQueue.invokeAndWait(() -> {
            frame.setSize(size);
            frame.validate();
        });
        EventQueue.invokeAndWait(frame::validate);
    }

    private static void measure(Screen game, Method draw, Frame frame, String label) throws Exception {
        for (int i = 0; i < 40; i++)
            draw.invoke(game);
        long[] samples = new long[140];
        for (int index = 0; index < samples.length; index++) {
            long start = System.nanoTime();
            draw.invoke(game);
            samples[index] = System.nanoTime() - start;
        }
        Arrays.sort(samples);
        double average = Arrays.stream(samples).average().orElseThrow() / 1_000_000;
        double p95 = samples[(int) Math.ceil(samples.length * .95) - 1] / 1_000_000.0;
        Dimension raster = frame.renderSize(WIDTH, HEIGHT);
        System.out.printf(Locale.ROOT, "%s: %dx%d, mean %.2f ms, p95 %.2f ms%n",
                label, raster.width, raster.height, average, p95);
    }

    private static void cashOutHints(Path screenshots, String size) throws Exception {
        var previousLanguage = I18n.language();
        try {
            for (var language : I18n.Language.values()) {
                I18n.setLanguage(language);
                for (int nextLevel : new int[] {2, Core.getNumLevels()}) {
                    var screen = new CashOutScreen(WIDTH, HEIGHT, 60,
                            new GameState(nextLevel, 0, 3, 0, 0, 1, 0));
                    method(CashOutScreen.class, "draw").invoke(screen);
                    var image = (BufferedImage) field(DrawManager.class, "backBuffer").get(null);
                    String name = "cash-out-" + size + "-" + language.name().toLowerCase(Locale.ROOT)
                            + "-level-" + nextLevel;
                    ImageIO.write(image, "png", screenshots.resolve(name + ".png").toFile());
                    check(hintRows(image) == 2, name + " should have two separate hint lines");
                }
            }
        } finally {
            I18n.setLanguage(previousLanguage);
        }
    }

    private static int hintRows(BufferedImage image) {
        // Count ink bands in the footer, ignoring gaps within a single glyph line.
        int firstRow = image.getHeight() * (HEIGHT - 100) / HEIGHT;
        int minGap = (int) Math.ceil(4.0 * image.getHeight() / HEIGHT);
        int lastInk = firstRow - minGap - 1;
        int bands = 0;
        for (int y = firstRow; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0x00ffffff) != 0) {
                    if (y - lastInk > minGap)
                        bands++;
                    lastInk = y;
                    break;
                }
            }
        }
        return bands;
    }

    private static void nativeWindow(Path screenshots) throws Exception {
        Frame[] window = new Frame[1];
        EventQueue.invokeAndWait(() -> {
            window[0] = new Frame(WIDTH, HEIGHT);
            window[0].setTitle("Invaders display check");
        });
        Frame frame = window[0];
        try {
            frame.applyWindowMode(WindowSettings.Mode.LARGE);
            Core.getDrawManager().setFrame(frame);
            Canvas surface = (Canvas) field(Frame.class, "surface").get(frame);
            var transform = surface.getGraphicsConfiguration().getDefaultTransform();
            System.out.printf(Locale.ROOT, "Desktop scaling: %.0f%%%n", transform.getScaleX() * 100);
            Dimension originalWindow = frame.getSize();
            var initialRaster = frame.renderSize(WIDTH, HEIGHT);
            System.out.printf("Desktop: %s; window: %s; canvas: %s; preferred: %s; raster: %s%n",
                    surface.getGraphicsConfiguration().getBounds(), originalWindow, surface.getSize(),
                    surface.getPreferredSize(), initialRaster);
            check(initialRaster.width > WIDTH && initialRaster.height > HEIGHT, "Native game opens in HD");

            SettingsScreen settings = new SettingsScreen(WIDTH, HEIGHT, 60);
            field(Cooldown.class, "time").setLong(field(screen.Screen.class, "inputDelay").get(settings), 0);
            Method update = method(SettingsScreen.class, "update");
            Rectangle chinese = (Rectangle) method(SettingsScreen.class, "languageOption", int.class)
                    .invoke(settings, I18n.Language.CHINESE.ordinal());
            Point select = windowPoint(surface, chinese);
            click(surface, select, true);
            update.invoke(settings);
            click(surface, select, false);
            update.invoke(settings);
            check(I18n.language() == I18n.Language.CHINESE, "Click selects Chinese at native HD scale");
            Files.createDirectories(screenshots);
            BufferedImage image = (BufferedImage) field(DrawManager.class, "backBuffer").get(null);
            ImageIO.write(image, "png", screenshots.resolve("settings-hd-chinese.png").toFile());
            update.invoke(settings);
            check(image == field(DrawManager.class, "backBuffer").get(null),
                    "Consecutive HD frames reuse the raster");

            resize(frame, new Dimension(originalWindow.width + 100, originalWindow.height));
            Rectangle viewport = Frame.viewport(surface.getWidth(), surface.getHeight(), WIDTH, HEIGHT);
            check(viewport.x > 0, "Resized desktop window letterboxes");
            click(surface, new Point(viewport.x - 1, viewport.y + viewport.height / 2), true);
            check(!Core.getInputManager().isLeftMousePressed(), "Clicks on the border are ignored");
            Rectangle test = (Rectangle) method(SettingsScreen.class, "testButton").invoke(settings);
            Point open = windowPoint(surface, test);
            click(surface, open, true);
            update.invoke(settings);
            click(surface, open, false);
            update.invoke(settings);
            check(field(SettingsScreen.class, "testDialog").getBoolean(settings),
                    "The lower-right test button is clickable in a letterboxed window");
            ImageIO.write((BufferedImage) field(DrawManager.class, "backBuffer").get(null),
                    "png", screenshots.resolve("test-dialog-hd-chinese.png").toFile());
            method(SettingsScreen.class, "closeTestDialog").invoke(settings);

            EventQueue.invokeAndWait(() -> frame.setExtendedState(Frame.ICONIFIED));
            update.invoke(settings);
            EventQueue.invokeAndWait(() -> frame.setExtendedState(Frame.NORMAL));
            resize(frame, originalWindow);
            update.invoke(settings);
            check(frame.renderSize(WIDTH, HEIGHT).equals(initialRaster),
                    "Restoring a minimized window restores HD rendering");

            GameScreen game = new GameScreen(new GameState(1, 0, 3, 0, 0, 0),
                    new GameSettings(5, 3, 60, 2000), false, WIDTH, HEIGHT, 60);
            game.initialize();
            Method draw = method(GameScreen.class, "draw");
            var chrome = frame.getInsets();
            resize(frame, new Dimension((int) Math.round(WIDTH / transform.getScaleX())
                    + chrome.left + chrome.right, (int) Math.round(HEIGHT / transform.getScaleY())
                    + chrome.top + chrome.bottom));
            cashOutHints(screenshots, "standard");
            measure(game, draw, frame, "Campaign standard");
            resize(frame, originalWindow);
            cashOutHints(screenshots, "hd");
            measure(game, draw, frame, "Campaign HD");
            ImageIO.write((BufferedImage) field(DrawManager.class, "backBuffer").get(null),
                    "png", screenshots.resolve("campaign-hd-chinese.png").toFile());

            var constructor = EndlessMode.class.getDeclaredConstructor(int.class, int.class, boolean.class);
            constructor.setAccessible(true);
            EndlessMode endless = constructor.newInstance(WIDTH, HEIGHT, true);
            endless.initialize();
            var run = (EndlessMode.Run) field(EndlessMode.class, "run").get(endless);
            field(EndlessMode.Run.class, "phase").set(run, EndlessMode.Run.Phase.PLAYING);
            field(EndlessMode.Run.class, "lives").setInt(run, 1);
            field(EndlessMode.class, "lowHealthTime").setDouble(endless, 1.0);
            measure(endless, method(EndlessMode.class, "draw"), frame, "Endless HD, low-life effect");
            ImageIO.write((BufferedImage) field(DrawManager.class, "backBuffer").get(null),
                    "png", screenshots.resolve("endless-hd-chinese.png").toFile());
        } finally {
            EventQueue.invokeAndWait(frame::dispose);
        }
    }

    public static void main(String[] args) throws Exception {
        geometry();
        if (args.length != 0)
            nativeWindow(Path.of(args[0]));
        System.out.println("PASS: " + checks + " display checks");
    }
}
