package screen;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import engine.CurrencyManager;
import engine.DiamondManager;
import engine.PurchaseManager;
import shop.Currency;
import shop.ShopCatalog;
import shop.ShopCategory;
import shop.ShopItem;

/**
 * The shop (GoG - Currency System). Three tabs: Ships, Skins and Upgrades,
 * each listing what is for sale with its coin and diamond prices. Prices
 * come from {@link PurchaseManager}, so they show the next upgrade level.
 *
 * Keyboard: left/right switch tabs, up/down select, esc back.
 *
 * @author GoG - Currency System
 */
public class ShopScreen extends Screen {

	/** The shop's tabs, each with its own accent colour. */
	private enum Tab {
		/** Player ships. */
		SHIPS("Ships", Color.GREEN),
		/** Ship skins. */
		SKINS("Skins", Color.CYAN),
		/** Item team upgrades. */
		UPGRADES("Upgrades", new Color(255, 200, 0));

		/** Text on the tab. */
		private final String title;
		/** Accent colour of the tab and its selection. */
		private final Color color;

		Tab(final String title, final Color color) {
			this.title = title;
			this.color = color;
		}
	}

	/** One row in the list. */
	private static final class Entry {
		/** Entry for sale. */
		private final ShopItem item;

		Entry(final ShopItem item) {
			this.item = item;
		}

		/**
		 * @param currency
		 *            Currency to pay with.
		 * @return Price of the next purchase on this row, 0 if not sold for
		 *         that currency or already maxed.
		 */
		int price(final Currency currency) {
			return PurchaseManager.getInstance().getPrice(this.item,
					currency);
		}
	}

	/** Space between the screen edge and the panels. */
	private static final int MARGIN = 16;
	/** Height of the tab bar. */
	private static final int TAB_HEIGHT = 20;
	/** Height of one row in the list. */
	private static final int ROW_HEIGHT = 30;
	/** Width of the list on the left. */
	private static final int LIST_WIDTH = 188;
	/** Gap between the list and the detail panel. */
	private static final int PANEL_GAP = 8;
	/** Width of the scroll arrows column. */
	private static final int SCROLL_WIDTH = 12;
	/** Colour of prices the player cannot afford. */
	private static final Color TOO_EXPENSIVE = new Color(255, 70, 70);
	/** Colour of unselected panel borders. */
	private static final Color BORDER = new Color(70, 70, 70);
	/** Colour of the LIMITED tag. */
	private static final Color LIMITED = new Color(255, 120, 200);

	/** Keys watched for presses (not holds). */
	private static final int[] KEYS = { KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT,
			KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_A, KeyEvent.VK_D,
			KeyEvent.VK_W, KeyEvent.VK_S, KeyEvent.VK_ESCAPE };

	/** Tab shown. */
	private Tab tab = Tab.SHIPS;
	/** Rows of the tab shown. */
	private List<Entry> entries = new ArrayList<Entry>();
	/** Selected row. */
	private int selected;
	/** First row shown, for scrolling. */
	private int scroll;
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
	public ShopScreen(final int width, final int height, final int fps) {
		super(width, height, fps);
		this.returnCode = 1;
		showTab(Tab.SHIPS);
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

		if (this.inputDelay.checkFinished())
			handleKeys();
		draw();
	}

	// --- Input ---

	/**
	 * @param key
	 *            Key code from {@link #KEYS}.
	 * @return true if the key went down this frame.
	 */
	private boolean pressed(final int key) {
		for (int i = 0; i < KEYS.length; i++)
			if (KEYS[i] == key)
				return this.inputManager.isKeyDown(key)
						&& !this.keysWereDown[i];
		return false;
	}

	/** Reacts to key presses. */
	private void handleKeys() {
		if (pressed(KeyEvent.VK_ESCAPE)) {
			this.isRunning = false;
		} else if (pressed(KeyEvent.VK_LEFT) || pressed(KeyEvent.VK_A)) {
			showTab(Tab.values()[Math.floorMod(this.tab.ordinal() - 1,
					Tab.values().length)]);
		} else if (pressed(KeyEvent.VK_RIGHT) || pressed(KeyEvent.VK_D)) {
			showTab(Tab.values()[(this.tab.ordinal() + 1)
					% Tab.values().length]);
		} else if (pressed(KeyEvent.VK_UP) || pressed(KeyEvent.VK_W)) {
			select(this.selected - 1);
		} else if (pressed(KeyEvent.VK_DOWN) || pressed(KeyEvent.VK_S)) {
			select(this.selected + 1);
		}
		for (int i = 0; i < KEYS.length; i++)
			this.keysWereDown[i] = this.inputManager.isKeyDown(KEYS[i]);
	}


	/**
	 * Switches tab and selects its first row.
	 *
	 * @param newTab
	 *            Tab to show.
	 */
	private void showTab(final Tab newTab) {
		this.tab = newTab;
		this.entries = buildEntries(newTab);
		this.selected = 0;
		this.scroll = 0;
	}

	/**
	 * Selects a row, keeping it inside the list and on screen.
	 *
	 * @param row
	 *            Row to select.
	 */
	private void select(final int row) {
		if (this.entries.isEmpty())
			return;
		this.selected = Math.max(0, Math.min(this.entries.size() - 1, row));
		if (this.selected < this.scroll)
			this.scroll = this.selected;
		if (this.selected >= this.scroll + visibleRows())
			this.scroll = this.selected - visibleRows() + 1;
	}

	/**
	 * @param shown
	 *            Tab to list.
	 * @return Its rows, in shop order.
	 */
	private static List<Entry> buildEntries(final Tab shown) {
		List<Entry> result = new ArrayList<Entry>();
		switch (shown) {
		case SHIPS:
			addAll(result, ShopCatalog.getByCategory(ShopCategory.SHIP));
			break;
		case SKINS:
			addAll(result, ShopCatalog.getByCategory(ShopCategory.SKIN));
			break;
		default:
			addAll(result, ShopCatalog.getByCategory(ShopCategory.UPGRADE));
			break;
		}
		return result;
	}

	private static void addAll(final List<Entry> result,
			final List<ShopItem> items) {
		for (ShopItem item : items)
			result.add(new Entry(item));
	}


	// --- Layout ---

	private int balanceBaseline() {
		return this.height / 8 + 28;
	}

	private int tabTop() {
		return balanceBaseline() + 10;
	}

	private int bodyTop() {
		return tabTop() + TAB_HEIGHT + 8;
	}

	private int bodyBottom() {
		return this.height - 70;
	}

	private int visibleRows() {
		return Math.max(1, (bodyBottom() - bodyTop()) / ROW_HEIGHT);
	}

	private Rectangle tabArea(final Tab each) {
		int count = Tab.values().length;
		int tabWidth = (this.width - MARGIN * 2) / count;
		return new Rectangle(MARGIN + each.ordinal() * tabWidth, tabTop(),
				tabWidth, TAB_HEIGHT);
	}

	private Rectangle rowArea(final int row) {
		return new Rectangle(MARGIN, bodyTop() + row * ROW_HEIGHT,
				LIST_WIDTH - SCROLL_WIDTH - 2, ROW_HEIGHT - 3);
	}

	private Rectangle scrollUpArea() {
		return new Rectangle(MARGIN + LIST_WIDTH - SCROLL_WIDTH, bodyTop(),
				SCROLL_WIDTH, ROW_HEIGHT);
	}

	private Rectangle scrollDownArea() {
		return new Rectangle(MARGIN + LIST_WIDTH - SCROLL_WIDTH,
				bodyTop() + (visibleRows() - 1) * ROW_HEIGHT, SCROLL_WIDTH,
				ROW_HEIGHT);
	}


	// --- Drawing ---

	/**
	 * Draws the elements associated with the screen.
	 */
	private void draw() {
		this.drawManager.initDrawing(this);
		this.drawManager.drawScreenTitle(this, MenuItem.SHOP.getTitle());
		drawBalances();
		drawTabs();
		drawList();
		this.drawManager.drawKeyHints(this,
				"left/right tab | up/down select | esc back");
		this.drawManager.completeDrawing(this);
	}

	/** Coins on the left, diamonds on the right, under the title. */
	private void drawBalances() {
		int baseline = balanceBaseline();
		String coins = Integer.toString(CurrencyManager.getInstance()
				.getCoins());
		String diamonds = Integer.toString(DiamondManager.getInstance()
				.getDiamonds());
		this.drawManager.drawCurrencyIcon(MARGIN, baseline, false, 12);
		this.drawManager.drawRegularString(coins, MARGIN + 18, baseline,
				Color.WHITE);
		int diamondsWidth = this.drawManager.getRegularStringWidth(diamonds);
		int right = this.width - MARGIN;
		this.drawManager.drawCurrencyIcon(right - diamondsWidth - 18,
				baseline, true, 12);
		this.drawManager.drawRegularString(diamonds, right - diamondsWidth,
				baseline, Color.WHITE);
	}

	private void drawTabs() {
		for (Tab each : Tab.values()) {
			Rectangle area = tabArea(each);
			boolean active = each == this.tab;
			if (active)
				this.drawManager.fillBox(area.x, area.y, area.width,
						area.height, tint(each.color));
			this.drawManager.drawBox(area.x, area.y, area.width - 1,
					area.height - 1, active ? each.color : BORDER);
			String title = each.title.toUpperCase();
			this.drawManager.drawSmallString(title, area.x + (area.width
					- this.drawManager.getSmallStringWidth(title)) / 2,
					area.y + 14, active ? each.color : Color.GRAY);
		}
	}

	private void drawList() {
		if (this.entries.isEmpty()) {
			this.drawManager.drawSmallString("Nothing for sale here.",
					MARGIN + 6, bodyTop() + 18, Color.GRAY);
			return;
		}
		PurchaseManager purchases = PurchaseManager.getInstance();
		for (int row = 0; row < visibleRows()
				&& this.scroll + row < this.entries.size(); row++) {
			int index = this.scroll + row;
			Entry entry = this.entries.get(index);
			Rectangle area = rowArea(row);
			boolean isSelected = index == this.selected;
			boolean soldOut = purchases.isMaxed(entry.item);

			if (isSelected)
				this.drawManager.fillBox(area.x, area.y, area.width,
						area.height, tint(this.tab.color));
			this.drawManager.drawBox(area.x, area.y, area.width - 1,
					area.height - 1, isSelected ? this.tab.color : BORDER);

			Color nameColor = isSelected ? this.tab.color
					: soldOut ? Color.GRAY : Color.WHITE;
			this.drawManager.drawSmallString(entry.item.getName()
					.toUpperCase(), area.x + 6, area.y + 12, nameColor);
			String tag = null;
			Color tagColor = Color.YELLOW;
			if (ShopCatalog.limitedSkin(entry.item) != null) {
				tag = "LIMITED";
				tagColor = LIMITED;
			}
			if (tag != null)
				this.drawManager.drawSmallString(tag, area.x + area.width - 6
						- this.drawManager.getSmallStringWidth(tag),
						area.y + 12, tagColor);

			int baseline = area.y + 24;
			if (soldOut) {
				this.drawManager.drawSmallString(entry.item.isRepeatable()
						? "MAX LEVEL" : "OWNED", area.x + 6, baseline,
						Color.GRAY);
			} else if (!ShopCatalog.isOnSale(entry.item, LocalDate.now())) {
				this.drawManager.drawSmallString("ENDED", area.x + 6,
						baseline, Color.GRAY);
			} else {
				int x = drawPrice(entry, Currency.COINS, area.x + 6,
						baseline);
				drawPrice(entry, Currency.DIAMONDS, x, baseline);
			}
			int quantity = purchases.getQuantity(entry.item);
			if (entry.item.isRepeatable() && quantity > 0) {
				String level = "LV " + quantity + "/"
						+ entry.item.getMaxLevel();
				this.drawManager.drawSmallString(level, area.x + area.width
						- 6 - this.drawManager.getSmallStringWidth(level),
						baseline, Color.GRAY);
			}
		}

		// Scroll arrows, only when the list does not fit.
		if (this.scroll > 0)
			drawArrow(scrollUpArea(), "^");
		if (this.scroll + visibleRows() < this.entries.size())
			drawArrow(scrollDownArea(), "v");
	}

	private void drawArrow(final Rectangle area, final String arrow) {
		this.drawManager.drawSmallString(arrow, area.x + 3,
				area.y + area.height / 2 + 4, this.tab.color);
	}

	/**
	 * Draws a currency icon and price, red when the player cannot afford it.
	 * Draws nothing if the row is not sold for that currency.
	 *
	 * @param entry
	 *            Row to draw the price of.
	 * @param currency
	 *            Currency of the price.
	 * @param left
	 *            Left edge.
	 * @param baseline
	 *            Text baseline.
	 * @return Left edge for whatever is drawn next.
	 */
	private int drawPrice(final Entry entry, final Currency currency,
			final int left, final int baseline) {
		int price = entry.price(currency);
		if (price <= 0)
			return left;
		boolean affordable = PurchaseManager.getInstance().canAfford(
				currency, price);
		String text = Integer.toString(price);
		this.drawManager.drawCurrencyIcon(left, baseline,
				currency == Currency.DIAMONDS, 8);
		this.drawManager.drawSmallString(text, left + 12, baseline,
				affordable ? Color.WHITE : TOO_EXPENSIVE);
		return left + 12 + this.drawManager.getSmallStringWidth(text) + 12;
	}




	/**
	 * @param color
	 *            Accent colour.
	 * @return A dark, see-through version for filled highlights.
	 */
	private static Color tint(final Color color) {
		return new Color(color.getRed(), color.getGreen(), color.getBlue(),
				45);
	}
}
