package screen;

import engine.*;
import engine.soundEngine.SoundManager;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.Random;
import sun.misc.Unsafe;

/** Real menu/game updates, short taps, OS repeat, dialog cancellation and seeded gameplay soak. */
public final class PlayInputChecks {
    private static int checks,failures;
    private static DisplaySizeChecks.FakeFrame frame;
    private static Object controls;
    private static Field field(Class<?> c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}
    private static void check(boolean ok,String reason){checks++;if(!ok){failures++;System.out.println("FAIL: "+reason);}}
    private static void key(int code,boolean down){
        KeyEvent e=new KeyEvent(frame.panel,down?KeyEvent.KEY_PRESSED:KeyEvent.KEY_RELEASED,0,0,code,KeyEvent.CHAR_UNDEFINED);
        if(down){Core.getInputManager().keyPressed(e);((KeyListener)controls).keyPressed(e);}
        else{Core.getInputManager().keyReleased(e);((KeyListener)controls).keyReleased(e);}
    }
    private static void tap(int code){key(code,true);key(code,false);}
    private static void reset(){Core.getInputManager().resetInput();((ResettableInput)controls).resetInput();}
    private static GameScreen game(int level)throws Exception{
        reset();GameSettings settings=(GameSettings)field(Core.class,"SETTINGS_LEVEL_"+level).get(null);
        GameScreen g=new GameScreen(new GameState(level,0,3,0,0,0),settings,false,434,497,60);g.initialize();g.isRunning=true;
        field(Cooldown.class,"time").setLong(g.inputDelay,0);return g;
    }
    private static boolean paused(GameScreen g)throws Exception{return field(GameScreen.class,"paused").getBoolean(g);}
    private static TitleScreen title()throws Exception{
        reset();TitleScreen t=new TitleScreen(434,497,60);t.isRunning=true;
        Method draw=TitleScreen.class.getDeclaredMethod("draw");draw.setAccessible(true);draw.invoke(t);return t;
    }
    private static boolean confirmation(TitleScreen t)throws Exception{return field(TitleScreen.class,"showingExitConfirm").getBoolean(t);}
    public static void main(String[] args)throws Exception{
        System.setProperty("game.displaySettingsDir",Files.createTempDirectory("kfc-play-input-").toString());
        Core.getLogger().setLevel(java.util.logging.Level.OFF);SoundManager.getInstance().setMuted(true);
        Unsafe unsafe=(Unsafe)field(Unsafe.class,"theUnsafe").get(null);
        frame=(DisplaySizeChecks.FakeFrame)unsafe.allocateInstance(DisplaySizeChecks.FakeFrame.class);
        frame.panel=new GameViewport(434,497);Core.getDrawManager().setFrame(frame);field(EndlessMode.class,"boundFrame").set(null,frame);
        controls=field(EndlessMode.class,"controls").get(null);
        for(WindowSettings.Mode mode:WindowSettings.Mode.values()){
            frame.applyWindowMode(mode);
            TitleScreen t=title();tap(KeyEvent.VK_ESCAPE);t.update();check(confirmation(t),"fresh lobby Esc works during activation guard / "+mode);
            t=title();field(Cooldown.class,"time").setLong(t.inputDelay,0);tap(KeyEvent.VK_ESCAPE);t.update();
            check(confirmation(t),"short lobby Esc is never lost / "+mode);
            if(confirmation(t)){
                check(!field(TitleScreen.class,"exitConfirmYes").getBoolean(t),"lobby exit still defaults to No");
                tap(KeyEvent.VK_ESCAPE);t.update();check(!confirmation(t)&&t.isRunning,"Esc cancels without closing game");
            }
            t=title();field(Cooldown.class,"time").setLong(t.inputDelay,0);tap(KeyEvent.VK_E);t.update();
            check(field(EndlessMode.class,"gateOpen").getBoolean(null),"Endless entry opens normally");
            key(KeyEvent.VK_ESCAPE,true);t.update();
            check(!field(EndlessMode.class,"gateOpen").getBoolean(null)&&!confirmation(t),"Esc closes only the entry overlay");
            for(int i=0;i<3;i++){key(KeyEvent.VK_ESCAPE,true);t.update();check(!confirmation(t),"held Esc cannot cascade into parent exit");}
            key(KeyEvent.VK_ESCAPE,false);tap(KeyEvent.VK_ESCAPE);t.update();check(confirmation(t),"fresh Esc can open parent exit afterwards");
            reset();
            SettingsScreen settingsPage=new SettingsScreen(434,497,60);settingsPage.isRunning=true;
            field(Cooldown.class,"time").setLong(settingsPage.inputDelay,0);
            Method testButton=SettingsScreen.class.getDeclaredMethod("testButton");testButton.setAccessible(true);
            Rectangle test=(Rectangle)testButton.invoke(settingsPage);
            Core.getInputManager().mousePressed(new MouseEvent(frame.panel,MouseEvent.MOUSE_PRESSED,0,0,test.x+50,test.y+12,1,false,MouseEvent.BUTTON1));settingsPage.update();
            Core.getInputManager().mouseReleased(new MouseEvent(frame.panel,MouseEvent.MOUSE_RELEASED,0,0,test.x+50,test.y+12,1,false,MouseEvent.BUTTON1));settingsPage.update();
            check(field(SettingsScreen.class,"testDialog").getBoolean(settingsPage),"latest Settings test dialog opens");
            tap(KeyEvent.VK_ESCAPE);settingsPage.update();
            check(!field(SettingsScreen.class,"testDialog").getBoolean(settingsPage)&&settingsPage.isRunning,"quick Esc closes only tester dialog");
            key(KeyEvent.VK_ESCAPE,true);settingsPage.update();
            check(!settingsPage.isRunning,"next fresh Esc returns from Settings");
            key(KeyEvent.VK_ESCAPE,false);
            for(int level:new int[]{1,5,10}){
                GameScreen g=game(level);tap(KeyEvent.VK_ESCAPE);g.update();check(paused(g),"short gameplay Esc pauses stage "+level+" / "+mode);
                g=game(level);key(KeyEvent.VK_P,true);g.update();check(paused(g),"P first press pauses");
                for(int i=0;i<3;i++){
                    field(Cooldown.class,"time").setLong(field(GameScreen.class,"pauseKeyCooldown").get(g),0);
                    key(KeyEvent.VK_P,true);g.update();check(paused(g),"held P / OS repeat cannot resume stage "+level);
                }
                key(KeyEvent.VK_P,false);tap(KeyEvent.VK_P);g.update();check(!paused(g),"fresh P resumes without waiting for cooldown");
                g=game(level);key(KeyEvent.VK_SPACE,true);
                Rectangle b=g.navigationBounds();Core.getInputManager().mousePressed(new MouseEvent(frame.panel,MouseEvent.MOUSE_PRESSED,0,0,b.x+5,b.y+5,1,false,MouseEvent.BUTTON1));g.update();
                Core.getInputManager().mouseReleased(new MouseEvent(frame.panel,MouseEvent.MOUSE_RELEASED,0,0,b.x+5,b.y+5,1,false,MouseEvent.BUTTON1));
                PauseConfirm confirm=(PauseConfirm)field(GameScreen.class,"pauseConfirm").get(g);
                check(confirm.isOpen()&&!confirm.isYesSelected(),"Quit starts safely on No");
                tap(KeyEvent.VK_ESCAPE);g.update();check(!confirm.isOpen()&&paused(g),"Esc cancels Quit even while fire remains held");
                key(KeyEvent.VK_SPACE,false);tap(KeyEvent.VK_ESCAPE);g.update();check(!paused(g),"next fresh Esc resumes / "+mode);
            }
            reset();Constructor<EndlessMode> ctor=EndlessMode.class.getDeclaredConstructor(int.class,int.class,boolean.class);ctor.setAccessible(true);
            EndlessMode endless=ctor.newInstance(434,497,true);endless.initialize();endless.isRunning=true;field(Cooldown.class,"time").setLong(endless.inputDelay,0);
            EndlessMode.Run endlessRun=(EndlessMode.Run)field(EndlessMode.class,"run").get(endless);
            field(EndlessMode.Run.class,"phase").set(endlessRun,EndlessMode.Run.Phase.PLAYING);
            key(KeyEvent.VK_SPACE,true);Rectangle quit=endless.navigationBounds();
            Core.getInputManager().mousePressed(new MouseEvent(frame.panel,MouseEvent.MOUSE_PRESSED,0,0,quit.x+5,quit.y+5,1,false,MouseEvent.BUTTON1));endless.update();
            Core.getInputManager().mouseReleased(new MouseEvent(frame.panel,MouseEvent.MOUSE_RELEASED,0,0,quit.x+5,quit.y+5,1,false,MouseEvent.BUTTON1));
            PauseConfirm endlessConfirm=(PauseConfirm)field(EndlessMode.class,"pauseConfirm").get(endless);
            check(endlessConfirm.isOpen()&&!endlessConfirm.isYesSelected(),"Endless Quit defaults to No");
            tap(KeyEvent.VK_ESCAPE);endless.update();check(!endlessConfirm.isOpen()&&endlessRun.getPhase()==EndlessMode.Run.Phase.PAUSED,"Endless Esc cancels even while fire is held");
            key(KeyEvent.VK_SPACE,false);tap(KeyEvent.VK_ESCAPE);endless.update();check(endlessRun.getPhase()==EndlessMode.Run.Phase.PLAYING,"fresh Endless Esc resumes afterwards");
            endlessRun.finish("TEST COMPLETE");
        }
        if(args.length<2||!args[1].equals("--input-only")){
            for(int seed:new int[]{7,42,20261123}){
                Random random=new Random(seed);EndlessMode.Run run=new EndlessMode.Run(434,497,random);
                for(int tick=0;tick<60000;tick++){
                    if(run.getPhase()==EndlessMode.Run.Phase.OVER)run=new EndlessMode.Run(434,497,random);
                    if(run.getPhase()==EndlessMode.Run.Phase.REWARD)run.chooseReward(random.nextInt(3));
                    if(tick%997==0&&run.getPhase()!=EndlessMode.Run.Phase.OVER){
                        run.togglePause();long score=run.getScore(),seconds=run.getSeconds();run.update(.1,1,true);
                        check(run.getScore()==score&&run.getSeconds()==seconds,"pause freezes scoring and survival");run.togglePause();
                    }
                    run.update(1.0/60,(tick/75)%3-1,true);
                    if(run.getEnemies().size()>40||run.getShots().size()>250)throw new AssertionError("unbounded actor growth");
                    if(!Double.isFinite(run.getPlayer().x)||!Double.isFinite(run.getPlayer().y))throw new AssertionError("invalid player position");
                }
                check(true,"60000-frame randomized simulation / seed "+seed);
            }
        }
        if(failures>0)throw new AssertionError(failures+" of "+checks+" checks failed");
        System.out.println("PASS: "+checks+" input-flow and gameplay checks; 3 seeded 60000-frame simulations when enabled");
    }
}
