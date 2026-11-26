package screen;

import engine.*;
import engine.soundEngine.SoundManager;
import entity.Ship;
import java.awt.event.*;
import java.lang.reflect.*;
import java.nio.file.*;
import sun.misc.Unsafe;

/** Real campaign updates with the production viewport, across all display modes. */
public final class GameplaySizeChecks {
    private static int checks;
    private static Field field(Class<?> c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}
    private static Object get(GameScreen s,String n)throws Exception{return field(GameScreen.class,n).get(s);}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    private static DisplaySizeChecks.FakeFrame frame;
    private static void press(int key){Core.getInputManager().keyPressed(new KeyEvent(frame.panel,KeyEvent.KEY_PRESSED,0,0,key,KeyEvent.CHAR_UNDEFINED));}
    private static void release(int key){Core.getInputManager().keyReleased(new KeyEvent(frame.panel,KeyEvent.KEY_RELEASED,0,0,key,KeyEvent.CHAR_UNDEFINED));}
    private static void pause(GameScreen game)throws Exception{
        field(Cooldown.class,"time").setLong(get(game,"pauseKeyCooldown"),0);
        press(KeyEvent.VK_P);game.update();release(KeyEvent.VK_P);game.update();
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("game.displaySettingsDir",Files.createTempDirectory("kfc-gameplay-prefs-").toString());
        Core.getLogger().setLevel(java.util.logging.Level.OFF);SoundManager.getInstance().setMuted(true);
        Unsafe unsafe=(Unsafe)field(Unsafe.class,"theUnsafe").get(null);
        frame=(DisplaySizeChecks.FakeFrame)unsafe.allocateInstance(DisplaySizeChecks.FakeFrame.class);
        frame.panel=new GameViewport(434,497);Core.getDrawManager().setFrame(frame);
        field(EndlessMode.class,"boundFrame").set(null,frame);
        for(WindowSettings.Mode mode:WindowSettings.Mode.values())for(int level:new int[]{1,2,3,4,5,6,7,8,9,10}){
            frame.applyWindowMode(mode);Core.getInputManager().resetInput();
            GameSettings settings=(GameSettings)field(Core.class,"SETTINGS_LEVEL_"+level).get(null);
            GameScreen game=new GameScreen(new GameState(level,0,3,0,0,0),settings,false,434,497,60);
            game.initialize();field(Cooldown.class,"time").setLong(field(Screen.class,"inputDelay").get(game),0);
            Ship ship=(Ship)get(game,"ship");int initial=ship.getPositionX();
            press(KeyEvent.VK_D);for(int i=0;i<8;i++)game.update();release(KeyEvent.VK_D);
            check(ship.getPositionX()>initial,"right movement / "+mode+" / "+level);
            press(KeyEvent.VK_A);for(int i=0;i<8;i++)game.update();release(KeyEvent.VK_A);
            check(ship.getPositionX()==initial,"left movement / "+mode+" / "+level);
            press(KeyEvent.VK_SPACE);game.update();release(KeyEvent.VK_SPACE);
            check((Integer)get(game,"bulletsShot")>0,"shooting works");
            pause(game);check((Boolean)get(game,"paused"),"pause opens");
            int x=ship.getPositionX(),lives=(Integer)get(game,"lives"),score=(Integer)get(game,"score");
            for(WindowSettings.Mode changed:WindowSettings.Mode.values()){
                frame.applyWindowMode(changed);press(KeyEvent.VK_D);game.update();release(KeyEvent.VK_D);
                check(ship.getPositionX()==x,"paused ship stays fixed across resize");
                check((Integer)get(game,"lives")==lives&&(Integer)get(game,"score")==score,"resize preserves lives and score");
                check(game.getWidth()==434&&game.getHeight()==497,"logical bounds remain fixed");
            }
            pause(game);check(!(Boolean)get(game,"paused"),"resume works");
            press(KeyEvent.VK_D);game.update();release(KeyEvent.VK_D);check(ship.getPositionX()>x,"movement works after resume");
            Core.getInputManager().resetInput();
        }
        System.out.println("PASS: "+checks+" cross-size campaign checks (10 stages x 4 modes)");
    }
}
