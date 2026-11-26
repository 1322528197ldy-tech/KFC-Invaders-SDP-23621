package screen;

import engine.*;
import engine.soundEngine.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import sun.misc.Unsafe;

/** Same-frame hover feedback and independence from a deliberately stalled audio backend. */
public final class MenuResponseChecks {
    private static int checks;
    private static DisplaySizeChecks.FakeFrame frame;
    private static Object controls;
    private static Field field(Class<?> c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}
    private static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);checks++;}
    private static void move(int y)throws Exception{
        Rectangle b=frame.panel.transform().bounds();
        int px=b.x+b.width/2,py=b.y+(int)((y+.5)*b.height/497);
        SwingUtilities.invokeAndWait(()->frame.panel.dispatchEvent(new MouseEvent(frame.panel,MouseEvent.MOUSE_MOVED,0,0,px,py,0,false,MouseEvent.NOBUTTON)));
    }
    private static void draw(TitleScreen menu)throws Exception{
        Method m=TitleScreen.class.getDeclaredMethod("draw");m.setAccessible(true);m.invoke(menu);
    }
    private static int baseline(TitleScreen menu,int index)throws Exception{
        Method m=EndlessMode.class.getDeclaredMethod("menuBaseline",Screen.class,int.class);m.setAccessible(true);return (Integer)m.invoke(null,menu,index);
    }
    private static int index()throws Exception{return field(EndlessMode.class,"menuIndex").getInt(null);}
    private static int[] pixels(){return ((DataBufferInt)frame.last.getRaster().getDataBuffer()).getData().clone();}
    private static final class SlowAudio implements AudioBackend {
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        final AtomicInteger calls=new AtomicInteger();
        public void preload(Sound s){} public void playBgm(Sound s,float v){}
        public void stopBgm(){} public boolean isBgmPlaying(){return true;}
        public void setBgmVolume(float v){} public void dispose(){}
        public void playSfx(Sound s,float v){calls.incrementAndGet();entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("game.displaySettingsDir",Files.createTempDirectory("kfc-hover-").toString());
        Core.getLogger().setLevel(java.util.logging.Level.OFF);SoundManager manager=SoundManager.getInstance();manager.setMuted(true);
        Unsafe unsafe=(Unsafe)field(Unsafe.class,"theUnsafe").get(null);
        frame=(DisplaySizeChecks.FakeFrame)unsafe.allocateInstance(DisplaySizeChecks.FakeFrame.class);
        frame.panel=new GameViewport(434,497);Core.getDrawManager().setFrame(frame);
        field(EndlessMode.class,"boundFrame").set(null,frame);controls=field(EndlessMode.class,"controls").get(null);
        frame.panel.addMouseListener(Core.getInputManager());frame.panel.addMouseMotionListener(Core.getInputManager());
        frame.panel.addMouseListener((MouseListener)controls);frame.panel.addMouseMotionListener((MouseMotionListener)controls);
        TitleScreen last=null;
        for(WindowSettings.Mode mode:WindowSettings.Mode.values()){
            frame.applyWindowMode(mode);Core.getInputManager().resetInput();((ResettableInput)controls).resetInput();
            TitleScreen menu=new TitleScreen(434,497,60);menu.isRunning=true;draw(menu);
            field(Cooldown.class,"time").setLong(menu.inputDelay,0);
            // Consecutive pointer events need neither sleeps nor keyboard repeat cooldown expiry.
            for(int target:new int[]{2,5,3,1,6,0,4,2}){
                move(baseline(menu,target)-4);menu.update();
                check(index()==target,"hover selects correct row immediately / "+mode);
                int[] presented=pixels();draw(menu);
                check(Arrays.equals(presented,pixels()),"current frame must already show the new selection / "+mode);
                check(menu.isRunning,"hover cannot activate a row");
            }
            TitleScreen returning=new TitleScreen(434,497,60);returning.isRunning=true;draw(returning);
            int target=index()==3?4:3;move(baseline(returning,target)-4);returning.update();
            check(index()==target,"hover works during the activation guard / "+mode);
            check(returning.isRunning,"activation guard still prevents navigation");
            last=returning;
        }
        if(args.length>1&&args[1].equals("--hover-only")){System.out.println("PASS: "+checks+" same-frame hover checks");return;}
        // Keep constructors/music outside the blocked-audio interval: only pointer updates are timed.
        SlowAudio slow=new SlowAudio();Field backend=field(SoundManager.class,"backend");Object original=backend.get(manager);
        backend.set(manager,slow);manager.setMuted(false);field(MenuSounds.class,"lastPlayed").setLong(null,0);
        ExecutorService caller=Executors.newSingleThreadExecutor();
        try{
            Future<?> request=caller.submit(MenuSounds::playMove);
            check(slow.entered.await(2,TimeUnit.SECONDS),"audio request reaches backend");
            request.get(500,TimeUnit.MILLISECONDS);
            check(true,"move-sound request returns while audio is stalled");
            field(Cooldown.class,"time").setLong(last.inputDelay,0);
            for(int target:new int[]{4,2,5,3}){
                move(baseline(last,target)-4);last.update();check(index()==target,"hover stays responsive with blocked audio");
                MenuSounds.playMove();
            }
            check(slow.calls.get()==1,"slow audio cannot accumulate stale hover sounds");
        }finally{slow.release.countDown();caller.shutdownNow();}
        // The backend lock ensures the queued call has returned before restoring it.
        manager.setMuted(true);backend.set(manager,original);
        System.out.println("PASS: "+checks+" same-frame, activation-guard and stalled-audio hover checks");
    }
}
