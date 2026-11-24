package engine;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Real native peer sizing and localized raster layout; events stay within the test window. */
public final class NativeWindowChecks {
    private static int checks;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    public static void main(String[] args)throws Exception{
        Path output=Path.of(args[0]);Files.createDirectories(output);
        System.setProperty("game.displaySettingsDir",Files.createTempDirectory(output,"native-preferences-").toString());
        Frame frame=null;
        try{
            frame=new Frame(448,520);
            Core.getDrawManager().setFrame(frame);
            int width=frame.getLogicalWidth(),height=frame.getLogicalHeight();Insets i=frame.getInsets();
            check(width==448&&height==520,"original simulation bounds preserved");
            check(frame.getWidth()==448&&frame.getHeight()==520,"original native window size preserved");
            BufferedImage picture=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
            Graphics2D g=picture.createGraphics();g.setColor(Color.GREEN);g.fillRect(0,0,width,height);g.dispose();
            frame.present(picture);
            for(WindowSettings.Mode mode:WindowSettings.Mode.values()){
                frame.applyWindowMode(mode);
                check(frame.getLogicalWidth()==width&&frame.getLogicalHeight()==height,"resizing cannot change simulation bounds");
                java.lang.reflect.Field surface=Frame.class.getDeclaredField("surface");surface.setAccessible(true);
                Canvas pane=(Canvas)surface.get(frame);
                check(pane.getMouseListeners().length>0,"Canvas uses the shared mapped pointer listeners");
                check(pane.getWidth()==frame.getWidth()-i.left-i.right&&pane.getHeight()==frame.getHeight()-i.top-i.bottom,"Swing content really resizes with the window");
                check(Frame.viewport(pane.getWidth(),pane.getHeight(),width,height).width>0,"visible viewport remains drawable");
                BufferedImage white=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
                Graphics2D fill=white.createGraphics();fill.setColor(Color.WHITE);fill.fillRect(0,0,width,height);fill.dispose();
                for(int repaint=0;repaint<20;repaint++) {
                    frame.present(white,Color.WHITE);Toolkit.getDefaultToolkit().sync();Thread.sleep(20);
                }
                check(!pane.getBufferStrategy().contentsLost(),"native presentation buffer remains valid");
                for (I18n.Language language : I18n.Language.values()) {
                    I18n.setLanguage(language);
                    screen.SettingsScreen settings = new screen.SettingsScreen(width,height,60);
                    java.lang.reflect.Method draw=screen.SettingsScreen.class.getDeclaredMethod("draw");draw.setAccessible(true);draw.invoke(settings);
                    java.lang.reflect.Method option=screen.SettingsScreen.class.getDeclaredMethod("languageOption",int.class);option.setAccessible(true);
                    java.lang.reflect.Method binding=screen.SettingsScreen.class.getDeclaredMethod("bindingY",int.class);binding.setAccessible(true);
                    Rectangle languageCell=(Rectangle)option.invoke(settings,0);
                    java.lang.reflect.Field metricsField=DrawManager.class.getDeclaredField("fontRegularMetrics");metricsField.setAccessible(true);
                    FontMetrics metrics=(FontMetrics)metricsField.get(null);
                    check(languageCell.getMaxY() < (Integer)binding.invoke(settings,0)-metrics.getAscent(),"language controls do not overlap key bindings / "+language);
                    check((Integer)binding.invoke(settings,3)+metrics.getDescent()<height-62,"key bindings leave room for footer / "+language);
                    java.lang.reflect.Field buffer=DrawManager.class.getDeclaredField("backBuffer");buffer.setAccessible(true);
                    ImageIO.write((BufferedImage)buffer.get(null),"png",output.resolve("settings-"+mode.id+"-"+language.name().toLowerCase()+".png").toFile());
                }
                I18n.setLanguage(I18n.Language.ENGLISH);
            }
            frame.applyWindowMode(WindowSettings.Mode.ORIGINAL);
            check(frame.getWidth()==448&&frame.getHeight()==520,"restoring original dimensions is exact");
            System.out.println("PASS: "+checks+" real native window checks; logical="+width+"x"+height);
        }finally{if(frame!=null){Frame closing=frame;SwingUtilities.invokeAndWait(closing::dispose);}}
        System.exit(0);
    }
}
