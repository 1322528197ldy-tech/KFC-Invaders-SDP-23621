package engine;
import java.awt.Point;
import java.awt.Rectangle;

/** Shared letterbox geometry for displayed pixels and all pointer listeners. */
public final class ViewportTransform {
    private final int logicalWidth,logicalHeight;
    private final Rectangle bounds;
    public ViewportTransform(int logicalWidth,int logicalHeight,int width,int height) {
        this.logicalWidth=Math.max(1,logicalWidth);this.logicalHeight=Math.max(1,logicalHeight);
        double scale=Math.min(Math.max(1,width)/(double)this.logicalWidth,Math.max(1,height)/(double)this.logicalHeight);
        int w=Math.max(1,(int)Math.round(this.logicalWidth*scale)),h=Math.max(1,(int)Math.round(this.logicalHeight*scale));
        bounds=new Rectangle((width-w)/2,(height-h)/2,w,h);
    }
    public Rectangle bounds(){return new Rectangle(bounds);}
    public boolean contains(int x,int y){return bounds.contains(x,y);}
    public Point toLogical(int x,int y){return contains(x,y)?toLogicalUnclamped(x,y):new Point(-1,-1);}
    public Point toLogicalUnclamped(int x,int y){
        return new Point((int)Math.floor((x-bounds.x)*(double)logicalWidth/bounds.width),
                (int)Math.floor((y-bounds.y)*(double)logicalHeight/bounds.height));
    }
}
