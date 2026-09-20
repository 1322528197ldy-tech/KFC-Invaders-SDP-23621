package screen;

import java.awt.event.KeyEvent;

/**
 * Implements the settings screen.
 */
public class SettingsScreen extends Screen {
	//temporary variables.(I'll replace these variables to other functions(e.g. SettingManager.getInstance().getBgmVolume))
	private int bgmVolume = 50;
	private int sfxVolume = 50;
	private int currentMenuItem = 0; //0:bgm, 1:sfx, 2:key bindings, 3:back

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
		super(width, height, fps);
		this.returnCode = 1;
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
	 * Updates the elements on screen and checks for events.
	 */
	protected final void update() {
		super.update();
		draw();

		if (this.inputDelay.checkFinished()) {
			if (this.inputManager.isKeyDown(KeyEvent.VK_ESCAPE)) {
				this.isRunning = false;
			}
			//select menu using spacebar logic
			if (this.inputManager.isKeyDown(KeyEvent.VK_SPACE)) {
				if (currentMenuItem == 3) {
					this.isRunning = false; //quit loop and go to main menu screen
				}
				this.inputDelay.reset();
			}

			//existing menu movement logic
			if (this.inputManager.isKeyDown(KeyEvent.VK_UP)) {
				currentMenuItem = Math.max(0, currentMenuItem - 1);
			} else if (this.inputManager.isKeyDown(KeyEvent.VK_DOWN)) {
				currentMenuItem = Math.min(3, currentMenuItem + 1);
				this.inputDelay.reset();
			}

			//volume(bgm, sfx) adjust logic
			if (this.inputManager.isKeyDown(KeyEvent.VK_LEFT)) {
				if (currentMenuItem == 0) bgmVolume = Math.max(0, bgmVolume - 10);
				else if (currentMenuItem == 1) sfxVolume = Math.max(0, sfxVolume - 10);
				this.inputDelay.reset();
			} else if (this.inputManager.isKeyDown(KeyEvent.VK_RIGHT)) {
				if (currentMenuItem == 0) bgmVolume = Math.min(100, bgmVolume + 10);
				else if (currentMenuItem == 1) sfxVolume = Math.min(100, sfxVolume + 10);
				this.inputDelay.reset();
			}
		}
	}

	/**
	 * Draws the elements associated with the screen.
	 */
	private void draw() {
		this.drawManager.initDrawing(this);
		this.drawManager.drawHorizontalLine(this, this.getHeight() / 3 - 20);
		this.drawManager.drawCenteredRegularString(this, "SETTINGS", this.getHeight() / 3);

		String bgmString = "BGM Volume: < " + bgmVolume + "% >";
		String sfxString = "SFX Volume: < " + sfxVolume + "% >";

		this.drawManager.drawCenteredRegularString(this, (currentMenuItem == 0 ? "-> " : "") + bgmString, this.getHeight() / 2);
		this.drawManager.drawCenteredRegularString(this, (currentMenuItem == 1 ? "-> " : "") + sfxString, this.getHeight() / 2 + 30);
		this.drawManager.drawCenteredRegularString(this, (currentMenuItem == 2 ? "-> " : "") + "Key Bindings", this.getHeight() / 2 + 60);
		this.drawManager.drawCenteredRegularString(this, (currentMenuItem == 3 ? "-> " : "") + "Back", this.getHeight() / 2 + 90);

		this.drawManager.completeDrawing(this);
	}
}
