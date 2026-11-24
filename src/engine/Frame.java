package engine;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import screen.Screen;

/** Presents the game in a DPI-aware window while keeping the logical playfield fixed. */
@SuppressWarnings("serial")
public class Frame extends JFrame {
    static final int MAX_RENDER_SCALE = 2;
    private final int gameWidth;
    private final int gameHeight;
    private final Canvas surface;
    private volatile Screen currentScreen;

    public Frame(int width, int height) {
        this.gameWidth = width;
        this.gameHeight = height;
        setTitle("Invaders");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        this.surface = new Canvas();
        surface.setBackground(Color.BLACK);
        surface.setIgnoreRepaint(true);
        surface.setFocusable(true);
        add(surface);
        addGameKeyListener(Core.getInputManager());
        addGameMouseListener(Core.getInputManager());

        pack();
        GraphicsConfiguration config = getGraphicsConfiguration();
        Rectangle desktop = config.getBounds();
        Insets reserved = Toolkit.getDefaultToolkit().getScreenInsets(config);
        Insets chrome = getInsets();
        Dimension preferred = windowSize(width, height,
                desktop.width - reserved.left - reserved.right - chrome.left - chrome.right - 16,
                desktop.height - reserved.top - reserved.bottom - chrome.top - chrome.bottom - 16,
                config.getDefaultTransform().getScaleX(), config.getDefaultTransform().getScaleY());
        setMinimumSize(new Dimension(Math.min(preferred.width, width / 2) + chrome.left + chrome.right,
                Math.min(preferred.height, height / 2) + chrome.top + chrome.bottom));
        surface.setPreferredSize(preferred);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
        surface.createBufferStrategy(2);
        addWindowFocusListener(new WindowAdapter() {
            @Override public void windowGainedFocus(WindowEvent event) {
                surface.requestFocusInWindow();
            }
        });
        surface.requestFocusInWindow();
    }

    public final int setScreen(Screen screen) {
        currentScreen = screen;
        screen.initialize();
        return screen.run();
    }

    public void addGameKeyListener(KeyListener listener) {
        addKeyListener(listener);
        if (surface != null)
            surface.addKeyListener(listener);
    }

    public void addGameMouseListener(MouseListener listener) {
        var target = surface == null ? getContentPane() : surface;
        target.addMouseListener(listener);
        if (listener instanceof MouseMotionListener motion)
            target.addMouseMotionListener(motion);
    }

    static Dimension windowSize(int width, int height, int availableWidth, int availableHeight,
            double dpiX, double dpiY) {
        double preferredScale = MAX_RENDER_SCALE / Math.max(dpiX, dpiY);
        double fit = Math.min(availableWidth / (double) width, availableHeight / (double) height);
        double scale = Math.max(0, Math.min(fit, preferredScale));
        return new Dimension(Math.max(1, (int) Math.floor(width * scale)),
                Math.max(1, (int) Math.floor(height * scale)));
    }

    static Rectangle viewport(int canvasWidth, int canvasHeight, int gameWidth, int gameHeight) {
        if (canvasWidth <= 0 || canvasHeight <= 0)
            return new Rectangle();
        double scale = Math.min(canvasWidth / (double) gameWidth, canvasHeight / (double) gameHeight);
        int width = Math.max(1, (int) Math.round(gameWidth * scale));
        int height = Math.max(1, (int) Math.round(gameHeight * scale));
        return new Rectangle((canvasWidth - width) / 2, (canvasHeight - height) / 2, width, height);
    }

    static Point mapPoint(Point point, Rectangle view, int gameWidth, int gameHeight) {
        if (view.isEmpty())
            return new Point(-1, -1);
        return new Point((int) Math.floor((point.x - view.x) * (double) gameWidth / view.width),
                (int) Math.floor((point.y - view.y) * (double) gameHeight / view.height));
    }

    public static Point gamePoint(MouseEvent event) {
        return gamePoint(event, false);
    }

    /** Returns null when a new click lands in the black border outside the game. */
    public static Point gameClick(MouseEvent event) {
        return gamePoint(event, true);
    }

    private static Point gamePoint(MouseEvent event, boolean click) {
        if (SwingUtilities.getWindowAncestor(event.getComponent()) instanceof Frame frame
                && frame.surface != null) {
            Point point = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), frame.surface);
            Rectangle view = frame.viewport();
            if (click && !view.contains(point))
                return null;
            return mapPoint(point, view, frame.gameWidth, frame.gameHeight);
        }
        return event.getPoint();
    }

    private Rectangle viewport() {
        Dimension size = surface.getSize();
        return viewport(size.width, size.height, gameWidth, gameHeight);
    }

    static Dimension rasterSize(Rectangle view, int width, int height, double dpiX, double dpiY) {
        return new Dimension(Math.max(1, Math.min(width * MAX_RENDER_SCALE,
                        (int) Math.round(view.width * dpiX))),
                Math.max(1, Math.min(height * MAX_RENDER_SCALE, (int) Math.round(view.height * dpiY))));
    }

    Dimension renderSize(int width, int height) {
        if (surface == null)
            return new Dimension(width, height);
        Rectangle view = viewport();
        if (view.isEmpty())
            return new Dimension(width, height);
        var transform = surface.getGraphicsConfiguration().getDefaultTransform();
        return rasterSize(view, width, height, transform.getScaleX(), transform.getScaleY());
    }

    void present(BufferedImage image) {
        if (surface == null || !surface.isShowing() || (getExtendedState() & ICONIFIED) != 0)
            return;
        BufferStrategy buffers = surface.getBufferStrategy();
        if (buffers == null)
            return;
        for (int attempt = 0; attempt < 2; attempt++) {
            Graphics2D graphics = (Graphics2D) buffers.getDrawGraphics();
            try {
                Rectangle view = viewport();
                graphics.setColor(Color.BLACK);
                graphics.fillRect(0, 0, surface.getWidth(), surface.getHeight());
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.drawImage(image, view.x, view.y, view.width, view.height, null);
            } finally {
                graphics.dispose();
            }
            if (!buffers.contentsRestored()) {
                buffers.show();
                if (!buffers.contentsLost())
                    return;
            }
        }
    }
}
