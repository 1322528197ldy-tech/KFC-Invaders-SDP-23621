package engine;

import endless.GameplaySession;
import endless.ModeRecords;
import entity.Coin;
import screen.EndlessMode;
import screen.Screen;
import screen.SettingsScreen;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.List;
import item.ItemSystem;
import entity.Ship.ShipType;
import shop.ShopCatalog;
import shop.Skin;
import screen.TitleScreen;
import java.awt.Canvas;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import sun.misc.Unsafe;

/** Runs from a JAR in a source-free working directory, using disposable saves. */
public final class EntryGateChecks {
    @FunctionalInterface
    private interface FrameStep { void run() throws Exception; }

    @SuppressWarnings("serial")
    public static final class Offscreen extends Frame {
        private FrameStep afterDraw;
        public Offscreen() { super(432,497); }
        @Override public Graphics getGraphics() { return new BufferedImage(432,497,BufferedImage.TYPE_INT_RGB).getGraphics(); }
        @Override public Insets getInsets() { return new Insets(0,0,0,0); }
        @Override public void present(BufferedImage image) {
            if (afterDraw == null)
                return;
            try {
                afterDraw.run();
            } catch (Exception e) {
                throw new IllegalStateException("Could not drive the packaged screen", e);
            }
        }
    }
    private static int checks;
    private static final Canvas INPUT=new Canvas();
    private static Object controls;
    private static TitleScreen menu;
    private static Field field(Class<?> type,String name)throws Exception {
        Field f=type.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static Object get(Class<?> type,Object target,String name)throws Exception { return field(type,name).get(target); }
    private static void set(Class<?> type,Object target,String name,Object value)throws Exception { field(type,name).set(target,value); }
    private static void check(boolean value,String message) { checks++;if(!value)throw new AssertionError(message); }
    private static void tap(int code) {
        KeyListener listener=(KeyListener)controls;
        listener.keyPressed(new KeyEvent(INPUT,KeyEvent.KEY_PRESSED,0,0,code,KeyEvent.CHAR_UNDEFINED));
        listener.keyReleased(new KeyEvent(INPUT,KeyEvent.KEY_RELEASED,0,0,code,KeyEvent.CHAR_UNDEFINED));
    }
    private static void click(int x,int y) {
        ((MouseListener)controls).mousePressed(new MouseEvent(INPUT,MouseEvent.MOUSE_PRESSED,0,0,x+16,y+55,1,false,MouseEvent.BUTTON1));
    }
    private static void update()throws Exception { EndlessMode.updateEntry(menu); }
    private static void updateSettings(SettingsScreen settings) throws Exception {
        Method update = SettingsScreen.class.getDeclaredMethod("update");
        update.setAccessible(true);
        update.invoke(settings);
    }
    private static void settingsKey(SettingsScreen settings, int code) throws Exception {
        settingsKey(code, true);
        updateSettings(settings);
        settingsKey(code, false);
        updateSettings(settings);
    }
    private static void settingsKey(int code, boolean pressed) {
        InputManager input = Core.getInputManager();
        KeyEvent event = new KeyEvent(INPUT, pressed ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                0, 0, code, KeyEvent.CHAR_UNDEFINED);
        if (pressed)
            input.keyPressed(event);
        else
            input.keyReleased(event);
    }
    private static Rectangle settingsButton(SettingsScreen settings, String method, Object... arguments) throws Exception {
        Method bounds = arguments.length == 0 ? SettingsScreen.class.getDeclaredMethod(method)
                : SettingsScreen.class.getDeclaredMethod(method, boolean.class);
        bounds.setAccessible(true);
        return (Rectangle) bounds.invoke(settings, arguments);
    }
    private static void settingsMouse(Rectangle button, boolean pressed) {
        int x = (int) button.getCenterX();
        int y = (int) button.getCenterY();
        InputManager input = Core.getInputManager();
        MouseEvent event = new MouseEvent(INPUT, pressed ? MouseEvent.MOUSE_PRESSED : MouseEvent.MOUSE_RELEASED,
                0, 0, x, y, 1, false, MouseEvent.BUTTON1);
        if (pressed)
            input.mousePressed(event);
        else
            input.mouseReleased(event);
    }
    private static void settingsClick(SettingsScreen settings, String method, Object... arguments) throws Exception {
        Rectangle button = settingsButton(settings, method, arguments);
        settingsMouse(button, true);
        updateSettings(settings);
        settingsMouse(button, false);
        updateSettings(settings);
    }

    private static Screen activeScreen(Offscreen frame) throws Exception {
        return (Screen) get(Frame.class, frame, "currentScreen");
    }

    private static void play(Offscreen frame, FrameStep... steps) throws Exception {
        // Feed input after each real draw on the game thread so every edge reaches the next update.
        var remaining = Arrays.asList(steps).iterator();
        frame.afterDraw = () -> {
            if (!remaining.hasNext())
                throw new AssertionError("Old Endless run drew another frame after profile switch");
            remaining.next().run();
        };
        try {
            tap(KeyEvent.VK_ENTER);
            update();
            check(!remaining.hasNext(), "Endless run completed the scripted screen flow");
        } finally {
            frame.afterDraw = null;
        }
    }

    private static void verifyProfile(EndlessMode screen, boolean testing, Path records, long bestScore)
            throws Exception {
        GameplaySession session = (GameplaySession) get(EndlessMode.class, screen, "systems");
        PurchaseManager loadout = PurchaseManager.getInstance();
        check((Boolean) get(EndlessMode.class, screen, "testing") == testing && session.isPersistent() != testing,
                "Endless starts with the selected profile: " + records);
        check(session.getShip().getType() == loadout.getSelectedShip() && session.getSkin() == loadout.getSelectedSkin(),
                "Endless starts with the selected loadout: " + records);
        ModeRecords activeRecords = (ModeRecords) get(EndlessMode.class, screen, "records");
        check(records.toAbsolutePath().equals(get(ModeRecords.class, activeRecords, "path"))
                && activeRecords.getScore() == bestScore, "Endless loads the selected profile's record file: " + records);
    }

    private static void switchFromPause(Offscreen frame, boolean enable, Path records, long bestScore,
            FrameStep activity) throws Exception {
        tap(KeyEvent.VK_E);
        update();
        play(frame, () -> {
            verifyProfile((EndlessMode) activeScreen(frame), !enable, records, bestScore);
            activity.run();
            tap(KeyEvent.VK_P);
        }, () -> {
            var run = (EndlessMode.Run) get(EndlessMode.class, activeScreen(frame), "run");
            check(run.getPhase() == EndlessMode.Run.Phase.PAUSED, "The live Endless run is paused");
            tap(KeyEvent.VK_O);
        }, () -> {
            var settings = (SettingsScreen) activeScreen(frame);
            check((Boolean) get(SettingsScreen.class, settings, "fromEndless"), "Pause opens the Endless Settings screen");
            field(Cooldown.class, "time").setLong(get(Screen.class, settings, "inputDelay"), 0);
            settingsMouse(settingsButton(settings, "testButton"), true);
        }, () -> {
            var settings = (SettingsScreen) activeScreen(frame);
            check((Boolean) get(SettingsScreen.class, settings, "testDialog"), "The test dialog opens during pause");
            settingsMouse(settingsButton(settings, "testButton"), false);
            if (enable) {
                for (int i = 0; i < 3; i++) {
                    settingsKey(KeyEvent.VK_0, true);
                    settingsKey(KeyEvent.VK_0, false);
                }
            }
            settingsKey(KeyEvent.VK_ENTER, true);
        }, () -> {
            settingsKey(KeyEvent.VK_ENTER, false);
            check(TesterMode.isEnabled() == enable, "Test mode switches inside paused Settings");
            settingsKey(KeyEvent.VK_ESCAPE, true);
        }, () -> {
            settingsKey(KeyEvent.VK_ESCAPE, false);
            check(!(Boolean) get(Screen.class, activeScreen(frame), "isRunning"), "Escape closes paused Settings");
        });
    }

    @SuppressWarnings("unchecked")
    private static void earnTestRewards(EndlessMode screen) throws Exception {
        var session = (GameplaySession) get(EndlessMode.class, screen, "systems");
        ((List<Coin>) get(GameplaySession.class, session, "coins")).add(new Coin(100, 100, 5));
        session.waveCleared();
        session.finish(true, 1, 1);
        check(session.getWalletCoins() == 5 && session.getWalletDiamonds() > 0, "Test run earns local coins and diamonds");
        var records = (ModeRecords) get(EndlessMode.class, screen, "records");
        check(records.save(99, 12, 3), "Test run writes its own record");
    }

    private static void render(Path directory,String name)throws Exception {
        Core.getDrawManager().initDrawing(menu);EndlessMode.drawEntry(menu);
        ImageIO.write((BufferedImage)get(DrawManager.class,null,"backBuffer"),"png",directory.resolve(name+".png").toFile());
    }
    public static void main(String[] args)throws Exception {
        check(!Files.exists(Path.of("src")),"Entry checks must run without a source directory");
        Path location=Path.of(Core.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        check(location.toString().endsWith(".jar"),"Check the actual packaged classpath");
        check(location.getParent().getFileName().toString().startsWith("connected-check"),"Packaged saves are isolated");
        Unsafe unsafe=(Unsafe)get(Unsafe.class,null,"theUnsafe");
        Offscreen frame=(Offscreen)unsafe.allocateInstance(Offscreen.class);
        Core.getDrawManager().setFrame(frame);
        menu=new TitleScreen(432,497,60);
        // No native window/listener registration is needed for injected offscreen input.
        set(EndlessMode.class,null,"boundFrame",frame);
        set(EndlessMode.class,null,"menuScreen",menu);
        controls=get(EndlessMode.class,null,"controls");
        Core.getDrawManager().initDrawing(menu);
        Method hook=TitleScreen.class.getDeclaredMethod("optionalEndless",String.class);hook.setAccessible(true);
        check(Boolean.TRUE.equals(hook.invoke(menu,"drawEntry")),"Compiled Endless entry works without source files");
        try(URLClassLoader absent=new URLClassLoader(new URL[]{location.toUri().toURL()},null){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                if(name.startsWith("screen.EndlessMode"))throw new ClassNotFoundException(name);
                return super.loadClass(name,resolve);
            }
        }) {
            Class<?> title=absent.loadClass("screen.TitleScreen");
            Object base=title.getConstructor(int.class,int.class,int.class).newInstance(432,497,60);
            Method optional=title.getDeclaredMethod("optionalEndless",String.class);optional.setAccessible(true);
            check(Boolean.FALSE.equals(optional.invoke(base,"drawEntry")),"Base menu remains usable if the add-on class is absent");
        }
        check(!EndlessMode.unlocked(),"Fresh saves keep normal Endless locked");
        tap(KeyEvent.VK_E);update();
        check((Boolean)get(EndlessMode.class,null,"gateOpen"),"Shortcut opens the locked panel");
        tap(KeyEvent.VK_ENTER);update();
        check(get(Frame.class,frame,"currentScreen")==null,"Enter cannot bypass the normal unlock");
        Path screenshots=Path.of(args[0]);Files.createDirectories(screenshots);
        render(screenshots,"entry-locked");
        click(360,337);update();
        check((Boolean)get(EndlessMode.class,null,"gateOpen")
                && get(Frame.class,frame,"currentScreen")==null && !TesterMode.isEnabled(),
                "The former lock location has no test action");
        tap(KeyEvent.VK_ESCAPE);update();

        PurchaseManager normal = PurchaseManager.getInstance();
        var normalShip = normal.getSelectedShip();
        var normalSkin = normal.getSelectedSkin();
        int normalCoins = CurrencyManager.getInstance().getCoins();
        int normalDiamonds = DiamondManager.getInstance().getDiamonds();
        var normalScores = Core.getFileManager().loadHighScores();
        var normalAchievementManager = Core.getAchievementManager();
        byte[] ownedSave = Files.readAllBytes(location.getParent().resolve("shop-owned"));

        SettingsScreen settings = new SettingsScreen(432,497,60);
        field(Cooldown.class,"time").setLong(get(Screen.class,settings,"inputDelay"),0);
        settingsClick(settings,"testButton");
        check((Boolean)get(SettingsScreen.class,settings,"testDialog"),"Settings lock opens the test mode prompt");
        settingsKey(settings,KeyEvent.VK_ENTER);
        check(!TesterMode.isEnabled(),"Empty code is rejected");
        settingsKey(settings,KeyEvent.VK_1);
        settingsKey(settings,KeyEvent.VK_2);
        settingsKey(settings,KeyEvent.VK_ENTER);
        check((Boolean)get(SettingsScreen.class,settings,"wrongCode")
                && Core.getInputManager().enteredCode().isEmpty(),"Wrong code clears the input");
        settingsKey(settings,KeyEvent.VK_0);
        settingsKey(settings,KeyEvent.VK_1);
        settingsKey(settings,KeyEvent.VK_BACK_SPACE);
        settingsKey(settings,KeyEvent.VK_NUMPAD0);
        check(Core.getInputManager().enteredCode().equals("00"),"Code field accepts numpad and backspace");
        settingsKey(settings,KeyEvent.VK_DELETE);
        check(Core.getInputManager().enteredCode().isEmpty(),"Delete clears the code");
        settingsKey(settings,KeyEvent.VK_ESCAPE);
        check(!(Boolean)get(SettingsScreen.class,settings,"testDialog") && !TesterMode.isEnabled(),
                "Escape closes the prompt without enabling testing");

        settingsClick(settings,"testButton");
        for (int i=0;i<3;i++) settingsKey(settings,KeyEvent.VK_0);
        settingsClick(settings,"dialogButton",true);
        check(TesterMode.isEnabled() && EndlessMode.unlocked(),"Correct code unlocks Endless from Settings");
        PurchaseManager tester = PurchaseManager.getInstance();
        check(tester != normal && ShopCatalog.getAll().stream().allMatch(tester::isOwned),
                "All catalog entries are unlocked in a separate profile");
        check(tester.selectShip(ShipType.FAST_MOVE) && tester.selectSkin(Skin.GILDED),
                "Test ship and skin can be equipped");
        check(tester.setUpgradeOn("magnet",true) && ItemSystem.equippedItem().itemId.equals("magnet"),
                "Test equipment can be enabled and equipped");
        check(Core.getAchievementManager().getAchievements().stream().allMatch(Achievement::isUnlocked),
                "Achievements are unlocked in the test profile");
        CurrencyManager.getInstance().addCoins(123);
        DiamondManager.getInstance().addDiamonds(45);
        Core.getFileManager().saveHighScores(List.of(new Score("TST",99999)));

        tap(KeyEvent.VK_E);update();
        check((Boolean)get(EndlessMode.class,null,"gateAvailable"),"Unlocked settings profile enables Start Run");
        render(screenshots,"entry-unlocked-for-testing");
        Path testRecords = Path.of("endless-test-records.properties");
        play(frame, () -> {
            var active = (EndlessMode) activeScreen(frame);
            verifyProfile(active, true, testRecords, 0);
            set(Screen.class, active, "isRunning", false);
        });
        settingsClick(settings,"testButton");
        settingsClick(settings,"dialogButton",false);
        check(TesterMode.isEnabled(),"Back leaves test mode enabled");
        settingsClick(settings,"testButton");
        settingsClick(settings,"dialogButton",true);
        check(!TesterMode.isEnabled() && !EndlessMode.unlocked(),"Disabling restores the original Endless lock");
        check(PurchaseManager.getInstance()==normal && normal.getSelectedShip()==normalShip
                && normal.getSelectedSkin()==normalSkin,"Disabling restores the regular loadout");
        check(CurrencyManager.getInstance().getCoins()==normalCoins
                && DiamondManager.getInstance().getDiamonds()==normalDiamonds,"Test currency never enters regular balances");
        check(Core.getAchievementManager()==normalAchievementManager
                && Core.getAchievementManager().getAchievements().stream().anyMatch(a->!a.isUnlocked()),
                "Regular achievements retain their original state");
        check(Core.getFileManager().loadHighScores().size()==normalScores.size(),
                "Test records are excluded from regular high scores");
        check(Arrays.equals(ownedSave,Files.readAllBytes(location.getParent().resolve("shop-owned"))),
                "Testing leaves the regular shop file byte-for-byte intact");

        normalAchievementManager.recordLevelCompleted(10);
        var unlockedIds = normalAchievementManager.getAchievements().stream()
                .filter(Achievement::isUnlocked).map(Achievement::getId).toList();
        Path regularRecords = Path.of("endless-records.properties");
        check(EndlessMode.unlocked() && new ModeRecords(regularRecords).save(42, 11, 2),
                "The regular profile has its own unlock and prior record");
        byte[] regularRecordSave = Files.readAllBytes(regularRecords);
        switchFromPause(frame, true, regularRecords, 42, () -> {});
        switchFromPause(frame, false, testRecords, 0, () -> earnTestRewards((EndlessMode) activeScreen(frame)));
        tap(KeyEvent.VK_E);
        update();
        play(frame, () -> {
            var active = (EndlessMode) activeScreen(frame);
            verifyProfile(active, false, regularRecords, 42);
            set(Screen.class, active, "isRunning", false);
        });
        check(new ModeRecords(testRecords).getScore() == 99
                && Arrays.equals(regularRecordSave, Files.readAllBytes(regularRecords)),
                "Test activity leaves the regular record file intact");
        check(CurrencyManager.getInstance().getCoins() == normalCoins
                && DiamondManager.getInstance().getDiamonds() == normalDiamonds,
                "A test run's rewards cannot enter the regular wallet");
        check(normal.getSelectedShip() == normalShip && normal.getSelectedSkin() == normalSkin
                && Arrays.equals(ownedSave, Files.readAllBytes(location.getParent().resolve("shop-owned"))),
                "Switching from a test run leaves regular loadout and ownership intact");
        check(Core.getAchievementManager().getAchievements().stream().filter(Achievement::isUnlocked)
                .map(Achievement::getId).toList().equals(unlockedIds),
                "Test activity leaves the regular achievement unlocks intact");

        tap(KeyEvent.VK_E);update();
        check(get(EndlessMode.class,null,"bossRule")==endless.BossRule.EVERY_FIVE_WAVES,"Five-wave bosses are the default rule");
        tap(KeyEvent.VK_B);update();
        check(get(EndlessMode.class,null,"bossRule")==endless.BossRule.EVERY_90_SECONDS,"B switches to the 90 second rule");
        render(screenshots,"entry-boss-rule-seconds");
        click(300,250);update();
        check(get(EndlessMode.class,null,"bossRule")==endless.BossRule.EVERY_30_KILLS
                && (Boolean)get(EndlessMode.class,null,"gateOpen"),"Clicking the boss box switches to the 30 kill rule without leaving the panel");
        render(screenshots,"entry-boss-rule-kills");
        tap(KeyEvent.VK_B);update();
        check(get(EndlessMode.class,null,"bossRule")==endless.BossRule.EVERY_FIVE_WAVES,"The third switch returns to five waves");
        System.out.println("PASS: "+checks+" packaged entry / Settings test mode checks");
    }
}
