package screen;

import engine.*;
import engine.soundEngine.SoundManager;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.*;
import javax.imageio.metadata.*;
import javax.imageio.stream.ImageOutputStream;
import sun.misc.Unsafe;

/** Deterministic render preview and transition/input tests, without a display or extra libraries. */
public final class StartupAnimationTest {
    private static int checks;
    public static final class Offscreen extends engine.Frame {
        public Offscreen() { super(448,520); }
        @Override public Graphics getGraphics() { return new BufferedImage(448,520,1).getGraphics(); }
        @Override public Insets getInsets() { return new Insets(0,0,0,0); }
    }
    private static Field field(Class<?> owner, String name) throws Exception {
        Field f=owner.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private static void check(boolean yes, String message) {
        if (!yes) throw new AssertionError(message);
        checks++; System.out.println("PASS " + message);
    }
    private static BufferedImage render(StartupAnimation intro, int w, int h, long time) {
        BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics(); intro.draw(g,w,h,time); g.dispose(); return image;
    }
    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());
    }
    private static void press(int key) {
        Core.getInputManager().keyPressed(new KeyEvent(new Canvas(),KeyEvent.KEY_PRESSED,0,0,key,KeyEvent.CHAR_UNDEFINED));
    }
    private static void release(int key) {
        Core.getInputManager().keyReleased(new KeyEvent(new Canvas(),KeyEvent.KEY_RELEASED,0,0,key,KeyEvent.CHAR_UNDEFINED));
    }
    private static StartupScreen screen(AtomicLong clock) {
        StartupScreen s=new StartupScreen(448,520,60,clock::get);
        s.initialize(); s.isRunning=true; return s;
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Core.getLogger().setLevel(java.util.logging.Level.OFF);
        Unsafe unsafe=(Unsafe)field(Unsafe.class,"theUnsafe").get(null);
        engine.Frame offscreen = (engine.Frame)unsafe.allocateInstance(Offscreen.class);
        Core.getDrawManager().setFrame(offscreen);
        // The upstream Endless entry installs native listeners; use injected offscreen input here.
        field(EndlessMode.class,"boundFrame").set(null, offscreen);
        SoundManager.getInstance().setMuted(true);
        Font font=(Font)field(DrawManager.class,"fontRegular").get(null);
        Map<DrawManager.SpriteType,boolean[][]> sprites=(Map<DrawManager.SpriteType,boolean[][]>)field(DrawManager.class,"spriteMap").get(null);
        BufferedImage logo=StartupAnimation.loadLogo();
        check(logo != null && logo.getWidth()>1000 && logo.getHeight()>700,"supplied team logo loads from bundled resources");
        BufferedImage school=StartupAnimation.loadSchoolLogo();
        check(school!=null&&school.getWidth()==school.getHeight()&&school.getWidth()>1000,"supplied Hanyang emblem loads from bundled resources");
        StartupAnimation animation=new StartupAnimation(font,school,logo);
        File dir=new File(args.length==0?"verification/preview":args[0]); dir.mkdirs();
        for (long time:new long[]{0,400,1000,1600,2300,2850,3200,3600,4200,4800,5500,6050,6400}) {
            BufferedImage image=render(animation,448,520,time);
            ImageIO.write(image,"png",new File(dir,"intro-"+time+".png"));
            check(image.getWidth()==448 && image.getHeight()==520,"renders intro at "+time+" ms");
        }
        check(Arrays.equals(pixels(render(animation,448,520,1600)),pixels(render(animation,448,520,1600))),"render is independent of frame rate and deterministic at the same timestamp");
        check(!Arrays.equals(pixels(render(animation,448,520,1000)),pixels(render(animation,448,520,1600))),"animation progresses over time");
        check(Arrays.stream(pixels(render(animation,448,520,6400))).allMatch(v->(v&0xFFFFFF)==0),"intro fades to black after both logos before returning to menu");
        check(render(animation,432,501,1500).getWidth()==432,"renderer accepts original game client dimensions");
        check(Arrays.stream(pixels(render(animation,434,497,6400))).allMatch(v->(v&0xFFFFFF)==0),"final fade covers the complete actual game viewport");
        BufferedImage blue=new BufferedImage(8,8,BufferedImage.TYPE_INT_RGB),red=new BufferedImage(8,8,BufferedImage.TYPE_INT_RGB);
        Graphics2D fill=blue.createGraphics();fill.setColor(Color.BLUE);fill.fillRect(0,0,8,8);fill.dispose();
        fill=red.createGraphics();fill.setColor(Color.RED);fill.fillRect(0,0,8,8);fill.dispose();
        StartupAnimation order=new StartupAnimation(font,blue,red);
        check(render(order,448,520,1600).getRGB(224,244)==Color.BLUE.getRGB(),"first scene is the school image");
        check(render(order,448,520,4800).getRGB(224,244)==Color.RED.getRGB(),"second scene is the team image");
        check(render(order,448,520,3200).getRGB(224,244)==Color.WHITE.getRGB(),"school transitions to team through the shared white background");
        ImageIO.write(render(animation,434,497,1600),"png",new File(dir,"school-preview.png"));
        ImageIO.write(render(animation,434,497,4800),"png",new File(dir,"team-preview.png"));
        AtomicLong clock=new AtomicLong(10_000_000_000L);
        StartupScreen s=screen(clock);
        clock.addAndGet(1_600_000_000L); s.update();
        check(s.isRunning,"intro stays active at midpoint");
        clock.addAndGet(1_600_000_000L); s.update();
        check(s.isRunning,"intro continues into the team scene after the school emblem");
        clock.addAndGet(3_200_000_000L);s.update();
        check(!s.isRunning && s.returnCode==1,"intro ends automatically at 6.4 seconds and returns menu code");
        for(long phase:new long[]{1000,4200}) for (int key:new int[]{KeyEvent.VK_SPACE,KeyEvent.VK_ENTER,KeyEvent.VK_ESCAPE}) {
            s=screen(clock);clock.addAndGet(phase*1_000_000L); press(key); s.update();
            check(!s.isRunning && s.returnCode==1,"key "+KeyEvent.getKeyText(key)+" skips immediately to menu");
            TitleScreen menu=new TitleScreen(448,520,60); menu.isRunning=true;
            field(Cooldown.class,"time").set(menu.inputDelay,0L);
            menu.update();
            check(menu.isRunning && !(boolean)field(TitleScreen.class,"showingExitConfirm").get(menu),"held skip key does not start game or open Exit confirmation");
            release(key);
        }
        s=screen(clock); press(KeyEvent.VK_A); s.update(); release(KeyEvent.VK_A);
        check(s.isRunning,"ordinary movement keys do not skip the intro");
        StartupAnimation fallback=new StartupAnimation(null,null,null);
        check(render(fallback,448,520,1600)!=null,"fallback font and absent logo do not crash rendering");
        check(render(fallback,448,520,4800)!=null,"team fallback also works when images are absent");
        makeGif(animation,new File(dir,"startup-preview.gif"));
        System.out.println("TOTAL "+checks+" startup checks passed");
        System.exit(0);
    }
    private static void makeGif(StartupAnimation animation,File file) throws Exception {
        Iterator<ImageWriter> writers=ImageIO.getImageWritersByFormatName("gif");
        ImageWriter writer=writers.next();
        try (ImageOutputStream out=ImageIO.createImageOutputStream(file)) {
            writer.setOutput(out); writer.prepareWriteSequence(null);
            int frames=(int)(StartupAnimation.DURATION_MS/80)+1;
            for (int i=0;i<frames;i++) {
                BufferedImage image=render(animation,448,520,i*80L);
                ImageWriteParam params=writer.getDefaultWriteParam();
                IIOMetadata metadata=writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image),params);
                String name=metadata.getNativeMetadataFormatName();
                IIOMetadataNode root=(IIOMetadataNode)metadata.getAsTree(name);
                IIOMetadataNode control=(IIOMetadataNode)root.getElementsByTagName("GraphicControlExtension").item(0);
                control.setAttribute("disposalMethod","none");
                control.setAttribute("userInputFlag","FALSE");
                control.setAttribute("transparentColorFlag","FALSE");
                control.setAttribute("delayTime",i==frames-1?"70":"8");
                control.setAttribute("transparentColorIndex","0");
                if (i==0) {
                    IIOMetadataNode applications=new IIOMetadataNode("ApplicationExtensions");
                    IIOMetadataNode loop=new IIOMetadataNode("ApplicationExtension");
                    loop.setAttribute("applicationID","NETSCAPE"); loop.setAttribute("authenticationCode","2.0");
                    loop.setUserObject(new byte[]{1,0,0}); applications.appendChild(loop); root.appendChild(applications);
                }
                metadata.setFromTree(name,root);
                writer.writeToSequence(new IIOImage(image,null,metadata),params);
            }
            writer.endWriteSequence();
        } finally { writer.dispose(); }
    }
}
