package screen;

import engine.*;
import entity.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import item.ItemAPI;
import item.ItemAPI.*;
import item.ItemManager;
import item.ItemSystem;

/**
 * Removable local add-on: click the menu entry (or press E).
 * Requires the original infinity_void achievement; the bottom-right lock is a
 * tester bypass and writes only endless-test-records.properties.
 * Base score = active whole seconds * 10 + defeated enemies * 100; items can add bonuses.
 * Boss trigger = 90 active non-boss seconds OR 30 kills since its last defeat.
 * Boss kills drop supplies but grant no automatic upgrades. Every three cleared
 * waves offer three choices, with one optional reroll; exactly one is claimed.
 * The original seven drop items use a run-local ItemManager, never campaign state.
 * Delete this source and restart to remove the entry; TitleScreen has no direct
 * compile dependency. Recompile after edits with javac -encoding UTF-8.
 */
public final class EndlessMode extends Screen {
    private static final Font UI_FONT = loadUiFont();
    private static final Color MUTED = new Color(105,135,113);
    private static Font loadUiFont() {
        try { return Core.getFileManager().loadFont(12f); }
        catch (IOException | FontFormatException e) { return new Font(Font.MONOSPACED,Font.PLAIN,12); }
    }
    private static final Color[] SPARKLES = {Color.WHITE, Color.YELLOW, Color.ORANGE, Color.CYAN};
    private static final Controls controls = new Controls();
    private static engine.Frame boundFrame;
    private static Screen menuScreen;
    private static int menuIndex;
    private static boolean gateOpen, gateAvailable;
    private static final int GATE_WIDTH = 400, GATE_HEIGHT = 386;
    private final Run run;
    private final Records records;
    private final Ship player = new Ship(0, 0);
    private final Map<Run.Body, Entity> images = new HashMap<Run.Body, Entity>();
    private final List<EnemyShip> fading = new ArrayList<EnemyShip>();
    private final List<Particle> particles = new ArrayList<Particle>();
    private final ScreenShake shake = new ScreenShake();
    private final DamageDimEffect dim = new DamageDimEffect(900, .75f, new Color(150,0,0));
    private final GlitchEffect glitch = new GlitchEffect();
    private final boolean testing;
    private long previousTime;
    private double deathTime;
    private double lowHealthTime;
    private boolean saved;

    private EndlessMode(int width, int height, boolean testing) {
        super(width, height, 60);
        this.testing = testing;
        records = new Records(Paths.get(testing ? "endless-test-records.properties" : "endless-records.properties"));
        run = new Run(width,height,new Random());
        run.defeated = this::destroyed;
        run.playerHit = () -> {
            player.destroy(); dim.trigger(run.getLives() <= 1 ? 1f : .35f);
            if(run.getLives()==1) lowHealthTime=5;
        };
    }

    /** Only reads the existing achievement. The test bypass never modifies it. */
    public static boolean unlocked() {
        for (Achievement a : Core.getAchievementManager().getAchievements())
            if ("infinity_void".equals(a.getId())) return a.isUnlocked();
        return false;
    }

    private static Object drawingField(String name) {
        try {
            Field f = DrawManager.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(null);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    private static void bind() {
        engine.Frame frame = (engine.Frame)drawingField("frame");
        if (frame == boundFrame) return;
        boundFrame = frame;
        frame.addKeyListener(controls);
        frame.getContentPane().addMouseListener(controls);
        frame.getContentPane().addMouseMotionListener(controls);
    }

    private static Field menuField(String name) throws ReflectiveOperationException {
        Field field = TitleScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void prepareMenu(Screen screen) throws ReflectiveOperationException {
        bind();
        if (menuScreen == screen) return;
        menuScreen = screen;
        int ordinal = ((MenuItem)menuField("selected").get(screen)).ordinal();
        menuIndex = ordinal == 0 ? 0 : ordinal + 1;
        gateOpen = false;
        controls.clear();
    }

    private static int menuSpacing() {
        return ((FontMetrics)drawingField("fontRegularMetrics")).getHeight() * 5 / 4;
    }

    private static int menuBaseline(Screen screen, int index) {
        // Eight rows need one extra row above the original menu to clear its hints.
        return screen.getHeight() / 2 + menuSpacing() * (index + 1);
    }

    private static int menuAt(Screen screen, Point point) {
        if (point == null || point.x < 0 || point.x >= screen.getWidth()) return -1;
        FontMetrics metrics = (FontMetrics)drawingField("fontRegularMetrics");
        int spacing = menuSpacing();
        for (int i = 0; i <= MenuItem.values().length; i++) {
            int top = menuBaseline(screen, i) - metrics.getAscent()
                    - (spacing - metrics.getHeight()) / 2;
            if (point.y >= top && point.y < top + spacing) return i;
        }
        return -1;
    }

    public static boolean drawEntry(Screen screen) throws ReflectiveOperationException {
        prepareMenu(screen);
        Graphics2D g = (Graphics2D)drawingField("backBufferGraphics");
        Font old = g.getFont(); Color oldColor = g.getColor();
        MenuItem[] items = MenuItem.values();
        for (int i = 0; i <= items.length; i++) {
            MenuItem item = i == 1 ? null : items[i == 0 ? 0 : i - 1];
            String text = item == null ? "Endless Mode" : item.getTitle();
            boolean selected = i == menuIndex;
            if (selected) text = "> " + text + " <";
            g.setFont((Font)drawingField(selected ? "fontSelected" : "fontRegular"));
            g.setColor(selected ? Color.GREEN : item != null && !item.isEnabled()
                    ? Color.DARK_GRAY : Color.WHITE);
            g.drawString(text, (screen.getWidth()-g.getFontMetrics().stringWidth(text))/2,
                    menuBaseline(screen, i));
        }
        if ((Boolean)menuField("showingExitConfirm").get(screen)) controls.clear();
        if (gateOpen) drawGate(screen, g);
        g.setFont(old); g.setColor(oldColor);
        return true;
    }

    public static boolean updateEntry(Screen screen) throws Exception {
        prepareMenu(screen);
        Point click = controls.takeClick();
        boolean escape = controls.take(KeyEvent.VK_ESCAPE);
        boolean enter = controls.take(KeyEvent.VK_ENTER) | controls.take(KeyEvent.VK_SPACE);
        if (gateOpen) {
            Point local = click == null ? null : new Point(
                    click.x - (screen.getWidth()-GATE_WIDTH)/2,
                    click.y - (screen.getHeight()-GATE_HEIGHT)/2);
            if (escape || inside(local, 362,10,24,24)
                    || inside(local,22,320,gateAvailable ? 148 : 300,36)) {
                gateOpen = false;
                controls.clear();
            } else if (inside(local,344,320,34,36)) {
                startEndless(screen, true);
            } else if (gateAvailable && (enter || inside(local,182,320,140,36))) {
                startEndless(screen, false);
            }
            return true;
        }
        if (escape) return false; // Let the original menu show its exit confirmation.
        int hovered = menuAt(screen, controls.takeHover());
        if (hovered >= 0) menuIndex = hovered;
        if (controls.take(KeyEvent.VK_UP) | controls.take(KeyEvent.VK_W))
            menuIndex = Math.floorMod(menuIndex-1, MenuItem.values().length+1);
        if (controls.take(KeyEvent.VK_DOWN) | controls.take(KeyEvent.VK_S))
            menuIndex = (menuIndex+1) % (MenuItem.values().length+1);
        int clicked = menuAt(screen, click);
        if (clicked >= 0) menuIndex = clicked;
        boolean shortcut = controls.take(KeyEvent.VK_E);
        if (shortcut) menuIndex = 1;
        if (menuIndex != 1)
            menuField("selected").set(screen, MenuItem.values()[menuIndex == 0 ? 0 : menuIndex-1]);
        if (enter || clicked >= 0 || shortcut) {
            if (menuIndex == 1) {
                gateAvailable = unlocked();
                gateOpen = true;
                controls.clear();
            } else {
                java.lang.reflect.Method confirm = TitleScreen.class.getDeclaredMethod("confirm");
                confirm.setAccessible(true);
                confirm.invoke(screen);
            }
        }
        return true;
    }

    private static void startEndless(Screen screen, boolean testing) throws ReflectiveOperationException {
        gateOpen = false;
        controls.clear();
        boolean again;
        do {
            EndlessMode game = new EndlessMode(screen.getWidth(), screen.getHeight(), testing);
            again = boundFrame.setScreen(game) == 8;
            controls.clear();
        } while (again);
        menuField("escapeWasDown").set(screen, Core.getInputManager().isKeyDown(KeyEvent.VK_ESCAPE));
    }

    private static boolean inside(Point point, int x, int y, int w, int h) {
        return point != null && point.x >= x && point.x < x+w && point.y >= y && point.y < y+h;
    }

    private static void gateButton(Graphics2D g, Point pointer, String label, int x, int width) {
        boolean hover = inside(pointer, x,320,width,36);
        g.setColor(hover ? new Color(8,40,16) : new Color(0,16,5));
        g.fillRect(x,320,width,36);
        g.setColor(hover ? Color.GREEN : new Color(45,103,58));
        g.drawRect(x,320,width-1,35);
        g.setFont(UI_FONT);g.setColor(Color.GREEN);
        g.drawString(label,x+(width-g.getFontMetrics().stringWidth(label))/2,344);
    }

    private static void centerGate(Graphics2D g, String text, int y, Color color) {
        g.setColor(color);
        g.drawString(text,(GATE_WIDTH-g.getFontMetrics().stringWidth(text))/2,y);
    }

    private static void drawGate(Screen screen, Graphics2D graphics) {
        graphics.setColor(new Color(0,0,0,210));
        graphics.fillRect(0,0,screen.getWidth(),screen.getHeight());
        Graphics2D g = (Graphics2D)graphics.create();
        int left = (screen.getWidth()-GATE_WIDTH)/2, top = (screen.getHeight()-GATE_HEIGHT)/2;
        g.translate(left,top);
        Point pointer = controls.pointer();
        if (pointer != null) pointer.translate(-left,-top);
        g.setColor(Color.BLACK);g.fillRect(0,0,GATE_WIDTH,GATE_HEIGHT);
        g.setColor(new Color(0,9,3));
        for(int y=4;y<GATE_HEIGHT;y+=4)g.drawLine(1,y,GATE_WIDTH-2,y);
        g.setColor(new Color(40,115,54));g.drawRect(0,0,GATE_WIDTH-1,GATE_HEIGHT-1);
        g.setColor(Color.GREEN);
        for(int x:new int[]{0,GATE_WIDTH-17})for(int y:new int[]{0,GATE_HEIGHT-17})g.drawRect(x,y,16,16);
        g.setFont(UI_FONT.deriveFont(10f));g.setColor(MUTED);g.drawString("INVADERS / SURVIVAL",22,28);
        g.setColor(inside(pointer,362,10,24,24) ? Color.GREEN : MUTED);
        g.drawRect(362,10,23,23);
        g.drawLine(369,17,378,26);g.drawLine(378,17,369,26);
        g.setFont(UI_FONT.deriveFont(24f));centerGate(g,"ENDLESS",73,Color.GREEN);
        g.setColor(gateAvailable?Color.GREEN:new Color(110,132,116));pixelLock(g,182,95,4);
        g.setFont(UI_FONT.deriveFont(14f));centerGate(g,gateAvailable?"ACCESS GRANTED":"MODE LOCKED",163,gateAvailable?Color.GREEN:Color.WHITE);
        g.setFont(UI_FONT);centerGate(g,gateAvailable?"STAGE 10 COMPLETE. KEEP FIGHTING.":"CLEAR STAGE 10 TO UNLOCK.",190,MUTED);
        g.setColor(new Color(35,76,43));g.drawLine(22,210,378,210);
        String[] labels={"SURVIVE","SCORE","BOSS"};String[] values={"NO LIMIT","10/SEC","90S / 30"};
        for(int i=0;i<3;i++){int x=22+i*122;g.setColor(new Color(22,48,28));g.drawRect(x,224,112,58);
            g.setFont(UI_FONT.deriveFont(10f));g.setColor(MUTED);g.drawString(labels[i],x+10,244);
            g.setFont(UI_FONT);g.setColor(Color.WHITE);g.drawString(values[i],x+10,268);}
        gateButton(g,pointer,"BACK",22,gateAvailable?148:300);
        if(gateAvailable)gateButton(g,pointer,"START RUN",182,140);
        gateButton(g,pointer,"",344,34);
        g.setColor(new Color(185,174,96));pixelLock(g,352,328,2);
        g.setFont(UI_FONT.deriveFont(9f));g.setColor(MUTED);g.drawString("QA",352,310);
        centerGate(g,"ESC TO RETURN",374,MUTED);
        g.dispose();
    }

    private static void pixelLock(Graphics g,int x,int y,int scale) {
        g.fillRect(x+2*scale,y,5*scale,scale);
        g.fillRect(x+scale,y+scale,scale,4*scale);
        g.fillRect(x+7*scale,y+scale,scale,4*scale);
        g.fillRect(x,y+4*scale,9*scale,6*scale);
        g.setColor(Color.BLACK);g.fillRect(x+4*scale,y+6*scale,scale,2*scale);
    }
    @Override public void initialize(){previousTime=System.nanoTime(); controls.clear();}
    @Override public int run(){ super.run(); return returnCode; }
    @Override protected void update() {
        long now=System.nanoTime();
        double dt=Math.min(.1,(now-previousTime)/1_000_000_000.0); previousTime=now;
        Run.Phase before=run.getPhase();
        boolean esc=controls.take(KeyEvent.VK_ESCAPE), pause=controls.take(KeyEvent.VK_P);
        boolean enter=controls.take(KeyEvent.VK_ENTER), quit=controls.take(KeyEvent.VK_Q);
        if(before==Run.Phase.REWARD) {
            for(int i=0;i<3;i++) if(controls.take(KeyEvent.VK_1+i)) run.chooseReward(i);
            if(controls.take(KeyEvent.VK_R)) run.refreshRewards();
            Point click=controls.takeClick();
            if(click!=null) {
                for(int i=0;i<3;i++) if(click.x>=16&&click.x<=width-16
                        &&click.y>=125+i*76&&click.y<189+i*76) run.chooseReward(i);
                if(click.y>=height-76&&click.y<height-40) run.refreshRewards();
            }
        } else if(before==Run.Phase.PLAYING) {
            for(int i=0;i<run.items.getView().slots.size() && i<9;i++)
                if(controls.take(KeyEvent.VK_1+i)) run.useItem(i);
        }
        if(before==Run.Phase.OVER && deathTime>=3) {
            if(esc){returnCode=1; isRunning=false;}
            if(enter){returnCode=8; isRunning=false;}
        } else if(before==Run.Phase.PAUSED && quit) run.finish("RUN ENDED");
        else if(esc || pause) run.togglePause();
        if(before==run.getPhase()) {
            int move=(controls.down(KeyEvent.VK_RIGHT)||controls.down(KeyEvent.VK_D)?1:0)
                    -(controls.down(KeyEvent.VK_LEFT)||controls.down(KeyEvent.VK_A)?1:0);
            run.update(dt,move,controls.down(KeyEvent.VK_SPACE));
        }
        if(run.getPhase()==Run.Phase.OVER){
            deathTime+=dt;
            if(!saved){records.save(run); saved=true;}
        }
        if(run.getPhase()!=Run.Phase.PAUSED && run.getPhase()!=Run.Phase.REWARD) {
            lowHealthTime=Math.max(0,lowHealthTime-dt);
            player.setBlinking(run.getLives()==1 && lowHealthTime>0);
            for(Particle particle:particles) particle.update();
            particles.removeIf(Particle::isDead); fading.removeIf(EnemyShip::isFadeFinished);
            shake.update(); player.update();
            for(Entity image:images.values()) if(image instanceof EnemyShip) ((EnemyShip)image).update();
            else if(image instanceof MidBoss) ((MidBoss)image).update();
        }
        draw();
    }

    private Entity image(Run.Body b) {
        Entity result=images.get(b);
        if(result==null){
            result=b.boss ? new MidBoss((int)b.x,(int)b.y,DrawManager.SpriteType.MidBoss1)
                    : new EnemyShip((int)b.x,(int)b.y,run.getWave()%2==0
                    ? DrawManager.SpriteType.EnemyShipB1 : DrawManager.SpriteType.EnemyShipA1);
            images.put(b,result);
        }
        result.setPositionX((int)b.x); result.setPositionY((int)b.y); return result;
    }

    private void destroyed(Run.Body b) {
        Entity entity=image(b);
        EnemyShip explosion=entity instanceof EnemyShip ? (EnemyShip)entity
                : new EnemyShip((int)b.x,(int)b.y,DrawManager.SpriteType.EnemyShipA1);
        explosion.destroy(); fading.add(explosion);
        for(int i=0;i<14;i++) particles.add(new Particle(entity.getPositionX()+entity.getWidth()/2f,
                entity.getPositionY()+entity.getHeight()/2f,SPARKLES[i%SPARKLES.length]));
        shake.trigger(); images.remove(b);
    }

    private void draw() {
        drawManager.initDrawing(this);
        Graphics2D g=(Graphics2D)drawingField("backBufferGraphics");
        View view=run.items.getView();
        drawManager.setWorldOffset(shake.getOffsetX(),shake.getOffsetY());
        Run.Body boss=run.getBoss();
        for(Run.Body b:run.getEnemies()) {
            if(boss!=null && !b.boss) continue;
            Entity entity=image(b);
            if(run.getPhase()==Run.Phase.OVER) drawManager.drawEntityShrunk(entity,Math.max(0,1-deathTime/1.5));
            else drawManager.drawEntity(entity,(int)b.x,(int)b.y);
        }
        images.keySet().retainAll(run.getEnemies());
        for(EnemyShip e:fading) drawManager.drawEntity(e,e.getPositionX(),e.getPositionY());
        for(Particle p:particles) drawManager.drawParticle(p);
        for(Run.Body b:run.getShots()) {
            drawManager.drawSprite(run.isPlayerShot(b)?DrawManager.SpriteType.Bullet:DrawManager.SpriteType.EnemyBullet,
                    (int)b.x,(int)b.y,Color.WHITE);
        }
        for(DropView drop:view.drops) {
            g.setColor(ItemSystem.colorOf(drop.item.effectKind));
            g.fillRect((int)drop.bounds.x,(int)drop.bounds.y,(int)drop.bounds.width,(int)drop.bounds.height);
            g.setFont(UI_FONT);g.setColor(Color.BLACK);
            g.drawString(drop.item.displayName.substring(0,1),(int)drop.bounds.x+5,(int)drop.bounds.y+17);
        }
        player.setPositionX((int)run.getPlayer().x); player.setPositionY((int)run.getPlayer().y);
        drawManager.drawEntity(player,player.getPositionX(),player.getPositionY());
        for(EffectView effect:view.effects) if(effect.item.effectKind==EffectKind.SHIELD) {
            g.setColor(Color.CYAN);g.drawOval(player.getPositionX()-5,player.getPositionY()-5,36,26);
        }
        drawManager.setWorldOffset(0,0); drawManager.drawDamageDim(this,dim);
        g.setFont(UI_FONT);
        text(g,"ENDLESS / WAVE "+run.getWave()+(testing?" [QA]":""),14,22,Color.CYAN);
        text(g,"SCORE "+run.getScore(),14,44,Color.WHITE);
        text(g,"TIME "+run.getSeconds()+"s",width-124,22,Color.WHITE);
        text(g,"LIVES "+run.getLives(),width-124,44,Color.GREEN);
        text(g,boss==null?"BOSS IN "+run.getBossSecondsLeft()+" s OR "+(30-run.getCycleKills())+" KILLS"
                : "BOSS HP "+(int)Math.ceil(boss.hp)+" / "+(int)boss.maxHp,14,68,Color.GRAY);
        drawManager.drawHorizontalLine(this,83);
        g.setFont(UI_FONT.deriveFont(9f));int effectX=14;
        for(EffectView effect:view.effects) {
            String tag=effect.item.displayName.toUpperCase(java.util.Locale.ROOT);
            if(effect.item.effectKind==EffectKind.RAPID_FIRE)tag="RF";
            else if(effect.item.effectKind==EffectKind.BULLET_SPEED)tag="BS";
            else if(effect.item.effectKind==EffectKind.SCORE_BOOST)tag="SCORE X2";
            if(effect.stacks!=null)tag+=" X"+effect.stacks;
            if(effect.remainingMillis!=null)tag+=" "+(effect.remainingMillis+999)/1000+"S";
            g.setColor(ItemSystem.colorOf(effect.item.effectKind));g.drawString(tag,effectX,105);
            effectX+=g.getFontMetrics().stringWidth(tag)+12;
        }
        text(g,run.getNotice(),14,height-72,Color.YELLOW);
        text(g,"DMG +"+run.getDamageUpgrades()+"  SPD +"+run.getSpeedUpgrades()+"  SHOTS "+run.getProjectiles(),14,height-15,Color.GRAY);
        g.setFont(UI_FONT.deriveFont(9f));g.setColor(MUTED);
        for(int i=0;i<view.slots.size();i++) {
            ItemInfo slot=view.slots.get(i);
            g.drawString((i+1)+": "+(slot==null?"EMPTY":slot.displayName.toUpperCase(java.util.Locale.ROOT)),14+i*135,height-39);
        }
        glitch.setEnabled(run.getPhase()==Run.Phase.PLAYING && run.getLives()==1 && lowHealthTime>0);
        drawManager.drawGlitch(this,glitch);
        Run.Phase phase=run.getPhase();
        if(phase==Run.Phase.REWARD) drawRewards(g);
        else if(phase==Run.Phase.READY || phase==Run.Phase.INTERMISSION){
            panel(g,"GET READY / "+run.getCountdown());
            row(g,"A/D OR ARROWS MOVE / SPACE FIRE",0);
            row(g,"P / ESC PAUSE / Q END WHILE PAUSED",1);
            row(g,"10 PTS/SEC + 100 PTS/KILL",2);
        } else if(phase==Run.Phase.PAUSED){
            panel(g,"PAUSED"); row(g,"P / ESC RESUME",0); row(g,"Q END RUN AND SAVE",1);
        } else if(phase==Run.Phase.OVER){
            if(deathTime<3) drawManager.drawGameOverBanner(this,"GAME OVER",Math.min(9,(int)(deathTime*5)+1));
            else {panel(g,run.getEndReason()); row(g,"SCORE "+run.getScore()+" / BEST "+records.getScore(),0);
                row(g,"TIME "+run.getSeconds()+"s / KILLS "+run.getKills()+" / BOSSES "+run.getBosses(),1);
                row(g,"ENTER RETRY / ESC MAIN MENU",2); row(g,records.getStatus(),3);}
        }
        drawManager.completeDrawing(this);
    }
    private void panel(Graphics2D g,String title){drawManager.drawFadeOverlay(this,225); text(g,title,24,height/2-60,Color.GREEN);}
    private void drawRewards(Graphics2D g) {
        drawManager.drawFadeOverlay(this,245);
        g.setColor(new Color(40,115,54));g.drawRect(8,58,width-17,height-67);
        text(g,"WAVE "+(run.getWave()-1)+" CLEARED",24,84,Color.GREEN);
        text(g,"CHOOSE ONE REWARD",24,108,Color.WHITE);
        for(int i=0;i<run.offers.size();i++) {
            Run.Reward offer=run.offers.get(i);int y=125+i*76;
            g.setColor(new Color(0,18,6));g.fillRect(16,y,width-32,64);
            g.setColor(new Color(45,103,58));g.drawRect(16,y,width-32,64);
            text(g,"["+(i+1)+"] "+offer.title,28,y+25,offer.itemId==null?Color.GREEN:Color.CYAN);
            g.setFont(UI_FONT.deriveFont(10f));g.setColor(MUTED);g.drawString(offer.description,28,y+47);
        }
        g.setColor(new Color(45,103,58));g.drawRect(16,height-76,width-32,36);
        text(g,run.refreshed?"REFRESH USED":"[R] REFRESH / 1 AVAILABLE",28,height-52,run.refreshed?MUTED:Color.YELLOW);
        g.setFont(UI_FONT.deriveFont(9f));g.setColor(MUTED);g.drawString("CLICK A CARD OR PRESS 1 / 2 / 3",24,height-23);
    }
    private void row(Graphics2D g,String text,int index){text(g,text,24,height/2-20+index*27,Color.WHITE);}
    private static void text(Graphics2D g,String text,int x,int y,Color color){g.setFont(UI_FONT);g.setColor(color);g.drawString(text,x,y);}

    private static final class Controls extends MouseAdapter implements KeyListener {
        private final boolean[] down=new boolean[256], pressed=new boolean[256];
        private Point click, hover;
        private boolean moved;
        @Override public synchronized void keyPressed(KeyEvent e){int k=e.getKeyCode();if(k>=0&&k<256){if(!down[k])pressed[k]=true;down[k]=true;}}
        @Override public synchronized void keyReleased(KeyEvent e){int k=e.getKeyCode();if(k>=0&&k<256)down[k]=false;}
        @Override public void keyTyped(KeyEvent e){}
        synchronized boolean take(int k){boolean p=pressed[k];pressed[k]=false;return p;}
        synchronized boolean down(int k){return down[k];}
        synchronized Point takeClick(){Point p=click;click=null;return p;}
        synchronized Point takeHover(){Point p=moved?hover:null;moved=false;return p;}
        synchronized Point pointer(){return hover==null?null:new Point(hover);}
        synchronized void clear(){java.util.Arrays.fill(pressed,false); java.util.Arrays.fill(down,false); click=null;moved=false;}
        @Override public synchronized void mousePressed(MouseEvent e){if(e.getButton()==MouseEvent.BUTTON1)click=e.getPoint();}
        @Override public synchronized void mouseMoved(MouseEvent e){hover=e.getPoint();moved=true;}
    }

    public static final class Run {
    public enum Phase { READY, PLAYING, INTERMISSION, REWARD, PAUSED, OVER }

    public static final class Reward {
        public final String id, title, description, itemId;
        Reward(String id,String title,String description,String itemId){this.id=id;this.title=title;this.description=description;this.itemId=itemId;}
    }

    /** Geometry shared with rendering; positions use sub-pixel precision. */
    public static final class Body {
        public double x, y;
        public final int width, height;
        public final boolean boss;
        public double hp, maxHp;
        private double vx, vy, damage;

        private Body(double x, double y, int width, int height, boolean boss) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.boss = boss;
            hp = maxHp = 1;
        }
    }

    private final int width, height;
    private final Random random;
    private final List<Body> enemies = new ArrayList<Body>();
    private final List<Body> shots = new ArrayList<Body>();
    private final Body player;
    private final ItemManager itemManager;
    public final ItemAPI items;
    private List<Reward> offers=Collections.emptyList();
    private boolean refreshed;
    private int lifeCap=3,lifeBonus=500;
    private double itemTime, fireRate=1/.75, timeBonus;
    private long bonusPoints;
    private Phase phase = Phase.READY, resumePhase;
    private double elapsed, bossClock, countdown = 3, shotClock, enemyShotClock;
    private double invulnerability, moveRemainder, noticeClock;
    private int kills, cycleKills, bosses, wave = 1, lives = 3, projectiles = 1;
    private int direction = 1, damageUpgrades, speedUpgrades;
    private double damage = 1, playerSpeed = 120;
    private String notice = "", endReason = "";
    private java.util.function.Consumer<Body> defeated = b -> {};
    private Runnable playerHit = () -> {};

    public Run(int width, int height, Random random) {
        this.width = width;
        this.height = height;
        this.random = random;
        player = new Body(width / 2.0 - 13, height - 52, 26, 16, false);
        Properties config=new Properties();
        try(InputStream input=EndlessMode.class.getClassLoader().getResourceAsStream("item-balance.properties")){
            if(input!=null)config.load(input);
            String override=System.getProperty("invaders.itemBalance");
            if(override!=null)try(InputStream extra=Files.newInputStream(Paths.get(override))){config.load(extra);}
        }catch(IOException e){throw new IllegalStateException("Cannot load item balance",e);}
        lifeCap=Integer.parseInt(config.getProperty("life.maxLives","3"));
        lifeBonus=Integer.parseInt(config.getProperty("life.capBonusScore","500"));
        itemManager=new ItemManager(Integer.parseInt(config.getProperty("inventory.capacity","3")),new Random(random.nextLong()));
        items=new ItemAPI(itemManager);
        Map<String,Double> weights=new java.util.LinkedHashMap<String,Double>();
        Map<String,Double> decay=new HashMap<String,Double>();
        for(String id:new String[]{"life","shield","rapid_fire","bullet_speed","freeze","boost","score_boost"}){
            String key=id.equals("rapid_fire")?"rapidFire":id.equals("bullet_speed")?"bulletSpeed":id.equals("score_boost")?"scoreBoost":id;
            weights.put(id,Double.parseDouble(config.getProperty("drop.weight."+key,"1")));
        }
        decay.put("rapid_fire",Double.parseDouble(config.getProperty("rapidFire.dropDecay","0.7")));
        decay.put("bullet_speed",Double.parseDouble(config.getProperty("bulletSpeed.dropDecay","0.7")));
        Map<DropSource,DropRule> rules=new java.util.EnumMap<DropSource,DropRule>(DropSource.class);
        rules.put(DropSource.REGULAR_ENEMY,new DropRule(Double.parseDouble(config.getProperty("drop.regular.probability","0.15")),weights,decay));
        rules.put(DropSource.SPECIAL_ENEMY,new DropRule(Double.parseDouble(config.getProperty("drop.special.probability","1")),weights,decay));
        itemManager.beginLevel(new LevelRules("endless",0,width,94,player.y+player.height,
                Double.parseDouble(config.getProperty("drop.fallSpeed","80")),
                Double.parseDouble(config.getProperty("drop.width","24")),Double.parseDouble(config.getProperty("drop.height","24")),
                Long.parseLong(config.getProperty("drop.groundLifetimeMillis","8000")),rules),new LifePort(){
            public boolean canAddLife(){return true;}
            public boolean tryAddLife(){if(lives<lifeCap)lives++;else bonusPoints+=Math.round(lifeBonus*items.getModifiers().scoreMultiplier);return true;}
        });
        spawnWave();
    }

    /** Bounded fixed steps keep collision detection stable even after a slow frame. */
    public void update(double seconds, int movement, boolean fire) {
        if (!Double.isFinite(seconds) || seconds <= 0) return;
        double remaining = Math.min(seconds, 0.1);
        while (remaining > 0.000001) {
            double step = Math.min(remaining, 1.0 / 120);
            tick(step, Math.max(-1, Math.min(1, movement)), fire);
            remaining -= step;
        }
    }

    private void tick(double dt, int movement, boolean fire) {
        if (phase == Phase.PAUSED || phase == Phase.REWARD || phase == Phase.OVER) return;
        if (phase == Phase.READY || phase == Phase.INTERMISSION) {
            countdown -= dt;
            if (countdown <= 0) phase = Phase.PLAYING;
            return;
        }
        elapsed += dt;
        itemTime+=dt*1000;
        long milliseconds=(long)itemTime;itemTime-=milliseconds;
        itemManager.update(milliseconds,new PlayerSnapshot(new Bounds(player.x,player.y,player.width,player.height),invulnerability<=0,true));
        itemEvents();
        Modifiers mods=items.getModifiers();
        timeBonus+=dt*10*(mods.scoreMultiplier-1);
        if (getBoss() == null) bossClock += dt;
        invulnerability = Math.max(0, invulnerability - dt);
        noticeClock = Math.max(0, noticeClock - dt);
        player.x = Math.max(8, Math.min(width - player.width - 8,
                player.x + movement * playerSpeed * mods.movementSpeedMultiplier * dt));
        shotClock -= dt;
        if (fire && shotClock <= 0) {
            for (int i = 0; i < projectiles; i++) {
                Body shot = new Body(player.x + 10 + (i - (projectiles - 1) / 2.0) * 10,
                        player.y - 10, 6, 10, false);
                shot.vy = -(420+mods.bulletSpeedBonus*60);
                shot.damage = damage;
                shots.add(shot);
            }
            shotClock = 1/(fireRate*mods.fireRateMultiplier+mods.fireRateBonus);
        }
        // Trigger immediately, including mid-wave. Remaining enemies resume afterwards.
        if (getBoss() == null && (bossClock >= 90 || cycleKills >= 30)) spawnBoss();
        if(!mods.enemyMovementBlocked)moveEnemies(dt);
        enemyShotClock -= dt;
        if (enemyShotClock <= 0 && !enemies.isEmpty()) {
            fireEnemyShots();
            enemyShotClock = Math.max(0.32, 1.35 / getDifficulty());
        }
        updateShots(dt);
        if (phase == Phase.OVER) return;
        for (Body enemy : enemies) {
            if (!enemy.boss && enemy.y + enemy.height >= player.y) {
                lives = 0;
                finish("FORMATION BREACHED");
                return;
            }
        }
        if (enemies.isEmpty()) {
            wave++;
            shots.clear();
            spawnWave();
            phase = Phase.INTERMISSION;
            countdown = 2;
            if ((wave - 1) % 3 == 0 && lives < 3) {
                lives++;
                notice = "REPAIR: +1 LIFE";
                noticeClock = 5;
            }
            if((wave-1)%3==0){phase=Phase.REWARD;refreshed=false;rollRewards();}
        }
    }

    private void spawnWave() {
        int count = 12 + Math.min(23, (wave - 1) * 2);
        int columns = Math.min(7, 4 + (wave - 1) / 3);
        double left = (width - columns * 40) / 2.0;
        for (int i = 0; i < count; i++) {
            Body enemy = new Body(left + (i % columns) * 40,
                    118 + (i / columns) * 29, 24, 16, false);
            // Health keeps progressing after speed/density reach playable limits.
            enemy.hp = enemy.maxHp = 1 + Math.floor(elapsed / 180) * 0.5;
            enemies.add(enemy);
        }
        direction = 1;
        enemyShotClock = 1.2;
        moveRemainder = 0;
        invulnerability = 1.5;
    }

    private void spawnBoss() {
        Body boss = new Body(width / 2.0 - 16, 120, 32, 20, true);
        boss.hp = boss.maxHp = 18 + bosses * 8.0 + Math.floor(elapsed / 90) * 2;
        boss.vx = 85 + Math.min(85, bosses * 8);
        enemies.add(boss);
        shots.clear();
        enemyShotClock = 1;
        notice = "BOSS INCOMING";
        noticeClock = 2;
    }

    private void moveEnemies(double dt) {
        Body boss = getBoss();
        if (boss != null) {
            boss.x += boss.vx * dt;
            if (boss.x < 16 || boss.x + boss.width > width - 16) {
                boss.x = Math.max(16, Math.min(width - 16 - boss.width, boss.x));
                boss.vx = -boss.vx;
            }
            return; // Freeze the formation during the boss duel.
        }
        moveRemainder += dt;
        double interval = Math.max(0.08, 0.50 / getDifficulty());
        if (moveRemainder < interval) return;
        moveRemainder -= interval;
        boolean turn = false;
        for (Body enemy : enemies)
            if (enemy.x + direction * 8 < 12
                    || enemy.x + enemy.width + direction * 8 > width - 12) turn = true;
        if (turn) direction = -direction;
        for (Body enemy : enemies) {
            enemy.x += direction * 8;
            if (turn) enemy.y += 12;
        }
    }

    private void fireEnemyShots() {
        Body boss = getBoss();
        if (boss != null) {
            // Alternates a dodgeable fan and an aimed shot.
            if (random.nextBoolean()) {
                for (int i = -1; i <= 1; i++) addEnemyShot(boss, i * 75, 155);
            } else {
                double dx = player.x + 13 - (boss.x + boss.width / 2.0);
                double dy = player.y - boss.y;
                double length = Math.hypot(dx, dy);
                addEnemyShot(boss, dx / length * 190, dy / length * 190);
            }
        } else {
            Body enemy = enemies.get(random.nextInt(enemies.size()));
            addEnemyShot(enemy, 0, Math.min(240, 135 * getDifficulty()));
        }
    }

    private void addEnemyShot(Body source, double vx, double vy) {
        Body shot = new Body(source.x + source.width / 2.0 - 3,
                source.y + source.height, 6, 10, false);
        shot.vx = vx;
        shot.vy = vy;
        shots.add(shot);
    }

    private void updateShots(double dt) {
        boolean bossDefeated = false;
        Iterator<Body> iterator = shots.iterator();
        while (iterator.hasNext()) {
            Body shot = iterator.next();
            shot.x += shot.vx * dt;
            shot.y += shot.vy * dt;
            boolean remove = shot.y < 94 || shot.y > height || shot.x < -10 || shot.x > width;
            if (shot.vy < 0) {
                Body boss = getBoss();
                Iterator<Body> targets = enemies.iterator();
                while (targets.hasNext()) {
                    Body target = targets.next();
                    if (boss != null && !target.boss) continue;
                    if (!overlaps(shot, target)) continue;
                    target.hp -= shot.damage;
                    remove = true;
                    if (target.hp <= 0.00001) {
                        bonusPoints+=Math.round(100*(items.getModifiers().scoreMultiplier-1));
                        items.onEnemyDefeated(target.boss?DropSource.SPECIAL_ENEMY:DropSource.REGULAR_ENEMY,
                                target.x+target.width/2.0,target.y+target.height/2.0);
                        defeated.accept(target); targets.remove();
                        kills++;
                        if (target.boss) {
                            bosses++;
                            cycleKills = 0;
                            bossClock = 0;
                            bossDefeated = true;
                        } else cycleKills++;
                    }
                    break; // One projectile can damage exactly one enemy.
                }
            } else if (overlaps(shot, player)) {
                remove = true;
                if (invulnerability <= 0) {
                    if(items.tryBlockHit()){iterator.remove();continue;}
                    lives--; playerHit.run();
                    invulnerability = 1.5;
                    if (lives == 0) {
                        finish("SHIP LOST");
                        return;
                    }
                }
            }
            if (remove) iterator.remove();
        }
        if (bossDefeated) {
            shots.clear();
            phase = Phase.INTERMISSION;
            countdown = 2;
            invulnerability = 1.5;
            enemyShotClock = 1.2;
        }
    }

    public void useItem(int slot){if(phase==Phase.PLAYING){UseResult result=items.useSlot(slot);if(result!=UseResult.USED)notice="ITEM: "+result;noticeClock=3;itemEvents();}}
    private void itemEvents(){
        for(ItemAPI.ItemEvent event:items.drainEvents()){
            if(event.type==EventType.ITEM_COLLECTED||event.type==EventType.ITEM_GRANTED||event.type==EventType.EFFECT_STARTED){
                ItemInfo info=items.getItemInfo(event.itemId);notice=info.displayName.toUpperCase(java.util.Locale.ROOT);noticeClock=3;
            }else if(event.type==EventType.SHIELD_BLOCKED){notice="SHIELD BLOCKED A HIT";noticeClock=3;}
        }
    }
    private GrantRequest request(String id){return new GrantRequest("wave-"+(wave-1)+"-"+id,id,GrantSource.REWARD,GrantTiming.NOW);}
    private void rollRewards(){
        List<Reward> perks=new ArrayList<Reward>();
        perks.add(new Reward("damage","DAMAGE +10%","PERMANENT ATTACK UPGRADE",null));
        perks.add(new Reward("fire","FIRE RATE +10%","PERMANENT ATTACK SPEED UPGRADE",null));
        if(playerSpeed<360)perks.add(new Reward("speed","MOVE SPEED +10%","PERMANENT MOVEMENT UPGRADE",null));
        if(projectiles<5)perks.add(new Reward("projectile","+1 PROJECTILE","ADD ONE SHOT PER VOLLEY",null));
        List<Reward> supplies=new ArrayList<Reward>();
        for(ItemInfo item:items.getItemInfos())if(item.activationMode!=ActivationMode.EQUIPPED
                &&items.checkGrant(request(item.itemId)).status==GrantCheckStatus.ELIGIBLE){
            String hint;
            switch(item.effectKind){
                case LIFE:hint="+1 LIFE / FULL: +"+lifeBonus+" PTS";break;
                case SHIELD:hint="BLOCK 1 HIT / "+item.durationMillis/1000+"S / STORED";break;
                case FREEZE:hint="FREEZE MOVEMENT / "+item.durationMillis/1000+"S / STORED";break;
                case BOOST:hint="MOVE + ROF +50% / "+item.durationMillis/1000+"S / STORED";break;
                case SCORE_BOOST:hint="SCORE X2 / "+item.durationMillis/1000+"S / STORED";break;
                case RAPID_FIRE:hint="STACKING FIRE RATE / RUN-LONG";break;
                case BULLET_SPEED:hint="STACKING BULLET SPEED / RUN-LONG";break;
                default:hint="APPLIES IMMEDIATELY";
            }
            supplies.add(new Reward(item.itemId,item.displayName.toUpperCase(java.util.Locale.ROOT),hint,item.itemId));
        }
        Collections.shuffle(perks,random);Collections.shuffle(supplies,random);
        List<Reward> result=new ArrayList<Reward>();result.add(perks.remove(0));result.add(supplies.remove(0));
        perks.addAll(supplies);Collections.shuffle(perks,random);result.add(perks.get(0));
        offers=Collections.unmodifiableList(result);
    }
    public boolean refreshRewards(){if(phase!=Phase.REWARD||refreshed)return false;List<Reward> old=offers;
        for(int i=0;i<20;i++){rollRewards();if(!sameOffers(old,offers))break;}refreshed=true;return true;}
    private boolean sameOffers(List<Reward> a,List<Reward> b){java.util.Set<String> ids=new java.util.HashSet<String>();for(Reward r:a)ids.add(r.id);for(Reward r:b)if(!ids.contains(r.id))return false;return true;}
    public boolean chooseReward(int index){
        if(phase!=Phase.REWARD||index<0||index>=offers.size())return false;
        Reward reward=offers.get(index);
        if(reward.itemId!=null){GrantResult result=items.tryGrant(request(reward.itemId));if(result.status==GrantStatus.REJECTED)return false;itemEvents();}
        else if (reward.id.equals("damage")) {
            damage *= 1.10;
            damageUpgrades++;
            notice = "UPGRADE: DAMAGE +10%";
        } else if (reward.id.equals("speed")) {
            playerSpeed = Math.min(360, playerSpeed * 1.10);
            speedUpgrades++;
            notice = "UPGRADE: SPEED +10%";
        } else if(reward.id.equals("projectile")) {
            projectiles++;
            notice = "UPGRADE: +1 PROJECTILE";
        }else{fireRate*=1.10;notice="UPGRADE: FIRE RATE +10%";}
        noticeClock = 5;
        offers=Collections.emptyList();phase=Phase.INTERMISSION;countdown=2;return true;
    }

    private static boolean overlaps(Body a, Body b) {
        return a.x < b.x + b.width && a.x + a.width > b.x
                && a.y < b.y + b.height && a.y + a.height > b.y;
    }

    public void togglePause() {
        if (phase == Phase.OVER) return;
        if (phase == Phase.PAUSED) phase = resumePhase;
        else { resumePhase = phase; phase = Phase.PAUSED; }
    }

    public void finish(String reason) { phase = Phase.OVER; endReason = reason; }
    public Phase getPhase() { return phase; }
    public Body getPlayer() { return player; }
    public List<Body> getEnemies() { return Collections.unmodifiableList(enemies); }
    public List<Body> getShots() { return Collections.unmodifiableList(shots); }
    public boolean isPlayerShot(Body shot) { return shot.vy < 0; }
    public Body getBoss() {
        for (Body enemy : enemies) if (enemy.boss) return enemy;
        return null;
    }
    public long getSeconds() { return (long) elapsed; }
    public long getScore() { return getSeconds() * 10 + kills * 100L+bonusPoints+(long)timeBonus; }
    public int getKills() { return kills; }
    public int getWave() { return wave; }
    public int getLives() { return lives; }
    public int getBosses() { return bosses; }
    public int getProjectiles() { return projectiles; }
    public int getDamageUpgrades() { return damageUpgrades; }
    public int getSpeedUpgrades() { return speedUpgrades; }
    public int getCycleKills() { return cycleKills; }
    public int getBossSecondsLeft() { return Math.max(0, (int) Math.ceil(90 - bossClock)); }
    public int getCountdown() { return Math.max(1, (int) Math.ceil(countdown)); }
    public double getDifficulty() { return 1 + Math.floor(elapsed / 30) * 0.05; }
    public boolean isInvulnerable() { return invulnerability > 0; }
    public String getNotice() { return noticeClock > 0 ? notice : ""; }
    public String getEndReason() { return endReason; }
}

public static final class Records {
    private final Path path;
    private long score, seconds, kills;
    private String status = "";

    public Records(Path path) {
        this.path = path.toAbsolutePath();
        if (!Files.exists(path)) return;
        Properties data = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            data.load(input);
            long loadedScore = read(data, "score");
            long loadedSeconds = read(data, "seconds");
            long loadedKills = read(data, "kills");
            score = loadedScore;
            seconds = loadedSeconds;
            kills = loadedKills;
        } catch (IOException | IllegalArgumentException ex) {
            status = "RECORDS UNAVAILABLE; RUN IS SAFE.";
            Core.getLogger().warning("Cannot read endless records: " + ex.getMessage());
        }
    }

    private long read(Properties data, String key) {
        long value = Long.parseLong(data.getProperty(key, "0"));
        if (value < 0) throw new IllegalArgumentException("Negative record");
        return value;
    }

    public boolean save(Run run) {
        score = Math.max(score, run.getScore());
        seconds = Math.max(seconds, run.getSeconds());
        kills = Math.max(kills, run.getKills());
        Properties data = new Properties();
        data.setProperty("score", Long.toString(score));
        data.setProperty("seconds", Long.toString(seconds));
        data.setProperty("kills", Long.toString(kills));
        Path temporary = null;
        try {
            temporary = Files.createTempFile(path.getParent(), "endless-", ".tmp");
            try (OutputStream output = Files.newOutputStream(temporary)) {
                data.store(output, "Endless mode personal bests");
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            status = "PERSONAL BESTS SAVED";
            return true;
        } catch (IOException ex) {
            status = "SAVE FAILED; KEPT THIS SESSION.";
            Core.getLogger().warning("Cannot save endless records: " + ex.getMessage());
            return false;
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException ex) { Core.getLogger().fine(ex.getMessage()); }
            }
        }
    }

    public long getScore() { return score; }
    public long getSeconds() { return seconds; }
    public long getKills() { return kills; }
    public String getStatus() { return status; }
}

}
