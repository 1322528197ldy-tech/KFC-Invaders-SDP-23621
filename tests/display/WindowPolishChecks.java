package screen;

import engine.*;
import engine.soundEngine.Sound;
import engine.soundEngine.SoundManager;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import sun.misc.Unsafe;

/** Regressions for white/fading intro surrounds and the scaled Settings Back button. */
public final class WindowPolishChecks {
    private static final int W=434,H=497;
    private static int checks;
    private static DisplaySizeChecks.FakeFrame frame;
    private static Field field(Class<?> owner,String name)throws Exception{Field f=owner.getDeclaredField(name);f.setAccessible(true);return f;}
    private static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);checks++;}
    private static BufferedImage snapshot()throws Exception{
        BufferedImage image=new BufferedImage(frame.panel.getWidth(),frame.panel.getHeight(),BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(()->{Graphics2D g=image.createGraphics();frame.panel.paint(g);g.dispose();});return image;
    }
    private static void pointer(int id,int x,int y,int button)throws Exception{
        Rectangle b=frame.panel.transform().bounds();
        int px=b.x+(int)Math.floor((x+0.5)*b.width/W),py=b.y+(int)Math.floor((y+0.5)*b.height/H);
        MouseEvent event=new MouseEvent(frame.panel,id,0,0,px,py,1,false,button);
        SwingUtilities.invokeAndWait(()->frame.panel.dispatchEvent(event));
    }
    private static SettingsScreen settings()throws Exception{
        SettingsScreen s=new SettingsScreen(W,H,60);s.isRunning=true;
        field(Cooldown.class,"time").set(s.inputDelay,0L);s.update();return s;
    }
    public static void main(String[] args)throws Exception{
        Path output=Path.of(args[0]);Files.createDirectories(output);
        System.setProperty("game.displaySettingsDir",Files.createTempDirectory(output,"preferences-").toString());
        Core.getLogger().setLevel(java.util.logging.Level.OFF);SoundManager.getInstance().setMuted(true);
        Unsafe unsafe=(Unsafe)field(Unsafe.class,"theUnsafe").get(null);
        frame=(DisplaySizeChecks.FakeFrame)unsafe.allocateInstance(DisplaySizeChecks.FakeFrame.class);
        frame.panel=new GameViewport(W,H);frame.panel.addMouseListener(Core.getInputManager());
        frame.panel.addMouseMotionListener(Core.getInputManager());Core.getDrawManager().setFrame(frame);
        field(EndlessMode.class,"boundFrame").set(null,frame);
        Dimension[] clients={new Dimension(434,483),new Dimension(658,743),new Dimension(882,1003),new Dimension(1500,900)};
        WindowSettings.Mode[] modes=WindowSettings.Mode.values();
        for(int index=0;index<modes.length;index++){
            WindowSettings.Mode mode=modes[index];frame.applyWindowMode(mode);frame.panel.setSize(clients[index]);
            Rectangle geometry=frame.panel.transform().bounds();
            AtomicLong clock=new AtomicLong(5_000_000_000L);long start=clock.get();
            StartupScreen intro=new StartupScreen(W,H,60,clock::get);intro.initialize();intro.isRunning=true;
            for(long elapsed:new long[]{0,120,300,800,1000,1700,1999,2000,2400,3000,3500,3800,3950}){
                clock.set(start+elapsed*1_000_000L);intro.update();BufferedImage image=snapshot();
                int expected=frame.last.getRGB(0,0);
                for(Point edge:new Point[]{new Point(0,0),new Point(image.getWidth()-1,0),
                        new Point(0,image.getHeight()-1),new Point(image.getWidth()-1,image.getHeight()-1),
                        new Point(0,image.getHeight()/2),new Point(image.getWidth()-1,image.getHeight()/2)})
                    check(image.getRGB(edge.x,edge.y)==expected,"intro surround follows white and fades / "+mode+" / "+elapsed);
                check(frame.panel.transform().bounds().equals(geometry),"background fix cannot alter scaling geometry");
                if(elapsed==1000&&index==1)ImageIO.write(image,"png",output.resolve("intro-school-150.png").toFile());
                if(elapsed==3000&&index==1)ImageIO.write(image,"png",output.resolve("intro-team-150.png").toFile());
            }
            SettingsScreen s=settings();
            check(snapshot().getRGB(0,0)==Color.BLACK.getRGB(),"ordinary screens restore their black surround");
            WindowSettings.Mode previous=WindowSettings.mode();
            float bgm=SoundManager.getInstance().getVolume(Sound.Category.BGM),sfx=SoundManager.getInstance().getVolume(Sound.Category.SFX);
            pointer(MouseEvent.MOUSE_MOVED,50,26,MouseEvent.NOBUTTON);s.update();
            if(index==0||index==1)ImageIO.write(snapshot(),"png",output.resolve(index==0?"settings-back-original.png":"settings-back-150.png").toFile());
            pointer(MouseEvent.MOUSE_PRESSED,50,26,MouseEvent.BUTTON1);s.update();
            check(!s.isRunning&&s.returnCode==1,"scaled Back button returns from settings / "+mode);
            check(field(SettingsScreen.class,"draggingSlider").getInt(s)==-1,"return ends slider dragging");
            pointer(MouseEvent.MOUSE_RELEASED,50,26,MouseEvent.BUTTON1);
            check(WindowSettings.mode()==previous,"Back preserves selected size");
            check(SoundManager.getInstance().getVolume(Sound.Category.BGM)==bgm&&SoundManager.getInstance().getVolume(Sound.Category.SFX)==sfx,"Back preserves volumes");
            s=settings();pointer(MouseEvent.MOUSE_PRESSED,11,26,MouseEvent.BUTTON1);s.update();
            check(s.isRunning,"click outside Back does not return");pointer(MouseEvent.MOUSE_RELEASED,11,26,MouseEvent.BUTTON1);s.update();
            pointer(MouseEvent.MOUSE_PRESSED,210,85,MouseEvent.BUTTON1);s.update();
            pointer(MouseEvent.MOUSE_DRAGGED,50,26,MouseEvent.NOBUTTON);s.update();
            check(s.isRunning,"dragging an existing press over Back cannot activate it");
            pointer(MouseEvent.MOUSE_RELEASED,50,26,MouseEvent.BUTTON1);s.update();
            Core.getInputManager().keyPressed(new KeyEvent(frame.panel,KeyEvent.KEY_PRESSED,0,0,KeyEvent.VK_ESCAPE,KeyEvent.CHAR_UNDEFINED));
            s.update();check(!s.isRunning,"Escape still returns at every size");
            Core.getInputManager().keyReleased(new KeyEvent(frame.panel,KeyEvent.KEY_RELEASED,0,0,KeyEvent.VK_ESCAPE,KeyEvent.CHAR_UNDEFINED));
        }
        System.out.println("PASS: "+checks+" intro surround and Settings Back checks");
    }
}
