package engine;
import java.awt.Dimension;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/** Display-only preferences in a separate file beside the game JAR. */
public final class WindowSettings {
    public enum Mode {
        ORIGINAL("original","Original 448x520",448,520),MEDIUM("150","150% 672x780",672,780),
        LARGE("200","200% 896x1040",896,1040),FIT("fit","Fit screen",448,520);
        public final String id,label;
        private final int width,height;
        Mode(String id,String label,int width,int height){this.id=id;this.label=label;this.width=width;this.height=height;}
        public Dimension fit(int availableWidth,int availableHeight){
            int aw=Math.max(1,availableWidth),ah=Math.max(1,availableHeight);
            double s=Math.min(aw/(double)width,ah/(double)height);if(this!=FIT)s=Math.min(1,s);
            return new Dimension(Math.max(1,Math.min(aw,(int)Math.floor(width*s))),Math.max(1,Math.min(ah,(int)Math.floor(height*s))));
        }
        public Mode step(int direction){return values()[Math.floorMod(ordinal()+direction,values().length)];}
        public static Mode fromId(String value){for(Mode m:values())if(m.id.equals(value))return m;return ORIGINAL;}
    }
    private static volatile Mode selected;
    private static volatile boolean saved=true;
    private WindowSettings(){}
    public static Path preferenceFile(){
        String override=System.getProperty("game.displaySettingsDir");
        if(override!=null)return Paths.get(override).toAbsolutePath().resolve("window-settings.properties");
        try{Path p=Paths.get(WindowSettings.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return p.toAbsolutePath().getParent().resolve("window-settings.properties");
        }catch(Exception error){return Paths.get("window-settings.properties").toAbsolutePath();}
    }
    public static synchronized Mode mode(){if(selected==null)selected=read(preferenceFile());return selected;}
    public static Mode read(Path file){
        Properties p=new Properties();try(InputStream in=Files.newInputStream(file)){
            p.load(in);return Mode.fromId(p.getProperty("window.size"));
        }catch(IOException|IllegalArgumentException|SecurityException error){return Mode.ORIGINAL;}
    }
    public static boolean save(Path file,Mode mode){
        Path tmp=null;
        try{Files.createDirectories(file.toAbsolutePath().getParent());
            tmp=Files.createTempFile(file.toAbsolutePath().getParent(),"window-settings-",".tmp");
            Properties p=new Properties();p.setProperty("window.size",mode.id);
            try(OutputStream out=Files.newOutputStream(tmp)){p.store(out,"KFC Invaders window size");}
            try{Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException error){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
            return true;
        }catch(IOException|SecurityException error){return false;}
        finally{if(tmp!=null)try{Files.deleteIfExists(tmp);}catch(IOException|SecurityException ignored){}}
    }
    public static synchronized void select(Mode mode){selected=mode;saved=save(preferenceFile(),mode);}
    public static boolean isSaved(){return saved;}
}
