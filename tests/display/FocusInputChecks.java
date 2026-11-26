package engine;

import java.awt.event.*;
import java.lang.reflect.*;
import java.nio.file.*;
import javax.swing.SwingUtilities;
import screen.TitleScreen;
import screen.EndlessMode;
import engine.soundEngine.SoundManager;

/** Dispatches events only to this test's own native window. */
public final class FocusInputChecks {
    private static int checks;
    private static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);checks++;}
    private static Object call(Object target,String method,Class<?>[] types,Object...args)throws Exception{
        Method m=target.getClass().getDeclaredMethod(method,types);m.setAccessible(true);return m.invoke(target,args);
    }
    private static final Class<?>[] NONE=new Class<?>[0],INT={int.class};
    public static void main(String[] args)throws Exception{
        System.setProperty("game.displaySettingsDir",Files.createTempDirectory("kfc-focus-prefs-").toString());
        Core.getLogger().setLevel(java.util.logging.Level.OFF);SoundManager.getInstance().setMuted(true);
        Frame frame=new Frame(448,520);Core.getDrawManager().setFrame(frame);
        try{
            TitleScreen menu=new TitleScreen(frame.getLogicalWidth(),frame.getLogicalHeight(),60);
            Method draw=TitleScreen.class.getDeclaredMethod("draw");draw.setAccessible(true);draw.invoke(menu);
            Field f=EndlessMode.class.getDeclaredField("controls");f.setAccessible(true);Object controls=f.get(null);
            int[] held={KeyEvent.VK_D,KeyEvent.VK_A,KeyEvent.VK_SPACE,KeyEvent.VK_P,KeyEvent.VK_ENTER,KeyEvent.VK_1};
            for(WindowSettings.Mode mode:WindowSettings.Mode.values()){
                frame.applyWindowMode(mode);
                for(int event:new int[]{WindowEvent.WINDOW_LOST_FOCUS,WindowEvent.WINDOW_ICONIFIED}){
                    SwingUtilities.invokeAndWait(()->{
                        for(KeyListener listener:frame.getKeyListeners())for(int key:held)
                            listener.keyPressed(new KeyEvent(frame,KeyEvent.KEY_PRESSED,0,0,key,KeyEvent.CHAR_UNDEFINED));
                        MouseEvent press=new MouseEvent(frame,MouseEvent.MOUSE_PRESSED,0,0,100,100,1,false,MouseEvent.BUTTON1);
                        Core.getInputManager().mousePressed(press);((MouseListener)controls).mousePressed(press);
                        WindowEvent lost=new WindowEvent(frame,event);
                        // The native focus manager suppresses repeated synthetic lost-focus events.
                        // Exercise the window's registered callbacks for each independent scenario.
                        if(event==WindowEvent.WINDOW_LOST_FOCUS)
                            for(WindowFocusListener l:frame.getWindowFocusListeners())l.windowLostFocus(lost);
                        else for(WindowListener l:frame.getWindowListeners())l.windowIconified(lost);
                    });
                    for(int key:held){
                        check(!Core.getInputManager().isKeyDown(key),"core held key cleared");
                        check(!(Boolean)call(controls,"down",INT,key),"endless held key cleared");
                        check(!(Boolean)call(controls,"take",INT,key),"queued menu action cleared");
                    }
                    check(!Core.getInputManager().isLeftMousePressed(),"held mouse cleared");
                    check(call(controls,"takeClick",NONE)==null,"queued click cleared");
                    check(frame.getLogicalWidth()==448&&frame.getLogicalHeight()==520,"logical playfield unchanged");
                }
            }
            Core.getInputManager().beginCodeEntry();
            SwingUtilities.invokeAndWait(()->{
                InputManager listener=Core.getInputManager();
                listener.keyPressed(new KeyEvent(frame,KeyEvent.KEY_PRESSED,0,0,KeyEvent.VK_0,'0'));
                for(WindowFocusListener l:frame.getWindowFocusListeners())l.windowLostFocus(new WindowEvent(frame,WindowEvent.WINDOW_LOST_FOCUS));
                listener.keyPressed(new KeyEvent(frame,KeyEvent.KEY_PRESSED,0,0,KeyEvent.VK_1,'1'));
                listener.keyReleased(new KeyEvent(frame,KeyEvent.KEY_RELEASED,0,0,KeyEvent.VK_1,'1'));
                listener.keyPressed(new KeyEvent(frame,KeyEvent.KEY_PRESSED,0,0,KeyEvent.VK_D,'d'));
            });
            check("01".equals(Core.getInputManager().enteredCode()),"Settings code entry survives focus reset");
            Core.getInputManager().endCodeEntry();
            check(Core.getInputManager().isKeyDown(KeyEvent.VK_D),"fresh key works after reset");
            System.out.println("PASS: "+checks+" focus/minimize input checks");
        }finally{SwingUtilities.invokeAndWait(frame::dispose);}
        System.exit(0);
    }
}
