package engine;

import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

/**
 * Manages keyboard and mouse input for the provided screen.
 * 
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 * 
 */
public final class InputManager implements KeyListener, MouseMotionListener, MouseListener, ResettableInput {

	/** Number of recognised keys. */
	private static final int NUM_KEYS = 256;
	/** Array with the keys marked as pressed or not. */
	private static volatile boolean[] keys;
	private StringBuilder enteredCode;
	/** Singleton instance of the class. */
	private static InputManager instance;
	/** Current mouse X position. */
	private volatile int mouseX;
	/** Current mouse Y position. */
	private volatile int mouseY;
	/** Whether the mouse has moved since the last check. */
	private final java.util.concurrent.atomic.AtomicBoolean isMoved =
            new java.util.concurrent.atomic.AtomicBoolean();
	/** Whether a mouse button is currently held down. */
	private volatile boolean isClick;
	/** Whether the left mouse button is held down. */
	private volatile boolean leftMousePressed;
    /** Preserve a quick Escape tap even when both events occur between two game updates. */
    private final java.util.concurrent.atomic.AtomicBoolean escapePressed =
            new java.util.concurrent.atomic.AtomicBoolean();

    public boolean consumeEscapePress() { return escapePressed.getAndSet(false); }
    private final java.util.concurrent.atomic.AtomicBoolean pausePressed =
            new java.util.concurrent.atomic.AtomicBoolean();
    public boolean consumePausePress() { return pausePressed.getAndSet(false); }

	/**
	 * Private constructor.
	 */
	private InputManager() {
		keys = new boolean[NUM_KEYS];
	}

	/**
	 * Returns shared instance of InputManager.
	 * 
	 * @return Shared instance of InputManager.
	 */
	protected static InputManager getInstance() {
		if (instance == null)
			instance = new InputManager();
		return instance;
	}

	/**
	 * Returns true if the provided key is currently pressed.
	 * 
	 * @param keyCode
	 *            Key number to check.
	 * @return Key state.
	 */
	public synchronized boolean isKeyDown(final int keyCode) {
		return keys[keyCode];
	}

    /** Release keyboard/mouse state together when native focus or visibility changes. */
    @Override public synchronized void resetInput() {
        this.leftMousePressed=false;this.isClick=false;this.isMoved.set(false);
        this.escapePressed.set(false);
        this.pausePressed.set(false);
        this.mouseX=-1;this.mouseY=-1;
        // Publish a fresh released state to the game thread as well as the event thread.
        keys=new boolean[NUM_KEYS];
    }

	/**
	 * Changes the state of the key to pressed.
	 * 
	 * @param key
	 *            Key pressed.
	 */
	@Override
	public synchronized void keyPressed(final KeyEvent key) {
		int code = key.getKeyCode();
		if (code >= 0 && code < NUM_KEYS) {
			if (!keys[code]) {
                if (enteredCode != null) editCode(code);
                if (code == KeyEvent.VK_ESCAPE) escapePressed.set(true);
                if (code == KeyEvent.VK_P) pausePressed.set(true);
            }
			keys[code] = true;
		}
	}

	public synchronized void beginCodeEntry() {
		enteredCode = new StringBuilder();
	}

	public synchronized String enteredCode() {
		return enteredCode == null ? "" : enteredCode.toString();
	}

	public synchronized void endCodeEntry() {
		enteredCode = null;
	}

	private void editCode(int code) {
		if (code == KeyEvent.VK_BACK_SPACE && enteredCode.length() > 0) {
			enteredCode.deleteCharAt(enteredCode.length() - 1);
		} else if (code == KeyEvent.VK_DELETE) {
			enteredCode.setLength(0);
		} else if (enteredCode.length() < 8) {
			if (code >= KeyEvent.VK_0 && code <= KeyEvent.VK_9)
				enteredCode.append(code - KeyEvent.VK_0);
			else if (code >= KeyEvent.VK_NUMPAD0 && code <= KeyEvent.VK_NUMPAD9)
				enteredCode.append(code - KeyEvent.VK_NUMPAD0);
		}
	}

	/**
	 * Changes the state of the key to not pressed.
	 * 
	 * @param key
	 *            Key released.
	 */
	@Override
	public synchronized void keyReleased(final KeyEvent key) {
		if (key.getKeyCode() >= 0 && key.getKeyCode() < NUM_KEYS)
			keys[key.getKeyCode()] = false;
	}

	/**
	 * Does nothing.
	 * 
	 * @param key
	 *            Key typed.
	 */
	@Override
	public void keyTyped(final KeyEvent key) {

	}

	/**
	 * Returns the current mouse X position.
	 *
	 * @return Current mouse X position.
	 */
	public int getMouseX() {
		return mouseX;
	}

	/**
	 * Returns the current mouse Y position.
	 *
	 * @return Current mouse Y position.
	 */
	public int getMouseY() {
		return mouseY;
	}
	
	/**
	 * Returns whether a mouse button is currently held down.
	 *
	 * @return true if a mouse button is held down.
	 */
	public boolean isMousePressed() { 
		return isClick;
	}

	/**
	 * Updates the mouse position and records mouse movement.
	 *
	 * @param e
	 * 			Mouse movement event.
	 */
	@Override
	public void mouseMoved(final MouseEvent e) {
		updateMousePosition(Frame.gamePoint(e));
		isMoved.set(true);

	}

	/**
	 * Returns whether the mouse has moved since the last check
	 *
	 *  @return true if movement was recorded
	 */

	public boolean isMouseMoved() {
		return isMoved.getAndSet(false);
	}
	/**
	 * Updates the mouse position and records movement while dragging.
	 *
	 * @param e
	 *            Mouse drag event containing the current pointer position.
	 */
	@Override
	public void mouseDragged(final MouseEvent e) {
		mouseMoved(e);
	}
	/**
	 * Does nothing.
	 * 
	 * @param e Mouse event.
	 */
	@Override
	public void mouseClicked(final MouseEvent e) {
	}
	/**
	 * Marks the mouse button as pressed
	 * 
	 * @param e Mouse event containing button and position information.
	 */
	@Override
	public void mousePressed(final MouseEvent e) {
		Point point = Frame.gameClick(e);
		if (point == null)
			return;
		updateMousePosition(point);
		isClick = true;
		if (e.getButton() == MouseEvent.BUTTON1)
			this.leftMousePressed = true;
	}
	/**
	 * Marks the mouse buttons as released
	 *
	 * @param e Mouse event containing button and position information.
	 */
	@Override
	public void mouseReleased(final MouseEvent e) {
		isClick = false;
		updateMousePosition(Frame.gamePoint(e));
		if (e.getButton() == MouseEvent.BUTTON1)
			this.leftMousePressed = false;
	}

	private void updateMousePosition(Point point) {
		mouseX = point.x;
		mouseY = point.y;
	}
	/**
	 * Does nothing.
	 * 
	 * @param e Mouse event.
	 *            
	 */
	@Override
	public void mouseEntered(final MouseEvent e) {

	}


	/**
	 * Clears the mouse button state when the mouse exits the component.
	 * 
	 * @param e Mouse event.
	 */
	@Override
	public void mouseExited(final MouseEvent e) {
		isClick = false;
		this.leftMousePressed = false;
	}

	/**
	 * Returns whether the left mouse button is currently held down.
	 *
	 * @return true if the left mouse button is held down.
	 */
	public boolean isLeftMousePressed() {
		return this.leftMousePressed;
	}
}
