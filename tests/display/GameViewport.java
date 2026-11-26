package engine;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;

/** Transforms input before dispatch to both the original and Endless listeners. */
@SuppressWarnings("serial")
public final class GameViewport extends JPanel {
    private final int logicalWidth,logicalHeight;
    private static final class Presentation {
        final BufferedImage image;
        final Color surround;
        Presentation(BufferedImage image,Color surround){this.image=image;this.surround=surround;}
    }
    private volatile Presentation presentation;
    public GameViewport(int logicalWidth,int logicalHeight){
        this.logicalWidth=logicalWidth;this.logicalHeight=logicalHeight;
        setBackground(Color.BLACK);setFocusable(false);setPreferredSize(new Dimension(logicalWidth,logicalHeight));
    }
    public void present(BufferedImage next){present(next,Color.BLACK);}
    public void present(BufferedImage next,Color surround){
        presentation=new Presentation(next,surround);repaint();
    }
    public ViewportTransform transform(){return new ViewportTransform(logicalWidth,logicalHeight,getWidth(),getHeight());}
    @Override protected void paintComponent(Graphics graphics){
        super.paintComponent(graphics);Presentation current=presentation;if(current==null)return;
        Graphics2D g=(Graphics2D)graphics.create();
        try{Rectangle b=transform().bounds();
            g.setColor(current.surround);g.fillRect(0,0,getWidth(),getHeight());
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(current.image,b.x,b.y,b.width,b.height,null);
        }finally{g.dispose();}
    }
    private MouseEvent logicalEvent(MouseEvent event){
        ViewportTransform t=transform();
        boolean dragging=event.getID()==MouseEvent.MOUSE_DRAGGED||event.getID()==MouseEvent.MOUSE_RELEASED;
        Point p=dragging?t.toLogicalUnclamped(event.getX(),event.getY()):t.toLogical(event.getX(),event.getY());
        return new MouseEvent(this,event.getID(),event.getWhen(),event.getModifiersEx(),p.x,p.y,
                event.getXOnScreen(),event.getYOnScreen(),event.getClickCount(),event.isPopupTrigger(),event.getButton());
    }
    @Override protected void processMouseEvent(MouseEvent event){
        if((event.getID()==MouseEvent.MOUSE_PRESSED||event.getID()==MouseEvent.MOUSE_CLICKED)
                &&!transform().contains(event.getX(),event.getY()))return;
        super.processMouseEvent(logicalEvent(event));
    }
    @Override protected void processMouseMotionEvent(MouseEvent event){super.processMouseMotionEvent(logicalEvent(event));}
}
