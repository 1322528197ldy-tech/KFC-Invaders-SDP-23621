package screen;

import engine.Cooldown;
import engine.Core;
import engine.I18n;
import engine.I18n.Language;
import engine.TesterMode;
import java.awt.event.KeyEvent;
import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import engine.soundEngine.Sound;
import engine.soundEngine.SoundManager;

/**
 * Implements the settings screen.
 */
public class SettingsScreen extends Screen {
	/** Milliseconds between selection steps. */
	private static final int SELECTION_TIME = 200;
	/** Spacing between menu items. */
	private static final int SPACING = 23;
	private static final int LANGUAGE_ROW = 2;
	private static final int ROW_COUNT = LANGUAGE_ROW + 1;
	private static final Language[] LANGUAGES = Language.values();
	private static final Font LANGUAGE_FONT = new Font(Font.DIALOG, Font.PLAIN, 14);
	/** Cooldown for menu selection and volume changes. */
	private final Cooldown selectionCooldown;
	/** get volume from soundManager */
	private final SoundManager soundManager = SoundManager.getInstance();
	/** Current selected menu item. */
	private int currentMenuItem = 0;
	/** Vertical distance between the BGM and SFX sliders. */
	private static final int SLIDER_ROW_SPACING = 68;
	/** Slider currently being dragged, or -1 when none is active. */
	private int draggingSlider = -1;
	/** Whether the left mouse button was pressed on the previous frame. */
	private boolean mouseWasPressed;
	private final boolean fromEndless;
	private boolean testDialog;
	private boolean wrongCode;
	private boolean escapeWasDown;
	private boolean enterWasDown;

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
	public SettingsScreen(final int width, final int height, final int fps) {
		this(width, height, fps, false);
	}

	SettingsScreen(int width, int height, int fps, boolean fromEndless) {
		super(width, height, fps);
		this.fromEndless = fromEndless;
		this.returnCode = 1;
		this.selectionCooldown = Core.getCooldown(SELECTION_TIME);
		this.selectionCooldown.reset();

		this.mouseWasPressed = this.inputManager.isLeftMousePressed();
		this.escapeWasDown = this.inputManager.isKeyDown(KeyEvent.VK_ESCAPE);
		this.enterWasDown = this.inputManager.isKeyDown(KeyEvent.VK_ENTER);
	}

	/** Returns the current volume as a percentage. */
	private int getVolumePercent(final Sound.Category category) {
		return Math.round(this.soundManager.getVolume(category) * 100f);
	}

	/** Adjusts the selected category by the given percentage points. */
	private void adjustSelectedVolume(final int delta) {
		Sound.Category category = this.currentMenuItem == 0
				? Sound.Category.BGM
				: Sound.Category.SFX;

		int percent = Math.max(0,
				Math.min(100, getVolumePercent(category) + delta));

		this.soundManager.setVolume(category, percent / 100f);
	}
	/**
	 * Starts the action.
	 *
	 * @return Next screen code.
	 */
	public final int run() {
		super.run();
		return this.returnCode;
	}

	/**
	 * Updates keyboard input, slider dragging and screen drawing.
	 */
	@Override
	protected final void update() {
		super.update();

		boolean mousePressed = this.inputManager.isLeftMousePressed();
		boolean click = mousePressed && !this.mouseWasPressed;
		boolean escapeDown = this.inputManager.isKeyDown(KeyEvent.VK_ESCAPE);
		boolean enterDown = this.inputManager.isKeyDown(KeyEvent.VK_ENTER);
		boolean escape = escapeDown && !this.escapeWasDown;
		boolean enter = enterDown && !this.enterWasDown;
		this.escapeWasDown = escapeDown;
		this.enterWasDown = enterDown;

		if (this.testDialog) {
			updateTestDialog(click, enter, escape);
		} else if (this.inputDelay.checkFinished()) {
			if (escape) {
				this.draggingSlider = -1;
				this.isRunning = false;
			} else if (click && this.draggingSlider == -1 && hovered(testButton())) {
				this.testDialog = true;
				this.wrongCode = false;
				if (!TesterMode.isEnabled())
					this.inputManager.beginCodeEntry();
			} else {
				updateMouse(mousePressed);

				// Keyboard changes are paused while a slider is being dragged.
				if (this.draggingSlider == -1
						&& this.selectionCooldown.checkFinished()) {

					if (this.inputManager.isKeyDown(KeyEvent.VK_UP)) {
						this.currentMenuItem = Math.floorMod(this.currentMenuItem - 1, ROW_COUNT);
						this.selectionCooldown.reset();
					} else if (this.inputManager.isKeyDown(KeyEvent.VK_DOWN)) {
						this.currentMenuItem = (this.currentMenuItem + 1) % ROW_COUNT;
						this.selectionCooldown.reset();
					} else if (this.inputManager.isKeyDown(KeyEvent.VK_LEFT)) {
						adjustSelection(-1);
						this.selectionCooldown.reset();
					} else if (this.inputManager.isKeyDown(KeyEvent.VK_RIGHT)) {
						adjustSelection(1);
						this.selectionCooldown.reset();
					}
				}
			}
		}

		this.mouseWasPressed = mousePressed;
		draw();
	}

	private Rectangle testButton() {
		return new Rectangle(getWidth() - 141, getHeight() - 34, 129, 25);
	}

	private Rectangle dialogButton(boolean primary) {
		return new Rectangle(getWidth() / 2 + (primary ? 12 : -151),
				getHeight() / 2 + 83, 139, 29);
	}

	private boolean hovered(Rectangle bounds) {
		return bounds.contains(this.inputManager.getMouseX(), this.inputManager.getMouseY());
	}

	private void updateTestDialog(boolean click, boolean enter, boolean escape) {
		if (escape || (click && hovered(dialogButton(false)))) {
			closeTestDialog();
		} else if (enter || (click && hovered(dialogButton(true)))) {
			if (TesterMode.isEnabled()) {
				TesterMode.disable();
				closeTestDialog();
			} else if (TesterMode.enable(this.inputManager.enteredCode())) {
				closeTestDialog();
			} else {
				this.wrongCode = true;
				this.inputManager.beginCodeEntry();
			}
		}
	}

	private void closeTestDialog() {
		this.testDialog = false;
		this.inputManager.endCodeEntry();
	}

	private void adjustSelection(int direction) {
		if (this.currentMenuItem == LANGUAGE_ROW)
			I18n.cycle(direction);
		else
			adjustSelectedVolume(direction * 10);
	}

	/**
	 * Adjusts volume while dragging and updates mouse hover selection.
	 *
	 * @param mousePressed
	 *            Whether the left mouse button is currently held down.
	 */
	private void updateMouse(final boolean mousePressed) {
		int mouseX = this.inputManager.getMouseX();
		int mouseY = this.inputManager.getMouseY();
		boolean moved = this.inputManager.isMouseMoved();

		if (this.draggingSlider == -1) {
			for (int index = 0; index < LANGUAGES.length; index++) {
				if (languageOption(index).contains(mouseX, mouseY)) {
					if (moved || mousePressed)
						this.currentMenuItem = LANGUAGE_ROW;
					if (mousePressed && !this.mouseWasPressed)
						I18n.setLanguage(LANGUAGES[index]);
					return;
				}
			}
		}

		// Start dragging only when a new press occurs on a slider.
		if (mousePressed && !this.mouseWasPressed) {
			for (int index = 0; index < 2; index++) {
				boolean onTrack =
						mouseX >= sliderLeft() - 6
								&& mouseX <= sliderLeft() + sliderWidth() + 6
								&& Math.abs(mouseY - sliderY(index)) <= 14;

				if (onTrack) {
					this.draggingSlider = index;
					this.currentMenuItem = index;
					break;
				}
			}
		}

		// Keep adjusting the original slider until the button is released.
		if (this.draggingSlider != -1) {
			int percent = Math.round(
					(mouseX - sliderLeft()) * 100f / sliderWidth());

			percent = Math.max(0, Math.min(100, percent));

			Sound.Category category = this.draggingSlider == 0
					? Sound.Category.BGM
					: Sound.Category.SFX;

			this.soundManager.setVolume(category, percent / 100f);

			if (!mousePressed)
				this.draggingSlider = -1;
		} else if (moved && !mousePressed) {
			for (int index = 0; index < 2; index++) {
				boolean insideControl =
						mouseX >= sliderLeft() - 6
								&& mouseX <= sliderLeft() + sliderWidth() + 6
								&& mouseY >= sliderY(index) - 36
								&& mouseY <= sliderY(index) + 14;

				if (insideControl) {
					this.currentMenuItem = index;
					break;
				}
			}
		}
	}

	/**
	 * Returns the horizontal starting position of the volume sliders.
	 *
	 * @return Left edge of the slider tracks.
	 */
	private int sliderLeft() {
		return this.getWidth() / 5;
	}

	/**
	 * Returns the distance between the 0% and 100% positions.
	 *
	 * @return Slider track width in pixels.
	 */
	private int sliderWidth() {
		return Math.max(1, this.getWidth() * 3 / 5);
	}

	/**
	 * Returns the vertical centre of a volume slider.
	 *
	 * @param index
	 *            Slider index: 0 for BGM, 1 for SFX.
	 * @return Vertical centre of the slider track.
	 */
	private int sliderY(final int index) {
		return this.getHeight() / 4 + index * SLIDER_ROW_SPACING;
	}

	private int languageY() {
		return sliderY(1) + 62;
	}

	private Rectangle languageOption(int index) {
		int cellWidth = sliderWidth() / LANGUAGES.length;
		return new Rectangle(sliderLeft() + index * cellWidth, languageY() + 9, cellWidth - 5, 30);
	}

	/**
	 * Returns the baseline of a key-binding row below the sliders.
	 *
	 * @param row
	 *            Key-binding row index, starting from zero.
	 * @return Vertical position of the text baseline.
	 */
	private int bindingY(final int row) {
		return languageY() + 72 + SPACING * row;
	}

	/**
	 * Draws a horizontal volume slider with its label and percentage.
	 *
	 * @param index
	 *            Slider index: 0 for BGM, 1 for SFX.
	 * @param label
	 *            Name displayed above the slider.
	 * @param category
	 *            Sound category whose volume is displayed.
	 */
	private void drawVolumeSlider(final int index, final String label,
								  final Sound.Category category) {

		int left = sliderLeft();
		int width = sliderWidth();
		int centerY = sliderY(index);
		int percent = getVolumePercent(category);
		int thumbX = left + Math.round(width * percent / 100f);

		Color color = this.currentMenuItem == index
				? Color.GREEN
				: Color.WHITE;

		// Draw the label and percentage using the shared selection style.
		this.drawManager.drawMenuRow(
				this, I18n.text(label) + "  " + percent + "%",
				centerY - 22, this.currentMenuItem == index);

		// Unfilled track.
		this.drawManager.fillBox(
				left, centerY - 3, width + 1, 6, Color.DARK_GRAY);

		// Filled portion showing the current volume.
		this.drawManager.fillBox(
				left, centerY - 3, thumbX - left, 6, color);

		// Slider handle.
		this.drawManager.fillBox(
				thumbX - 5, centerY - 9, 11, 18, Color.WHITE);
	}

	private void drawLanguages() {
		this.drawManager.drawMenuRow(this, "Language", languageY(),
				this.currentMenuItem == LANGUAGE_ROW);
		for (int index = 0; index < LANGUAGES.length; index++) {
			Language option = LANGUAGES[index];
			Rectangle bounds = languageOption(index);
			boolean selected = I18n.language() == option;
			Color color = selected ? Color.GREEN : Color.LIGHT_GRAY;
			if (selected)
				this.drawManager.fillBox(bounds.x, bounds.y, bounds.width, bounds.height,
						new Color(0, 30, 9));
			this.drawManager.drawBox(bounds.x, bounds.y, bounds.width, bounds.height, color);
			this.drawManager.drawCenteredLabel(option.label(), bounds.x + bounds.width / 2,
					bounds.y + 20, I18n.font(LANGUAGE_FONT, option), color);
		}
	}

	private void drawButton(Rectangle bounds, String label, boolean highlighted) {
		Color color = highlighted ? Color.GREEN : Color.LIGHT_GRAY;
		this.drawManager.fillBox(bounds.x, bounds.y, bounds.width, bounds.height,
				highlighted ? new Color(0, 31, 12) : Color.BLACK);
		this.drawManager.drawBox(bounds.x, bounds.y, bounds.width, bounds.height, color);
		this.drawManager.drawSmallString(label,
				bounds.x + (bounds.width - this.drawManager.getSmallStringWidth(label)) / 2,
				bounds.y + bounds.height / 2 + 4, color);
	}

	private void drawTestDialog() {
		boolean enabled = TesterMode.isEnabled();
		int center = getHeight() / 2;
		this.drawManager.drawFadeOverlay(this, 204);
		this.drawManager.fillBox(27, center - 121, getWidth() - 54, 249, Color.BLACK);
		this.drawManager.drawBox(27, center - 121, getWidth() - 54, 249, Color.GREEN);
		this.drawManager.drawCenteredBigString(this, "Test mode", center - 79);
		drawDialogLine(enabled ? "All game content is unlocked for testing."
				: "Enter the test code", center - 39, Color.WHITE);
		if (!enabled) {
			String digits = "● ".repeat(this.inputManager.enteredCode().length()).strip();
			drawDialogLine(digits.isEmpty() ? "—" : digits, center - 9, Color.GREEN);
		}
		drawDialogLine(this.wrongCode ? "Incorrect code. Try again."
				: "Test progress stays separate from your save.", center + 22,
				this.wrongCode ? Color.ORANGE : Color.LIGHT_GRAY);
		if (this.fromEndless)
			drawDialogLine("Switching modes ends the current Endless run.", center + 50, Color.GRAY);
		drawButton(dialogButton(false), enabled ? "Back" : "Cancel", hovered(dialogButton(false)));
		drawButton(dialogButton(true), enabled ? "Disable test mode" : "Enable", true);
	}

	private void drawDialogLine(String label, int baseline, Color color) {
		this.drawManager.drawRegularString(label,
				(getWidth() - this.drawManager.getRegularStringWidth(label)) / 2, baseline, color);
	}

	/**
	 * Draws the volume sliders, key bindings and footer.
	 */
	private void draw() {
		this.drawManager.initDrawing(this);
		this.drawManager.drawScreenTitle(
				this, MenuItem.SETTINGS.getTitle());

		drawVolumeSlider(0, "BGM", Sound.Category.BGM);
		drawVolumeSlider(1, "SFX", Sound.Category.SFX);
		drawLanguages();

		this.drawManager.drawMenuRow(
				this, "Key Bindings", bindingY(0), false);

		this.drawManager.drawMenuRow(
				this, "- Move Left: A / Left Arrow -",
				bindingY(1), false);

		this.drawManager.drawMenuRow(
				this, "- Move Right: D / Right Arrow -",
				bindingY(2), false);

		this.drawManager.drawMenuRow(
				this, "- Shoot: Space -", bindingY(3), false);

		this.drawManager.drawMenuRow(
				this, "- Back: ESC -", bindingY(4), false);

		this.drawManager.drawKeyHints(
				this, "Up/Down: select | Left/Right: adjust | Esc: back");

		Rectangle test = testButton();
		boolean enabled = TesterMode.isEnabled();
		drawButton(test, enabled ? "Test mode on" : "Test mode", enabled || hovered(test));
		Color lockColor = enabled ? Color.GREEN : Color.LIGHT_GRAY;
		this.drawManager.drawBox(test.x + 11, test.y + 5, 5, 8, lockColor);
		this.drawManager.fillBox(test.x + 9, test.y + 11, 10, 8, lockColor);
		if (this.testDialog)
			drawTestDialog();
		this.drawManager.completeDrawing(this);
	}
}
