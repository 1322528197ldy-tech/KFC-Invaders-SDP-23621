import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.io.File;
import engine.*;
import entity.*;
import screen.*;
import sun.misc.Unsafe;
public class KfcBossChecks {
 public static class Offscreen extends engine.Frame {
  public Offscreen(){super(448,520);}
  public Graphics getGraphics(){return new BufferedImage(448,520,1).getGraphics();}
  public Insets getInsets(){return new Insets(0,0,0,0);}
 }
 static int checks;
 static Field f(Class<?> c,String n)throws Exception{Field x=c.getDeclaredField(n);x.setAccessible(true);return x;}
 static Object get(Object o,String n)throws Exception{return f(o.getClass(),n).get(o);}
 static void call(Object o,String n)throws Exception{Method m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);m.invoke(o);}
 static void ok(boolean v,String n){if(!v)throw new AssertionError(n);checks++;System.out.println("PASS "+n);}
 static void finishDelay(Object o)throws Exception{f(Cooldown.class,"time").set(f(Screen.class,"inputDelay").get(o),0L);}
 static boolean unlocked(String id){for(Achievement a:Core.getAchievementManager().getAchievements())if(a.getId().equals(id))return a.isUnlocked();throw new AssertionError(id);}
 static void image(String name)throws Exception{ImageIO.write((BufferedImage)f(DrawManager.class,"backBuffer").get(null),"png",new File(argsDir,name));}
 static String argsDir;
 public static void main(String[] args)throws Exception{
 argsDir=args[0];new File(argsDir).mkdirs();
 Unsafe u=(Unsafe)f(Unsafe.class,"theUnsafe").get(null);Core.getDrawManager().setFrame((engine.Frame)u.allocateInstance(Offscreen.class));
 ok(((Font)f(DrawManager.class,"fontRegular").get(null)).getFamily().contains("Silkscreen"),"standard Silkscreen font loads");
 for(Ship.ShipType type:Ship.ShipType.values()){Core.getDrawManager().initDrawing(new Screen(448,520,60));Ship ship=new Ship(100,100,type);Core.getDrawManager().drawEntity(ship,100,100);ok(ship.getWidth()==26,"new ship sprite renders: "+type);}
 Core.getInputManager();
 for(int level=1;level<=10;level++){
 GameSettings settings=(GameSettings)f(Core.class,"SETTINGS_LEVEL_"+level).get(null);GameScreen g=new GameScreen(new GameState(level,0,3,0,0,0,0,0),settings,false,448,520,60);g.initialize();call(g,"update");ok(!((boolean)get(g,"levelFinished")),"Stage "+level+" initializes and renders");
 if(level!=5&&level!=10){EnemyShip e=((EnemyShipFormation)get(g,"enemyShipFormation")).iterator().next();f(GameScreen.class,"coinDropManager").set(g,new CoinDropManager(1.0));Set<Bullet> shot=(Set<Bullet>)get(g,"bullets");shot.add(BulletPool.getBullet(e.getPositionX()+8,e.getPositionY()+4,-6));call(g,"manageCollisions");ok((int)get(g,"shipsDestroyed")==1,"normal kill Stage "+level);ok(((Set<?>)get(g,"coins")).size()==1,"normal coin drop Stage "+level);ok(!((java.util.List<?>)get(g,"particles")).isEmpty(),"normal VFX Stage "+level);}
 if(level==5||level==10){image("stage"+level+".png");Boss boss=(Boss)get(g,"midBoss");
 int x=boss.getPositionX();boss.moveBoss(448,520,settings);ok(boss.getPositionX()==x+settings.getBaseSpeed(),"boss movement Stage "+level);
 Set<Bullet> shots=(Set<Bullet>)get(g,"bullets");
 for(int hit=0;hit<(level==5?2:5);hit++){shots.add(BulletPool.getBullet(boss.getPositionX()+10,boss.getPositionY()+4,-6));call(g,"manageCollisions");}
 ok((int)get(g,"score")== (level==5?1200:6500),"boss hit and defeat score Stage "+level);ok((int)get(g,"shipsDestroyed")==1,"one boss kill Stage "+level);
 ok(unlocked("first_boss_kill"),"boss achievement hook");call(g,"update");ok((int)get(g,"pendingDiamonds")==level,"boss clear diamonds Stage "+level);call(g,"update");ok((int)get(g,"pendingDiamonds")==level,"no repeated clear reward Stage "+level);
 if(level==10){ok(unlocked("infinity_void"),"Stage 10 achievement hook");ok(unlocked("starter_ship_win"),"final-stage win hook");}
 g=new GameScreen(new GameState(level,0,3,0,0,0,0,0),settings,false,448,520,60);g.initialize();f(GameScreen.class,"lives").set(g,0);call(g,"update");ok((boolean)get(g,"gameOverActive"),"boss game over starts without null formation Stage "+level);f(Cooldown.class,"time").set(get(g,"gameOverRowCooldown"),0L);call(g,"update");ok((int)get(g,"pendingDiamonds")==0,"boss death has no clear diamonds Stage "+level);
 }
 }
 MidBoss b=new MidBoss(100,80,DrawManager.SpriteType.MidBoss1);Set<Bullet> bullets=new HashSet<>();GameSettings settings=new GameSettings(1,1,5,530);
 ok(!b.shoot(bullets,settings),"initial boss shooting delay");f(Cooldown.class,"time").set(get(b,"shootCooldown"),0L);ok(b.shoot(bullets,settings),"boss fires after delay");ok((int)get(b,"shootingInterval")==500,"shooting interval floor");b.destroy();ok(!b.shoot(bullets,settings),"destroyed boss cannot shoot");
 System.out.println("TOTAL "+checks+" checks passed");
 }
}
