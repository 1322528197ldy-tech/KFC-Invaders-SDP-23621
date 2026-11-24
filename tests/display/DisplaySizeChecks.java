package screen;

import engine.*;
import engine.soundEngine.Sound;
import engine.soundEngine.SoundManager;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import sun.misc.Unsafe;

/** Real pointer dispatch through the presentation layer, settings and original menu hooks. */
public final class DisplaySizeChecks {
    private static int checks;
    private static Field field(Class<?> c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
    private static final int W=434,H=497;
    public static final class FakeFrame extends engine.Frame {
        GameViewport panel;BufferedImage last;Dimension physical;
        public FakeFrame(){super(448,520);}
        @Override public Insets getInsets(){return new Insets(0,0,0,0);}
        @Override public Container getContentPane(){return panel;}
        @Override public void present(BufferedImage image){last=image;panel.present(image);}
        @Override public void present(BufferedImage image,Color surround){last=image;panel.present(image,surround);}
        @Override public void applyWindowMode(WindowSettings.Mode mode){
            physical=mode.fit(1600,1100);panel.setSize(physical);WindowSettings.select(mode);
        }
        @Override public String displaySizeLabel(){return physical.width+"x"+physical.height;}
    }
    private static FakeFrame frame;
    private static Object controls;
    private static Point physical(int x,int y){
        Rectangle b=frame.panel.transform().bounds();
        return new Point(b.x+(int)Math.floor((x+0.5)*b.width/W),b.y+(int)Math.floor((y+0.5)*b.height/H));
    }
    private static void event(int id,int x,int y,int button)throws Exception{
        MouseEvent e=new MouseEvent(frame.panel,id,0,0,x,y,1,false,button);
        SwingUtilities.invokeAndWait(()->frame.panel.dispatchEvent(e));
    }
    private static void logicalEvent(int id,int x,int y,int button)throws Exception{
        Point p=physical(x,y);event(id,p.x,p.y,button);
    }
    private static SettingsScreen settings()throws Exception{
        SettingsScreen s=new SettingsScreen(W,H,60);s.isRunning=true;
        field(Cooldown.class,"time").set(s.inputDelay,0L);s.update();return s;
    }
    private static void key(SettingsScreen screen,int code)throws Exception{
        field(Cooldown.class,"time").set(field(SettingsScreen.class,"selectionCooldown").get(screen),0L);
        Core.getInputManager().keyPressed(new KeyEvent(frame.panel,KeyEvent.KEY_PRESSED,0,0,code,KeyEvent.CHAR_UNDEFINED));
        screen.update();
        Core.getInputManager().keyReleased(new KeyEvent(frame.panel,KeyEvent.KEY_RELEASED,0,0,code,KeyEvent.CHAR_UNDEFINED));
        screen.update();
    }
    private static void preview(Path dir,String name)throws Exception{
        BufferedImage image=new BufferedImage(frame.panel.getWidth(),frame.panel.getHeight(),BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(()->{Graphics2D g=image.createGraphics();frame.panel.paint(g);g.dispose();});
        ImageIO.write(image,"png",dir.resolve(name+".png").toFile());
    }
    public static void main(String[] args)throws Exception{
        Path output=Path.of(args[0]);Files.createDirectories(output);
        System.setProperty("game.displaySettingsDir",Files.createTempDirectory(output,"preferences-").toString());
        Core.getLogger().setLevel(java.util.logging.Level.OFF);SoundManager.getInstance().setMuted(true);
        check(WindowSettings.mode()==WindowSettings.Mode.ORIGINAL,"first launch keeps original size");
        Path preferences=output.resolve("preference-roundtrip.properties");
        for(WindowSettings.Mode mode:WindowSettings.Mode.values()){
            check(WindowSettings.save(preferences,mode)&&WindowSettings.read(preferences)==mode,"mode survives restart / "+mode);
            for(Dimension area:new Dimension[]{new Dimension(1920,1032),new Dimension(1280,680),new Dimension(320,200)}){
                Dimension fitted=mode.fit(area.width,area.height);
                check(fitted.width>0&&fitted.height>0&&fitted.width<=area.width&&fitted.height<=area.height,"mode never exceeds usable screen");
            }
        }
        Files.writeString(preferences,"window.size=unknown\n");check(WindowSettings.read(preferences)==WindowSettings.Mode.ORIGINAL,"unknown preference safely falls back");
        Files.writeString(preferences,"window.size=\\uZZZZ\n");check(WindowSettings.read(preferences)==WindowSettings.Mode.ORIGINAL,"damaged preference safely falls back");
        check(WindowSettings.read(output.resolve("missing.properties"))==WindowSettings.Mode.ORIGINAL,"missing preference falls back");
        check(!WindowSettings.save(output,WindowSettings.Mode.MEDIUM),"unwritable file target does not crash");
        Unsafe unsafe=(Unsafe)field(Unsafe.class,"theUnsafe").get(null);
        frame=(FakeFrame)unsafe.allocateInstance(FakeFrame.class);frame.panel=new GameViewport(W,H);
        Core.getDrawManager().setFrame(frame);
        field(EndlessMode.class,"boundFrame").set(null,frame);
        controls=field(EndlessMode.class,"controls").get(null);
        frame.panel.addMouseListener(Core.getInputManager());frame.panel.addMouseMotionListener(Core.getInputManager());
        frame.panel.addMouseListener((MouseListener)controls);frame.panel.addMouseMotionListener((MouseMotionListener)controls);
        AtomicInteger presses=new AtomicInteger();frame.panel.addMouseListener(new MouseAdapter(){@Override public void mousePressed(MouseEvent e){presses.incrementAndGet();}});
        for(WindowSettings.Mode mode:WindowSettings.Mode.values()){
            frame.applyWindowMode(mode);
            for(Point logical:new Point[]{new Point(86,132),new Point(345,199),new Point(217,256),new Point(100,390)}){
                logicalEvent(MouseEvent.MOUSE_MOVED,logical.x,logical.y,MouseEvent.NOBUTTON);
                check(Math.abs(Core.getInputManager().getMouseX()-logical.x)<=1&&Math.abs(Core.getInputManager().getMouseY()-logical.y)<=1,"pointer maps to original coordinates / "+mode);
            }
            Rectangle b=frame.panel.transform().bounds();
            if(b.x>0||b.y>0){
                int before=presses.get();event(MouseEvent.MOUSE_PRESSED,0,0,MouseEvent.BUTTON1);
                check(before==presses.get()&&!Core.getInputManager().isLeftMousePressed(),"black bars cannot activate buttons");
            }
            logicalEvent(MouseEvent.MOUSE_PRESSED,100,132,MouseEvent.BUTTON1);
            check(Core.getInputManager().isLeftMousePressed(),"inside press is delivered");
            event(MouseEvent.MOUSE_RELEASED,-15,-15,MouseEvent.BUTTON1);
            check(!Core.getInputManager().isLeftMousePressed(),"release outside clears held button");
            SettingsScreen screen=settings();
            // Drag each real volume slider from one endpoint to the other at every display size.
            Method sliderY=SettingsScreen.class.getDeclaredMethod("sliderY",int.class);sliderY.setAccessible(true);
            for(int index=0;index<2;index++){
                int y=(Integer)sliderY.invoke(screen,index);
                logicalEvent(MouseEvent.MOUSE_PRESSED,W/5,y,MouseEvent.BUTTON1);screen.update();
                Sound.Category category=index==0?Sound.Category.BGM:Sound.Category.SFX;
                check(SoundManager.getInstance().getVolume(category)<=0.01,"scaled slider reaches zero");
                logicalEvent(MouseEvent.MOUSE_DRAGGED,W/5+W*3/5,y,MouseEvent.NOBUTTON);screen.update();
                check(SoundManager.getInstance().getVolume(category)>=0.99,"scaled slider reaches full volume");
                logicalEvent(MouseEvent.MOUSE_RELEASED,W/5+W*3/5,y,MouseEvent.BUTTON1);screen.update();
            }
            field(SettingsScreen.class,"currentMenuItem").setInt(screen,2);
            screen.update();preview(output,"settings-"+mode.id);
            float bgm=SoundManager.getInstance().getVolume(Sound.Category.BGM),sfx=SoundManager.getInstance().getVolume(Sound.Category.SFX);
            WindowSettings.Mode previous=WindowSettings.mode();key(screen,KeyEvent.VK_RIGHT);
            check(WindowSettings.mode()==previous.step(1),"keyboard changes size immediately");
            check(SoundManager.getInstance().getVolume(Sound.Category.BGM)==bgm&&SoundManager.getInstance().getVolume(Sound.Category.SFX)==sfx,"size selection never changes audio");
            check(screen.getWidth()==W&&screen.getHeight()==H,"settings retains original logical coordinates");
            check(WindowSettings.read(WindowSettings.preferenceFile())==WindowSettings.mode(),"selected setting persisted");
            Method sizeY=SettingsScreen.class.getDeclaredMethod("windowSizeY");sizeY.setAccessible(true);
            int y=(Integer)sizeY.invoke(screen);
            previous=WindowSettings.mode();logicalEvent(MouseEvent.MOUSE_PRESSED,W-55,y,MouseEvent.BUTTON1);screen.update();
            logicalEvent(MouseEvent.MOUSE_RELEASED,W-55,y,MouseEvent.BUTTON1);screen.update();
            check(WindowSettings.mode()==previous.step(1),"scaled size arrow responds once");
            key(screen,KeyEvent.VK_ESCAPE);check(!screen.isRunning&&screen.returnCode==1,"Escape returns from settings");
            frame.applyWindowMode(mode);
            // Use the original Endless menu hook through its actual additional mouse listener.
            TitleScreen menu=new TitleScreen(W,H,60);Core.getDrawManager().initDrawing(menu);EndlessMode.drawEntry(menu);
            FontMetrics metrics=(FontMetrics)field(DrawManager.class,"fontRegularMetrics").get(null);
            int baseline=H/2+metrics.getHeight()*5/4*2;
            logicalEvent(MouseEvent.MOUSE_PRESSED,W/2,baseline-4,MouseEvent.BUTTON1);EndlessMode.updateEntry(menu);
            logicalEvent(MouseEvent.MOUSE_RELEASED,W/2,baseline-4,MouseEvent.BUTTON1);
            check(field(EndlessMode.class,"gateOpen").getBoolean(null),"scaled Endless button opens the real gate");
            // Cancel using the existing gate's back button (400x386 centered in the same logical canvas).
            logicalEvent(MouseEvent.MOUSE_PRESSED,(W-400)/2+50,(H-386)/2+337,MouseEvent.BUTTON1);EndlessMode.updateEntry(menu);
            logicalEvent(MouseEvent.MOUSE_RELEASED,(W-400)/2+50,(H-386)/2+337,MouseEvent.BUTTON1);
            check(!field(EndlessMode.class,"gateOpen").getBoolean(null),"scaled gate Back button works");
            AtomicLong clock=new AtomicLong(1_000_000_000L);
            StartupScreen intro=new StartupScreen(W,H,60,clock::get);intro.initialize();intro.isRunning=true;
            clock.addAndGet(StartupAnimation.SCENE_DURATION_MS*500_000L);intro.update();
            check(intro.isRunning&&frame.last.getWidth()==W&&frame.last.getHeight()==H,"school scene uses the original logical canvas");
            clock.addAndGet(StartupAnimation.SCENE_DURATION_MS*1_000_000L);intro.update();
            check(intro.isRunning,"team scene remains active at every display size");
            clock.addAndGet(StartupAnimation.SCENE_DURATION_MS*500_000L);intro.update();
            check(!intro.isRunning,"opening finishes at the current repository duration");
            for(int skip:new int[]{KeyEvent.VK_SPACE,KeyEvent.VK_ENTER,KeyEvent.VK_ESCAPE}){
                intro=new StartupScreen(W,H,60,clock::get);intro.initialize();intro.isRunning=true;
                Core.getInputManager().keyPressed(new KeyEvent(frame.panel,KeyEvent.KEY_PRESSED,0,0,skip,KeyEvent.CHAR_UNDEFINED));
                intro.update();check(!intro.isRunning,"skip key still works at every scale");
                TitleScreen next=new TitleScreen(W,H,60);next.isRunning=true;
                field(Cooldown.class,"time").set(next.inputDelay,0L);next.update();
                check(next.isRunning&&!field(TitleScreen.class,"showingExitConfirm").getBoolean(next),"held skip does not activate the scaled menu");
                Core.getInputManager().keyReleased(new KeyEvent(frame.panel,KeyEvent.KEY_RELEASED,0,0,skip,KeyEvent.CHAR_UNDEFINED));
            }
        }
        for(Dimension physical:new Dimension[]{new Dimension(434,497),new Dimension(672,780),new Dimension(896,1040),new Dimension(1500,800)}){
            GameViewport panel=new GameViewport(4,4);panel.setSize(physical);
            BufferedImage colors=new BufferedImage(4,4,BufferedImage.TYPE_INT_RGB);
            colors.setRGB(0,0,Color.RED.getRGB());colors.setRGB(3,0,Color.GREEN.getRGB());colors.setRGB(0,3,Color.BLUE.getRGB());colors.setRGB(3,3,Color.WHITE.getRGB());
            panel.present(colors);BufferedImage actual=new BufferedImage(physical.width,physical.height,BufferedImage.TYPE_INT_RGB);
            Graphics2D g=actual.createGraphics();panel.paint(g);g.dispose();Rectangle b=panel.transform().bounds();
            check(actual.getRGB(b.x,b.y)==Color.RED.getRGB()&&actual.getRGB(b.x+b.width-1,b.y+b.height-1)==Color.WHITE.getRGB(),"complete frame survives letterboxing and scaling");
        }
        System.out.println("PASS: "+checks+" display scaling, settings, input and preference checks");
    }
}
