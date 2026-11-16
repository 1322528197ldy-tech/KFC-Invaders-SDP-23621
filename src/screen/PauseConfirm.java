package screen;

import engine.DrawManager;

/**
 * "Are you sure?" step shown before a pause menu option that throws away
 * progress (Restart, Quit) is carried out. Shared by the normal stages
 * ({@link GameScreen}) and endless mode ({@link EndlessMode}).
 *
 * This class owns everything about the confirmation: which actions need
 * one, the text explaining what each action does, the Yes/No cursor and
 * how the box is drawn. The screens only forward key presses and carry out
 * the action once {@link #confirm()} hands it back.
 *
 * To change a message, edit the matching {@link Action} below. To put a
 * new option behind a confirmation, add an {@link Action} for it and open
 * it from the screen's pause menu.
 */
public final class PauseConfirm {

	/**
	 * Actions that ask for confirmation. Each one carries the question
	 * shown as the big title and a short explanation of what saying Yes
	 * does: the first line (white) says what happens, the following
	 * lines (gray) add the detail. Keep the title short, it is drawn in
	 * the big font; the lines are shrunk to fit the box if too long.
	 */
	public enum Action {
		/** Restart of the current level in a normal stage. */
		NORMAL_RESTART("Really restart?",
				"This level will start over",
				"Score, lives and loot are reset"),
		/** Giving up the run from a normal stage. */
		NORMAL_QUIT("Really quit?",
				"This run will end now",
				"You go to the results screen"),
		/** Restart of an endless run. */
		ENDLESS_RESTART("Really restart?",
				"A new run will start now",
				"This run is not cashed out"),
		/** Cashing out / quitting an endless run. */
		ENDLESS_QUIT("Really quit?",
				"This run will end now",
				"Your earnings are cashed out");

		/** Question shown at the top of the box. */
		private final String title;
		/** What the action does, one entry per line. */
		private final String[] description;

		Action(final String title, final String... description) {
			this.title = title;
			this.description = description;
		}

		/** @return Question shown at the top of the box. */
		public String getTitle() {
			return this.title;
		}

		/** @return What the action does, one entry per line. */
		public String[] getDescription() {
			return this.description.clone();
		}
	}

	/** Key help shown under the box while it is open. */
	public static final String KEY_HINTS =
			"a+d / arrows to choose, space to confirm, Esc to cancel";

	/** Action waiting for an answer, or null while the box is closed. */
	private Action pending;
	/** Whether the cursor is on Yes. */
	private boolean yesSelected;

	/**
	 * Opens the confirmation for an action. The cursor always starts on
	 * No, so a stray key press cannot throw the run away.
	 *
	 * @param action
	 *            Action to ask about.
	 */
	public void open(final Action action) {
		this.pending = action;
		this.yesSelected = false;
	}

	/** Closes the confirmation without doing anything (same as No). */
	public void close() {
		this.pending = null;
		this.yesSelected = false;
	}

	/** @return Whether the confirmation is currently on screen. */
	public boolean isOpen() {
		return this.pending != null;
	}

	/** @return Action being asked about, or null while closed. */
	public Action getPending() {
		return this.pending;
	}

	/** @return Whether the cursor is on Yes. */
	public boolean isYesSelected() {
		return this.yesSelected;
	}

	/** Moves the cursor to Yes (drawn on the left). */
	public void selectYes() {
		this.yesSelected = true;
	}

	/** Moves the cursor to No (drawn on the right). */
	public void selectNo() {
		this.yesSelected = false;
	}

	/**
	 * Answers with whatever the cursor is on and closes the box.
	 *
	 * @return The action to carry out if the answer was Yes, or null if it
	 *         was No (or the box was not open).
	 */
	public Action confirm() {
		Action confirmed = this.yesSelected ? this.pending : null;
		close();
		return confirmed;
	}

	/**
	 * Draws the confirmation box. Does nothing while closed.
	 *
	 * @param drawManager
	 *            Draw manager of the screen.
	 * @param screen
	 *            Screen to draw on.
	 */
	public void draw(final DrawManager drawManager, final Screen screen) {
		if (this.pending == null)
			return;
		drawManager.drawConfirmDialog(screen, this.pending.title,
				this.pending.description, this.yesSelected);
		drawManager.drawKeyHints(screen, KEY_HINTS);
	}
}
