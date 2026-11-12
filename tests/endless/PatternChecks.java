package engine;

import endless.WavePattern;
import item.ItemAPI.*;
import screen.EndlessMode;
import screen.EndlessMode.Run;
import screen.EndlessMode.Run.Body;
import screen.EndlessMode.Run.Phase;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import javax.imageio.ImageIO;
import sun.misc.Unsafe;

/** Bounds, real wave transitions, patterns, item freezing and actual screen previews. */
public final class PatternChecks {
    private static int checks;
    private static Field field(Class<?> type, String name) throws Exception {
        Field f=type.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static Object get(Object o,String name) throws Exception { return field(o.getClass(),name).get(o); }
    private static void set(Object o,String name,Object value) throws Exception { field(o.getClass(),name).set(o,value); }
    private static void call(Object o,String name,double dt) throws Exception {
        Method m=o.getClass().getDeclaredMethod(name,double.class);m.setAccessible(true);m.invoke(o,dt);
    }
    private static void check(boolean value,String message) { checks++;if(!value)throw new AssertionError(message); }
    @SuppressWarnings("unchecked")
    private static Run wave(int number) throws Exception {
        Run run=new Run(432,497,new Random(42));
        set(run,"wave",number-1);set(run,"phase",Phase.PLAYING);
        ((List<Body>)get(run,"enemies")).clear();
        run.update(.001,0,false);
        if(run.getPhase()==Phase.REWARD)run.chooseReward(0);
        return run;
    }
    private static boolean overlaps(double ax,double ay,int aw,int ah,double bx,double by,int bw,int bh) {
        return ax<bx+bw && ax+aw>bx && ay<by+bh && ay+ah>by;
    }
    private static void layoutChecks() {
        Set<WavePattern.Formation> shapes=new HashSet<WavePattern.Formation>();
        Set<WavePattern.Movement> motions=new HashSet<WavePattern.Movement>();
        Set<WavePattern.Attack> attacks=new HashSet<WavePattern.Attack>();
        for(int wave=1;wave<=100;wave++) {
            if(wave%5==0)continue;
            WavePattern pattern=WavePattern.forWave(wave);
            shapes.add(pattern.formation);motions.add(pattern.movement);attacks.add(pattern.attack);
            for(int width:new int[]{432,448}) {
                List<WavePattern.Slot> slots=pattern.slots(WavePattern.enemyCount(wave),width,497,24,16);
                check(slots.size()==WavePattern.enemyCount(wave),"Preserves enemy count at wave "+wave);
                for(int i=0;i<slots.size();i++) {
                    WavePattern.Slot a=slots.get(i);
                    check(a.x>=24 && a.x+24<=width-24 && a.y>=138 && a.y+16<=497-52-70,
                            "Safe spawn bounds at wave "+wave);
                    for(int j=0;j<i;j++) {
                        WavePattern.Slot b=slots.get(j);
                        check(!overlaps(a.x,a.y,24,16,b.x,b.y,24,16),"No overlapping spawn slots at wave "+wave);
                    }
                }
            }
        }
        check(shapes.size()==5 && motions.size()==3 && attacks.size()==3,"All shapes, movements and attacks reachable");
        check(WavePattern.enemyCount(Integer.MAX_VALUE)==35,"Long-run count cannot overflow");
        check(WavePattern.forWave(6).formation==WavePattern.Formation.STAGGERED,"Boss does not skip a formation");
        for(int wave:new int[]{1,2})check(WavePattern.forWave(wave).attack==WavePattern.Attack.SINGLE,"Gentle opening shots");
    }
    private static void movementAndFreeze() throws Exception {
        for(int number:new int[]{1,2,3,4,6,7,8,9,11,12,13,14,16,17,18,19,96,97,98,99}) {
            Run run=wave(number);set(run,"phase",Phase.PLAYING);set(run,"elapsed",600.0);
            for(int step=0;step<500;step++) {
                call(run,"moveEnemies",.1);
                for(Body a:run.getEnemies()) {
                    check(a.x>=12 && a.x+a.width<=420 && a.y>=138,"Movement stays inside walls/HUD at wave "+number);
                    for(Body b:run.getEnemies())if(a!=b)
                        check(!overlaps(a.x,a.y,a.width,a.height,b.x,b.y,b.width,b.height),"Movement preserves separation at wave "+number);
                }
            }
        }
        Run run=wave(3);set(run,"phase",Phase.PLAYING);
        run.update(.1,0,false);
        Body first=run.getEnemies().get(0);double x=first.x,y=first.y;
        double time=(Double)get(run,"formationTime");
        run.togglePause();for(int i=0;i<30;i++)run.update(.1,0,false);
        check(first.x==x && first.y==y && (Double)get(run,"formationTime")==time,"Pause freezes the whole pattern");
        run.togglePause();
        check(run.items.tryGrant(new GrantRequest("pattern-freeze","freeze",GrantSource.REWARD,GrantTiming.NOW)).isSuccess(),"Freeze granted");
        run.useItem(0);for(int i=0;i<10;i++)run.update(.1,0,false);
        check(first.x==x && first.y==y && (Double)get(run,"formationTime")==time,"Freeze stops vertical and horizontal pattern clocks");
        set(run,"phase",Phase.REWARD);for(int i=0;i<30;i++)run.update(.1,0,false);
        check(first.x==x && first.y==y,"Reward selection freezes geometry");
        for(int wave:new int[]{2,3,7}) {
            WavePattern p=WavePattern.forWave(wave);
            for(double phase:new double[]{0,.65,2.6}) {
                check(p.verticalOffset(0,phase)==0,"No spawn jump");
                check(p.verticalOffset(4,phase)==0,"Oscillation returns without descent drift");
            }
        }
    }
    @SuppressWarnings("unchecked")
    private static void shootingAndBosses() throws Exception {
        for(int number:new int[]{1,3,7}) {
            Run run=wave(number);set(run,"phase",Phase.PLAYING);set(run,"enemyShotClock",0.0);
            run.getPlayer().x=8;
            run.update(.001,0,false);
            List<Body> shots=run.getShots();
            WavePattern.Attack attack=WavePattern.forWave(number).attack;
            check(shots.size()==(attack==WavePattern.Attack.FAN?3:1),"Correct volley size for "+attack);
            for(Body shot:shots) {
                double vx=(Double)get(shot,"vx"),vy=(Double)get(shot,"vy");
                check(vy>0 && Math.abs(vx)<=vy*.55,"Bounded projectile angle");
                if(attack==WavePattern.Attack.SINGLE)check(vx==0,"Single shot travels vertically");
                if(attack==WavePattern.Attack.AIMED)check(vx<0,"Aimed shot points toward the player");
                for(Body enemy:run.getEnemies())
                    check(!overlaps(shot.x,shot.y,shot.width,shot.height,enemy.x,enemy.y,enemy.width,enemy.height),"Shots originate below exposed enemies");
            }
            if(attack==WavePattern.Attack.FAN)
                check((Double)get(shots.get(0),"vx")<0 && (Double)get(shots.get(1),"vx")==0
                        && (Double)get(shots.get(2),"vx")>0,"Fan leaves three separate dodgeable lanes");
            double vx=(Double)get(shots.get(0),"vx");
            run.getPlayer().x=400;run.update(.001,0,false);
            check((Double)get(shots.get(0),"vx")==vx,"Aimed shots never home after firing");
        }
        Run fan=wave(7);set(fan,"phase",Phase.PLAYING);
        Method fire=Run.class.getDeclaredMethod("fireEnemyShots");fire.setAccessible(true);
        for(int i=0;i<50;i++)fire.invoke(fan);
        check(fan.getShots().size()==18,"Ordinary incoming projectiles are capped");
        // Removing an exposed shooter must expose a surviving ship on its next volley.
        ((List<Body>)get(fan,"shots")).clear();
        ((List<Body>)get(fan,"enemies")).subList(1,fan.getEnemies().size()).clear();
        fire.invoke(fan);check(fan.getShots().size()==3,"Last surviving enemy still fires");
        for(int number:new int[]{5,10,15}) {
            Run run=wave(number);
            check(run.getEnemies().size()==1 && run.getBoss()!=null && run.getPhase()==Phase.BOSS_INTRO,
                    "Boss-only safe introduction preserved at wave "+number);
        }
    }
    private static void screenshots(Path directory) throws Exception {
        Files.createDirectories(directory);
        Unsafe unsafe=(Unsafe)field(Unsafe.class,"theUnsafe").get(null);
        Core.getDrawManager().setFrame((Frame)unsafe.allocateInstance(EndlessUiChecks.Offscreen.class));
        Constructor<EndlessMode> ctor=EndlessMode.class.getDeclaredConstructor(int.class,int.class,boolean.class);
        ctor.setAccessible(true);
        Method update=EndlessMode.class.getDeclaredMethod("update");update.setAccessible(true);
        BufferedImage sheet=new BufferedImage(432*3,529*2,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=sheet.createGraphics();
        int index=0;
        for(int wave:new int[]{1,2,3,4,6,7}) {
            EndlessMode screen=ctor.newInstance(432,497,true);screen.initialize();
            Run run=(Run)get(screen,"run");
            ((List<?>)get(run,"enemies")).clear();set(run,"wave",wave-1);set(run,"phase",Phase.PLAYING);
            run.update(.001,0,false);set(run,"phase",Phase.PLAYING);
            set(screen,"previousTime",System.nanoTime());update.invoke(screen);
            BufferedImage image=(BufferedImage)field(DrawManager.class,"backBuffer").get(null);
            ImageIO.write(image,"png",directory.resolve("formation-wave-"+wave+".png").toFile());
            int x=index%3*432,y=index/3*529;
            g.drawImage(image,x,y,null);g.setColor(java.awt.Color.WHITE);
            g.setFont(new java.awt.Font(java.awt.Font.MONOSPACED,java.awt.Font.PLAIN,11));
            g.drawString("W"+wave+"  "+run.getPatternLabel(),x+8,y+516);index++;
        }
        g.dispose();ImageIO.write(sheet,"png",directory.resolve("endless-formations.png").toFile());
    }
    public static void main(String[] args) throws Exception {
        Core.getLogger().setLevel(java.util.logging.Level.WARNING);
        layoutChecks();movementAndFreeze();shootingAndBosses();
        if(args.length>0)screenshots(Path.of(args[0]));
        System.out.println("PASS: "+checks+" endless formation / pattern checks");
    }
}
