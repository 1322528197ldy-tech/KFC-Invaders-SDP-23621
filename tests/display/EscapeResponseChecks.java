package screen;

import engine.*;
import engine.soundEngine.SoundManager;
import java.awt.event.*;
import java.lang.reflect.*;
import java.nio.file.*;
import sun.misc.Unsafe;

/** Fresh Escape taps work before the activation delay; held keys never back out twice. */
public final class EscapeResponseChecks {
    private static int checks;
    private static DisplaySizeChecks.FakeFrame frame;
    private static Field field(Class<?> c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}
    private static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);checks++;}
    private static void key(boolean down){
        KeyEvent e=new KeyEvent(frame.panel,down?KeyEvent.KEY_PRESSED:KeyEvent.KEY_RELEASED,0,0,KeyEvent.VK_ESCAPE,KeyEvent.CHAR_UNDEFINED);
        if(down)Core.getInputManager().keyPressed(e);else Core.getInputManager().keyReleased(e);
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
    private static Screen active(int i)throws Exception{Screen s=page(i);s.initialize();s.isRunning=true;s.update();return s;}
    private static void settlement(Screen s,int before){
        if(s instanceof CashOutScreen){
            check(((CashOutScreen)s).didCashOut(),"Escape retains the original cash-out meaning");
            check(DiamondManager.getInstance().getDiamonds()==before+7,"cash-out banks exactly once");
        }
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("game.displaySettingsDir",Files.createTempDirectory("kfc-escape-").toString());
        Core.getLogger().setLevel(java.util.logging.Level.OFF);SoundManager.getInstance().setMuted(true);
        Unsafe unsafe=(Unsafe)field(Unsafe.class,"theUnsafe").get(null);
        frame=(DisplaySizeChecks.FakeFrame)unsafe.allocateInstance(DisplaySizeChecks.FakeFrame.class);
        frame.panel=new GameViewport(434,497);Core.getDrawManager().setFrame(frame);field(EndlessMode.class,"boundFrame").set(null,frame);
        for(WindowSettings.Mode mode:WindowSettings.Mode.values()){
            frame.applyWindowMode(mode);
            for(int i=0;i<9;i++){
                Core.getInputManager().resetInput();Screen s=active(i);
                check(!s.inputDelay.checkFinished(),"initial activation guard is still running");
                int before=DiamondManager.getInstance().getDiamonds();
                key(true);key(false);s.update();
                check(!s.isRunning,"fast tap must return in the current update / "+s.getClass().getSimpleName()+" / "+mode);
                settlement(s,before);
                // A held Escape inherited from another page cannot close this page.
                Core.getInputManager().resetInput();key(true);s=active(i);
                field(Cooldown.class,"time").setLong(s.inputDelay,0);
                before=DiamondManager.getInstance().getDiamonds();
                for(int repeat=0;repeat<3;repeat++){key(true);s.update();check(s.isRunning,"held key and OS repeat cannot back out again");}
                check(DiamondManager.getInstance().getDiamonds()==before,"held Escape never settles a run");
                key(false);key(true);key(false);s.update();
                check(!s.isRunning,"fresh tap works immediately after held key is released");settlement(s,before);
            }
        }
        System.out.println("PASS: "+checks+" Escape tap, activation-guard, held-key and settlement checks across 9 pages / 4 sizes");
    }
}
