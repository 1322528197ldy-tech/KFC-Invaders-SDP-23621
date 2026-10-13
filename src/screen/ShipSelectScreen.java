package screen;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;

import engine.Core;
import engine.DrawManager.SpriteType;
import engine.PurchaseManager;
import entity.Ship.ShipType;
import shop.Currency;
import shop.ShopCatalog;
import shop.ShopCategory;
import shop.ShopItem;
import shop.Skin;

/**
 * Implements the ship select screen, shown as "Customize" in the menu.
 * The player chooses the ship and skin for the next run from what they
 * own;
 * locked choices can be looked at but not used, and point to the shop.
 * The ship goes through Core.setSelectedShipType() (Mantis), the skin
 * through {@link PurchaseManager}, as soon as they are chosen.
 */
public class ShipSelectScreen extends Screen {

	/** The things that can be chosen. */
	private enum Row {
		/** Ship type. */
		SHIP("Ship"),
		/** Ship colour. */
		SKIN("Skin");

		/** Label on the left of the row. */
		private final String label;

		Row(final String label) {
			this.label = label;
		}
	}

	/** Space between the screen edge and the rows. */
	private static final int MARGIN = 32;
	/** Height of one row box. */
	private static final int ROW_HEIGHT = 30;
	/** Distance between two rows. */
	private static final int ROW_SPACING = 38;
	/** Width of the arrow at each end of a row. */
	private static final int ARROW_WIDTH = 28;
	/** Size of one sprite pixel in the big preview. */
	private static final int PREVIEW_PIXEL = 5;
	/** Colour of locked choices. */
	private static final Color LOCKED = new Color(255, 70, 70);
	/** Colour of unselected row borders. */
	private static final Color BORDER = new Color(70, 70, 70);
	/** Highest speed and fire rate among the ships, for the stat bars. */
	private static final double MAX_SPEED = maxSpeed();
	private static final double MAX_FIRE_RATE = maxFireRate();

	/** Keys watched for presses (not holds). */
	private static final int[] KEYS = { KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT,
			KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_A, KeyEvent.VK_D,
			KeyEvent.VK_W, KeyEvent.VK_S, KeyEvent.VK_SPACE,
			KeyEvent.VK_ENTER, KeyEvent.VK_ESCAPE };

	/** Array of all available ship types. */
	private final ShipType[] availableShips = ShipType.values();
	/** All skins. */
	private final Skin[] availableSkins = Skin.values();

	/** Row the cursor is on. */
	private Row row = Row.SHIP;
	/** Choice shown in each row, which may be locked. */
	private int shipIndex;
	private int skinIndex;
	/** Key states last frame, so a held key acts once. */
	private boolean[] keysWereDown;

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
	public ShipSelectScreen(final int width, final int height, final int fps) {
		super(width, height, fps);
		this.returnCode = 1; // Default return to main menu

		PurchaseManager loadout = PurchaseManager.getInstance();
		// Open on the ship chosen last time instead of always the first one.
		this.shipIndex = Core.getSelectedShipType().ordinal();
		this.skinIndex = loadout.getSelectedSkin().ordinal();
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

		if (this.keysWereDown == null) {
			// Keys still held from the menu must not act here.
			this.keysWereDown = new boolean[KEYS.length];
			for (int i = 0; i < KEYS.length; i++)
				this.keysWereDown[i] = this.inputManager.isKeyDown(KEYS[i]);
		}

		if (this.inputDelay.checkFinished()) {
			handleKeys();
		}
		draw();
	}

	// --- Input ---

	private boolean pressed(final int key) {
		for (int i = 0; i < KEYS.length; i++)
			if (KEYS[i] == key)
				return this.inputManager.isKeyDown(key)
						&& !this.keysWereDown[i];
		return false;
	}

	private void handleKeys() {
		boolean confirm = pressed(KeyEvent.VK_SPACE)
				|| pressed(KeyEvent.VK_ENTER);
		if (pressed(KeyEvent.VK_ESCAPE) || confirm) {
			this.isRunning = false;
		} else if (pressed(KeyEvent.VK_UP) || pressed(KeyEvent.VK_W)) {
			this.row = Row.values()[Math.floorMod(this.row.ordinal() - 1,
					Row.values().length)];
		} else if (pressed(KeyEvent.VK_DOWN) || pressed(KeyEvent.VK_S)) {
			this.row = Row.values()[(this.row.ordinal() + 1)
					% Row.values().length];
		} else if (pressed(KeyEvent.VK_LEFT) || pressed(KeyEvent.VK_A)) {
			change(this.row, -1);
		} else if (pressed(KeyEvent.VK_RIGHT) || pressed(KeyEvent.VK_D)) {
			change(this.row, 1);
		}
		for (int i = 0; i < KEYS.length; i++)
			this.keysWereDown[i] = this.inputManager.isKeyDown(KEYS[i]);
	}

	/**
	 * Shows the next or previous choice in a row, and uses it right away
	 * when it is unlocked.
	 *
	 * @param which
	 *            Row to change.
	 * @param step
	 *            1 for next, -1 for previous.
	 */
	private void change(final Row which, final int step) {
		PurchaseManager loadout = PurchaseManager.getInstance();
		switch (which) {
		case SHIP:
			this.shipIndex = Math.floorMod(this.shipIndex + step,
					this.availableShips.length);
			ShipType ship = this.availableShips[this.shipIndex];
			if (loadout.isShipUnlocked(ship)) {
				// Remember the choice for the menu and the game (Mantis).
				Core.setSelectedShipType(ship);
				this.logger.info("Ship selected: " + ship);
			}
			break;
		default:
			this.skinIndex = Math.floorMod(this.skinIndex + step,
					this.availableSkins.length);
			loadout.selectSkin(this.availableSkins[this.skinIndex]);
			break;
		}
	}

	// --- State of each row ---

	private boolean isUnlocked(final Row which) {
		PurchaseManager loadout = PurchaseManager.getInstance();
		switch (which) {
		case SHIP:
			return loadout.isShipUnlocked(this.availableShips[this.shipIndex]);
		default:
			return loadout.isSkinUnlocked(this.availableSkins[this.skinIndex]);
		}
	}

	private String choiceName(final Row which) {
		switch (which) {
		case SHIP:
			return ShopCatalog.shipName(this.availableShips[this.shipIndex]);
		default:
			return this.availableSkins[this.skinIndex].getDisplayName();
		}
	}

	/**
	 * @param which
	 *            Row.
	 * @return Shop entry that unlocks the row's current choice, or null.
	 */
	private ShopItem shopEntry(final Row which) {
		switch (which) {
		case SHIP:
			return ShopCatalog.findByRef(ShopCategory.SHIP,
					this.availableShips[this.shipIndex].name());
		default:
			return ShopCatalog.findByRef(ShopCategory.SKIN,
					this.availableSkins[this.skinIndex].name());
		}
	}

	// --- Layout ---

	private int previewTop() {
		return this.height / 8 + 24;
	}

	private Rectangle rowArea(final Row which) {
		int top = previewTop() + 84 + which.ordinal() * ROW_SPACING;
		return new Rectangle(MARGIN, top, this.width - MARGIN * 2,
				ROW_HEIGHT);
	}

	// --- Drawing ---

	/**
	 * Draws the elements associated with the screen.
	 */
	private void draw() {
		drawManager.initDrawing(this);

		drawManager.drawScreenTitle(this, MenuItem.SHIP_SELECT.getTitle());
		drawPreview();
		for (Row each : Row.values())
			drawRow(each);
		drawInfo();

		drawManager.drawKeyHints(this,
				"up/down row | left/right change | esc back");

		drawManager.completeDrawing(this);
	}

	/** The browsed ship, in the browsed skin, drawn large. */
	private void drawPreview() {
		ShipType type = this.availableShips[this.shipIndex];
		Color color = this.availableSkins[this.skinIndex]
				.colorAt(System.currentTimeMillis());
		if (!isUnlocked(Row.SHIP) || !isUnlocked(Row.SKIN))
			color = color.darker().darker();
		SpriteType sprite = type.getIdleSprite();
		int[] size = drawManager.getSpriteSize(sprite);
		drawManager.drawSpriteScaled(sprite, this.width / 2 - size[0]
				* PREVIEW_PIXEL / 2, previewTop() + 10, PREVIEW_PIXEL, color);
	}

	private void drawRow(final Row which) {
		Rectangle area = rowArea(which);
		boolean active = which == this.row;
		boolean unlocked = isUnlocked(which);
		Color accent = unlocked ? Color.GREEN : LOCKED;

		if (active)
			drawManager.fillBox(area.x, area.y, area.width, area.height,
					new Color(accent.getRed(), accent.getGreen(),
							accent.getBlue(), 40));
		drawManager.drawBox(area.x, area.y, area.width, area.height,
				active ? accent : BORDER);

		int baseline = area.y + 20;
		Color arrowColor = active ? accent : Color.GRAY;
		drawManager.drawRegularString("<", area.x + 10, baseline, arrowColor);
		drawManager.drawRegularString(">", area.x + area.width - 10
				- drawManager.getRegularStringWidth(">"), baseline,
				arrowColor);
		drawManager.drawSmallString(which.label.toUpperCase(),
				area.x + ARROW_WIDTH + 4, area.y + 18, Color.GRAY);

		String name = choiceName(which);
		Color nameColor = !unlocked ? Color.GRAY
				: active ? Color.GREEN : Color.WHITE;
		drawManager.drawRegularString(name, area.x + area.width / 2
				- drawManager.getRegularStringWidth(name) / 2 + 16, baseline,
				nameColor);
	}

	/** Ship stats, then what the selected row's choice means. */
	private void drawInfo() {
		Rectangle last = rowArea(Row.SKIN);
		int lineHeight = drawManager.getSmallLineHeight();
		int y = last.y + last.height + 22;

		ShipType type = this.availableShips[this.shipIndex];
		double fireRate = type.getBulletsPerShot() * 1000.0
				/ type.getShootingInterval();
		drawStat("SPEED", type.getSpeed() / MAX_SPEED,
				Integer.toString(type.getSpeed()), y);
		drawStat("SHOTS PER SEC", fireRate / MAX_FIRE_RATE,
				String.format("%.2f", fireRate), y + lineHeight + 4);
		drawStat("BULLETS", type.getBulletsPerShot() / 2.0,
				Integer.toString(type.getBulletsPerShot()),
				y + (lineHeight + 4) * 2);

		y += (lineHeight + 4) * 3 + 14;
		String status;
		Color color;
		if (isUnlocked(this.row)) {
			status = choiceName(this.row) + " is equipped";
			color = Color.GREEN;
		} else {
			ShopItem entry = shopEntry(this.row);
			if (entry != null && !ShopCatalog.isOnSale(entry,
					java.time.LocalDate.now()))
				status = "Limited edition - no longer sold";
			else
				status = "Locked - buy it in the Shop"
						+ (entry == null ? "" : " for " + priceText(entry));
			color = LOCKED;
		}
		if (this.row == Row.SKIN
				&& this.availableSkins[this.skinIndex].isLimited()) {
			String tag = "LIMITED EDITION";
			drawManager.drawSmallString(tag, this.width / 2
					- drawManager.getSmallStringWidth(tag) / 2,
					y - lineHeight - 4, new Color(255, 120, 200));
		}
		status = status.toUpperCase();
		drawManager.drawSmallString(status, this.width / 2
				- drawManager.getSmallStringWidth(status) / 2, y, color);

	}

	/**
	 * @param entry
	 *            Shop entry.
	 * @return Its price in every currency it takes, e.g. "50 coins or 25
	 *         diamonds".
	 */
	private static String priceText(final ShopItem entry) {
		StringBuilder text = new StringBuilder();
		for (Currency currency : Currency.values()) {
			int price = PurchaseManager.getInstance().getPrice(entry,
					currency);
			if (price <= 0)
				continue;
			if (text.length() > 0)
				text.append(" or ");
			text.append(price).append(' ').append(currency.getLabel());
		}
		return text.toString();
	}

	private void drawStat(final String label, final double ratio,
			final String value, final int baseline) {
		int barLeft = MARGIN + 120;
		int barWidth = this.width - MARGIN * 2 - 120 - 40;
		drawManager.drawSmallString(label, MARGIN + 4, baseline, Color.GRAY);
		drawManager.drawBox(barLeft, baseline - 8, barWidth, 8, BORDER);
		drawManager.fillBox(barLeft + 1, baseline - 7,
				(int) Math.round((barWidth - 1) * Math.min(1, ratio)), 7,
				Color.GREEN);
		drawManager.drawSmallString(value, barLeft + barWidth + 8, baseline,
				Color.WHITE);
	}

	private static double maxSpeed() {
		double max = 1;
		for (ShipType type : ShipType.values())
			max = Math.max(max, type.getSpeed());
		return max;
	}

	private static double maxFireRate() {
		double max = 0.1;
		for (ShipType type : ShipType.values())
			max = Math.max(max, type.getBulletsPerShot() * 1000.0
					/ type.getShootingInterval());
		return max;
	}
}
