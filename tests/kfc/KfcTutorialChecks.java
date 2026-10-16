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
public class KfcTutorialChecks {
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
 boolean[] keys=(boolean[])f(InputManager.class,"keys").get(null);Arrays.fill(keys,false);
 TutorialScreen tutorial=new TutorialScreen(448,520,60);tutorial.initialize();call(tutorial,"update");image("tutorial.png");
 for(Object step:f(TutorialScreen.class,"step").getType().getEnumConstants()){
 Arrays.fill(keys,false);tutorial=new TutorialScreen(448,520,60);tutorial.initialize();f(TutorialScreen.class,"step").set(tutorial,step);keys[KeyEvent.VK_S]=true;ok(tutorial.run()==2,"tutorial S: "+step);
 Arrays.fill(keys,false);tutorial=new TutorialScreen(448,520,60);tutorial.initialize();f(TutorialScreen.class,"step").set(tutorial,step);keys[KeyEvent.VK_ESCAPE]=true;keys[KeyEvent.VK_S]=true;ok(tutorial.run()==1,"tutorial ESC priority: "+step);}
 Arrays.fill(keys,false);
 System.out.println("TOTAL "+checks+" checks passed");
 }
}
