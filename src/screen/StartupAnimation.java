package screen;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** School emblem first, then team credit; both supplied images are preserved without edits. */
public final class StartupAnimation {
    public static final long SCENE_DURATION_MS = 3200;
    public static final long DURATION_MS = SCENE_DURATION_MS * 2;
    public static final String SCHOOL_LOGO_RESOURCE = "branding/hanyang-university-logo.png";
    public static final String LOGO_RESOURCE = "branding/kfc-team-logo.png";
    private static final int W = 448, H = 520;
    private final Font font;
    private final BufferedImage schoolLogo;
    private final BufferedImage logo;

    public StartupAnimation(Font font, BufferedImage logo) {
        this(font,loadSchoolLogo(),logo);
    }
    public StartupAnimation(Font font, BufferedImage schoolLogo, BufferedImage logo) {
        this.font = font == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 14) : font;
        this.schoolLogo = schoolLogo;
        this.logo = logo;
    }

    /** Read from the JAR once; the original desktop file is never needed at runtime. */
    public static BufferedImage loadLogo() {
        return loadImage(LOGO_RESOURCE);
    }
    public static BufferedImage loadSchoolLogo() {
        return loadImage(SCHOOL_LOGO_RESOURCE);
    }
    private static BufferedImage loadImage(String resource) {
        try (InputStream in = StartupAnimation.class.getClassLoader().getResourceAsStream(resource)) {
            if(in==null) return null;
            ImageReader reader=ImageIO.getImageReadersByFormatName("png").next();
            try(MemoryCacheImageInputStream memory=new MemoryCacheImageInputStream(in)) {
                reader.setInput(memory,true,true);return reader.read(0);
            } finally {reader.dispose();}
        } catch (IOException error) {
            engine.Core.getLogger().warning("Unable to load startup logo "+resource+": " + error.getMessage());
            return null;
        }
    }

    /** Preserve the entire image and its aspect ratio; only opacity and display size animate. */
    public void draw(Graphics2D target, int width, int height, long elapsedMs) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            java.awt.geom.AffineTransform targetTransform=g.getTransform();
            g.setColor(Color.WHITE);g.fillRect(0,0,width,height);
            double canvasScale=Math.min(width/(double)W,height/(double)H);
            g.translate((width-W*canvasScale)/2,(height-H*canvasScale)/2);
            g.scale(canvasScale,canvasScale);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, W, H);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            long elapsed=Math.max(0,elapsedMs);
            boolean showingSchool=elapsed<SCENE_DURATION_MS;
            BufferedImage current=showingSchool?schoolLogo:logo;
            double t = (showingSchool?elapsed:elapsed-SCENE_DURATION_MS) / 1000.0;
            float reveal = ease((t - 0.15) / 0.65);
            float zoom = 0.96f + 0.04f * ease(t / 2.7);
            g.setComposite(AlphaComposite.SrcOver.derive(reveal));
            if (current != null) {
                double fit = Math.min((W - 24.0) / current.getWidth(), (H - 150.0) / current.getHeight());
                double imageWidth = current.getWidth() * fit * zoom;
                double imageHeight = current.getHeight() * fit * zoom;
                double x = (W - imageWidth) / 2;
                double y = H * 0.47 - imageHeight / 2;
                java.awt.geom.AffineTransform transform = java.awt.geom.AffineTransform.getTranslateInstance(x, y);
                transform.scale(fit * zoom, fit * zoom);
                g.drawImage(current, transform, null);
            } else {
                g.setFont(font.deriveFont(32f));
                g.setColor(new Color(30, 31, 34));
                String text = showingSchool?"HANYANG UNIVERSITY":"KFC TEAM";
                if(showingSchool)g.setFont(font.deriveFont(24f));
                g.drawString(text, (W - g.getFontMetrics().stringWidth(text)) / 2, H / 2);
            }
            // Fade the school into the shared white background before revealing the team image.
            if(showingSchool) {
                g.setComposite(AlphaComposite.SrcOver.derive(ease((t-2.75)/0.45)));
                g.setColor(Color.WHITE);g.fillRect(0,0,W,H);
            }
            g.setComposite(AlphaComposite.SrcOver.derive(ease((elapsed / 1000.0 - 0.65) / 0.4)));
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            g.setColor(new Color(126, 131, 140));
            String hint = "SPACE / ENTER / ESC TO SKIP";
            g.drawString(hint, (W - g.getFontMetrics().stringWidth(hint)) / 2, 474);
            float shade = Math.max(1 - ease(elapsed / 350.0),
                    showingSchool?0:ease((t - 2.75) / 0.45));
            if (shade > 0) {
                g.setTransform(targetTransform);
                g.setComposite(AlphaComposite.SrcOver.derive(shade));
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, width, height);
            }
        } finally { g.dispose(); }
    }

    private static float ease(double value) {
        float t = (float) Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }
}
