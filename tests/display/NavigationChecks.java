package screen;

import engine.*;
import engine.soundEngine.SoundManager;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import sun.misc.Unsafe;

/** Exercises navigation through the production mouse transform and real screen updates. */
public final class NavigationChecks {
    private static int checks;
    private static DisplaySizeChecks.FakeFrame frame;
    private static Object controls;
    private static Path output;
    private static Field field(Class<?> c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}
    private static void check(boolean b,String reason){if(!b)throw new AssertionError(reason);checks++;}
    private static void ready(Screen s)throws Exception{field(Cooldown.class,"time").setLong(s.inputDelay,0);s.isRunning=true;}
    private static void mouse(int id,int x,int y)throws Exception{
        Rectangle b=frame.panel.transform().bounds();
        int px=b.x+(int)((x+.5)*b.width/434),py=b.y+(int)((y+.5)*b.height/497);
        SwingUtilities.invokeAndWait(()->frame.panel.dispatchEvent(new MouseEvent(frame.panel,id,0,0,px,py,1,false,MouseEvent.BUTTON1)));
    }
    private static void click(Screen s)throws Exception{
        Rectangle r=s.navigationBounds();mouse(MouseEvent.MOUSE_PRESSED,r.x+r.width/2,r.y+r.height/2);s.update();
        mouse(MouseEvent.MOUSE_RELEASED,r.x+r.width/2,r.y+r.height/2);
    }
    private static void key(int key,boolean down){
        KeyEvent e=new KeyEvent(frame.panel,down?KeyEvent.KEY_PRESSED:KeyEvent.KEY_RELEASED,0,0,key,KeyEvent.CHAR_UNDEFINED);
        if(down){Core.getInputManager().keyPressed(e);((KeyListener)controls).keyPressed(e);}
        else{Core.getInputManager().keyReleased(e);((KeyListener)controls).keyReleased(e);}
    }
    private static void image(String name)throws Exception{
        BufferedImage image=new BufferedImage(frame.panel.getWidth(),frame.panel.getHeight(),BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(()->{Graphics2D g=image.createGraphics();frame.panel.paint(g);g.dispose();});
        ImageIO.write(image,"png",output.resolve(name+".png").toFile());
    }
    private static Screen page(int i)throws Exception{
        switch(i){
            case 0:return new SettingsScreen(434,497,60);
            case 1:return new ShopScreen(434,497,60);
            case 2:return new ShipSelectScreen(434,497,60);
            case 3:return new AchievementsScreen(434,497,60);
            case 4:return new HighScoreScreen(434,497,60);
            case 5:return new TutorialScreen(434,497,60);
            case 6:return new ScoreScreen(434,497,60,new GameState(1,100,0,2,1,0));
            case 7:return new CashOutScreen(434,497,60,new GameState(2,100,3,2,1,7,0));
            default:
                Constructor<?> c=Class.forName("screen.EndlessMode$EndlessRecordsScreen").getDeclaredConstructor(int.class,int.class,boolean.class);
                c.setAccessible(true);return (Screen)c.newInstance(434,497,true);
        }
    }
    public static void main(String[] args)throws Exception{
        output=Path.of(args[0]);Files.createDirectories(output);
        System.setProperty("game.displaySettingsDir",Files.createTempDirectory(output,"nav-prefs-").toString());
        Core.getLogger().setLevel(java.util.logging.Level.OFF);SoundManager.getInstance().setMuted(true);
        Unsafe unsafe=(Unsafe)field(Unsafe.class,"theUnsafe").get(null);
        frame=(DisplaySizeChecks.FakeFrame)unsafe.allocateInstance(DisplaySizeChecks.FakeFrame.class);
        frame.panel=new GameViewport(434,497);Core.getDrawManager().setFrame(frame);
        field(EndlessMode.class,"boundFrame").set(null,frame);controls=field(EndlessMode.class,"controls").get(null);
        frame.panel.addMouseListener(Core.getInputManager());frame.panel.addMouseMotionListener(Core.getInputManager());
        frame.panel.addMouseListener((MouseListener)controls);frame.panel.addMouseMotionListener((MouseMotionListener)controls);
        check(!new TitleScreen(434,497,60).navigationVisible(),"main menu has no extra button");
        check(!new StartupScreen(434,497,60).navigationVisible(),"opening artwork retained");
        for(WindowSettings.Mode mode:WindowSettings.Mode.values()){
            frame.applyWindowMode(mode);
            for(int i=0;i<9;i++){
                Core.getInputManager().resetInput();((ResettableInput)controls).resetInput();
                Screen s=page(i);s.initialize();ready(s);s.update();image(s.getClass().getSimpleName()+"-"+mode.id);
                int diamonds=DiamondManager.getInstance().getDiamonds();
                click(s);check(!s.isRunning,"button leaves "+s.getClass().getSimpleName()+" / "+mode);
                if(s instanceof ScoreScreen)check(s.returnCode==1,"score returns to menu");
                if(s instanceof CashOutScreen){
                    check(((CashOutScreen)s).didCashOut(),"cash out choice preserved");
                    check(DiamondManager.getInstance().getDiamonds()==diamonds+7,"cash out banks once");
                }
            }
            Core.getInputManager().resetInput();((ResettableInput)controls).resetInput();
            GameSettings settings=(GameSettings)field(Core.class,"SETTINGS_LEVEL_1").get(null);
            GameScreen game=new GameScreen(new GameState(1,0,3,0,0,0),settings,false,434,497,60);game.initialize();ready(game);
            key(KeyEvent.VK_D,true);key(KeyEvent.VK_SPACE,true);click(game);
            PauseConfirm confirm=(PauseConfirm)field(GameScreen.class,"pauseConfirm").get(game);
            check(game.navigationBounds().getMaxY()<=game.height-80,"campaign key hints stay unobstructed");
            check(confirm.isOpen()&&!confirm.isYesSelected(),"combat quit defaults to No");
            game.update();check(confirm.isOpen()&&!confirm.isYesSelected(),"held movement/fire cannot confirm");
            image("campaign-confirm-"+mode.id);key(KeyEvent.VK_D,false);key(KeyEvent.VK_SPACE,false);game.update();
            field(Cooldown.class,"time").setLong(field(GameScreen.class,"pauseKeyCooldown").get(game),0);
            key(KeyEvent.VK_ESCAPE,true);game.update();key(KeyEvent.VK_ESCAPE,false);
            check(!confirm.isOpen()&&(Boolean)field(GameScreen.class,"paused").get(game),"cancel leaves campaign paused");
            click(game);game.update();key(KeyEvent.VK_A,true);key(KeyEvent.VK_SPACE,true);game.update();
            key(KeyEvent.VK_A,false);key(KeyEvent.VK_SPACE,false);
            check((Integer)field(GameScreen.class,"lives").get(game)==0,"confirmed quit uses existing campaign results flow");
            Core.getInputManager().resetInput();((ResettableInput)controls).resetInput();
            Constructor<EndlessMode> c=EndlessMode.class.getDeclaredConstructor(int.class,int.class,boolean.class);c.setAccessible(true);
            EndlessMode endless=c.newInstance(434,497,true);endless.initialize();ready(endless);
            EndlessMode.Run run=(EndlessMode.Run)field(EndlessMode.class,"run").get(endless);
            field(EndlessMode.Run.class,"phase").set(run,EndlessMode.Run.Phase.REWARD);
            Rectangle nav=endless.navigationBounds();
            Method refresh=EndlessMode.class.getDeclaredMethod("refreshBounds");refresh.setAccessible(true);
            check(!nav.intersects((Rectangle)refresh.invoke(endless)),"reward refresh remains unobstructed");
            field(EndlessMode.Run.class,"phase").set(run,EndlessMode.Run.Phase.PLAYING);
            key(KeyEvent.VK_D,true);key(KeyEvent.VK_SPACE,true);click(endless);endless.update();
            confirm=(PauseConfirm)field(EndlessMode.class,"pauseConfirm").get(endless);
            check(run.getPhase()==EndlessMode.Run.Phase.PAUSED&&confirm.isOpen()&&!confirm.isYesSelected(),"endless quit pauses without held-key confirmation");
            image("endless-confirm-"+mode.id);key(KeyEvent.VK_D,false);key(KeyEvent.VK_SPACE,false);endless.update();
            key(KeyEvent.VK_ESCAPE,true);endless.update();key(KeyEvent.VK_ESCAPE,false);
            check(!confirm.isOpen()&&run.getPhase()==EndlessMode.Run.Phase.PAUSED,"cancel leaves endless paused");
            click(endless);endless.update();key(KeyEvent.VK_A,true);key(KeyEvent.VK_SPACE,true);endless.update();
            key(KeyEvent.VK_A,false);key(KeyEvent.VK_SPACE,false);
            check(run.getPhase()==EndlessMode.Run.Phase.OVER&&"CASHED OUT".equals(run.getEndReason()),"confirmed endless quit uses existing cash-out flow");
        }
        System.out.println("PASS: "+checks+" navigation, settlement and quit-confirmation checks across 4 sizes");
    }
}
