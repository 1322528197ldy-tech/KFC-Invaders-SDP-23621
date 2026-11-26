package screen;

import java.awt.Insets;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import engine.Cooldown;
import engine.Core;
import engine.DrawManager;
import engine.InputManager;

/**
 * Implements a generic screen.
 * 
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 * 
 */
public class Screen {
	
	/** Milliseconds until the screen accepts user input. */
	private static final int INPUT_DELAY = 1000;

	/** Draw Manager instance. */
	protected DrawManager drawManager;
	/** Input Manager instance. */
	protected InputManager inputManager;
	/** Application logger. */
	protected Logger logger;

	/** Screen width. */
	protected int width;
	/** Screen height. */
	protected int height;
	/** Frames per second shown on the screen. */
	protected int fps;
	/** Screen insets. */
	protected Insets insets;
	/** Time until the screen accepts user input. */
	protected Cooldown inputDelay;

	/** If the screen is running. */
	protected boolean isRunning;
	/** What kind of screen goes next. */
	protected int returnCode;

    private boolean navigationMouseWasDown;

	/**
	 * Constructor, establishes the properties of the screen.
	 * 
	 * @param width
	 *            Screen width.
	 * @param height
	 *            Screen height.
	 * @param fps
	 *            Frames per second, frame rate at which the game is run.
	 */
	public Screen(final int width, final int height, final int fps) {
		this.width = width;
		this.height = height;
		this.fps = fps;

		this.drawManager = Core.getDrawManager();
		this.inputManager = Core.getInputManager();
		this.logger = Core.getLogger();
		this.inputDelay = Core.getCooldown(INPUT_DELAY);
		this.inputDelay.reset();
		this.returnCode = 0;
        this.navigationMouseWasDown = inputManager.isLeftMousePressed();
        // The key used to leave the previous page must not also leave this one.
        inputManager.consumeEscapePress();
	}

	/**
	 * Initializes basic screen properties.
	 */
	public void initialize() {

	}

	/**
	 * Activates the screen.
	 * 
	 * @return Next screen code.
	 */
	public int run() {
		this.isRunning = true;

		while (this.isRunning) {
			long time = System.currentTimeMillis();

			update();

			time = (1000 / this.fps) - (System.currentTimeMillis() - time);
			if (time > 0) {
				try {
					TimeUnit.MILLISECONDS.sleep(time);
				} catch (InterruptedException e) {
					return 0;
				}
			}
		}

		return 0;
	}

	/**
	 * Updates the elements on screen and checks for events.
	 */
	protected void update() {
	}

    /** Back is immediate; activation delays apply only to forward/destructive choices. */
    protected final boolean takeEscapePress() { return inputManager.consumeEscapePress(); }

    /** Menus keep the original canvas and share one scaled, edge-triggered Back button. */
    protected boolean navigationVisible() {
        return getClass() != Screen.class && !(this instanceof TitleScreen)
                && !(this instanceof StartupScreen) && !(this instanceof SettingsScreen);
    }

    protected java.awt.Rectangle navigationBounds() {
        return new java.awt.Rectangle(12,12,90,28);
    }

    protected String navigationLabel() { return "< Back"; }

    /** Ordinary menus retain their guard against accidental activation on entry. */
    protected boolean navigationReady() { return inputDelay.checkFinished(); }

    protected final boolean takeNavigationClick() {
        boolean down = inputManager.isLeftMousePressed();
        boolean clicked = down && !navigationMouseWasDown;
        navigationMouseWasDown = down;
        return navigationVisible() && navigationReady() && clicked
                && navigationBounds().contains(inputManager.getMouseX(),inputManager.getMouseY());
    }

    /** Called after content so the navigation control remains readable. */
    public final void drawNavigation() {
        if (!navigationVisible()) return;
        java.awt.Rectangle b = navigationBounds();
        boolean hover = b.contains(inputManager.getMouseX(),inputManager.getMouseY());
        java.awt.Color color = hover ? java.awt.Color.GREEN : java.awt.Color.GRAY;
        drawManager.fillBox(b.x,b.y,b.width,b.height,new java.awt.Color(8,20,13));
        drawManager.drawBox(b.x,b.y,b.width-1,b.height-1,color);
        drawManager.drawRegularString(navigationLabel(),b.x+10,b.y+19,color);
    }

	/**
	 * Getter for screen width.
	 * 
	 * @return Screen width.
	 */
	public final int getWidth() {
		return this.width;
	}

	/**
	 * Getter for screen height.
	 * 
	 * @return Screen height.
	 */
	public final int getHeight() {
		return this.height;
	}
}
