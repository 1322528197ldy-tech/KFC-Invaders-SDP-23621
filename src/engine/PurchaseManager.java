package engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import shop.Currency;
import shop.ShopCatalog;
import shop.ShopCategory;
import shop.ShopItem;
import shop.ShopListener;

/**
 * The one place that knows what the player has bought (GoG - Currency
 * System). The Shop screen buys through here, and other teams can ask what
 * the player owns:
 *
 * <pre>
 * PurchaseManager shop = PurchaseManager.getInstance();
 * shop.purchase(item, Currency.COINS); // SUCCESS, INSUFFICIENT_FUNDS, ...
 * shop.isOwned(item);
 * shop.addListener(new ShopListener() { ... });
 * </pre>
 *
 * <ul>
 * <li>Ships and skins: bought once.</li>
 * <li>Upgrades: bought up to their max level.</li>
 * <li>Skins and upgrades can be paid with coins or diamonds.</li>
 * </ul>
 *
 * Purchases are kept in memory for now; saving them comes next.
 *
 * @author GoG - Currency System
 */
public final class PurchaseManager {

	/** Outcome of a purchase attempt. */
	public enum PurchaseResult {
		/** Price deducted and the entry added. */
		SUCCESS,
		/** Not enough coins or diamonds; nothing changed. */
		INSUFFICIENT_FUNDS,
		/** Ship or skin that is already owned; nothing changed. */
		ALREADY_OWNED,
		/** Upgrade already at its max level; nothing changed. */
		MAX_LEVEL,
		/** The entry cannot be paid with that currency; nothing changed. */
		CURRENCY_NOT_ACCEPTED,
		/** Entry is null or not in the catalog; nothing changed. */
		UNKNOWN_ITEM,
		/** Limited edition that is no longer sold; nothing changed. */
		NOT_ON_SALE
	}

	/** Singleton instance. */
	private static PurchaseManager instance;

	/** Shop id mapped to how many the player has (upgrade level). */
	private final Map<String, Integer> owned;
	/** Teams told about changes. */
	private final List<ShopListener> listeners;
	/** Application logger. */
	private final Logger logger;

	/**
	 * Private constructor.
	 */
	private PurchaseManager() {
		this.logger = Core.getLogger();
		this.owned = new LinkedHashMap<String, Integer>();
		this.listeners = new ArrayList<ShopListener>();
	}

	/**
	 * @return Shared instance of PurchaseManager.
	 */
	public static PurchaseManager getInstance() {
		if (instance == null)
			instance = new PurchaseManager();
		return instance;
	}

	// --- Buying ---

	/**
	 * Buys an entry (or the next level of an upgrade). Checks and pays in
	 * one call; nothing changes unless it returns SUCCESS.
	 *
	 * @param item
	 *            Entry to buy.
	 * @param currency
	 *            Currency to pay with.
	 * @return What happened.
	 */
	public PurchaseResult purchase(final ShopItem item,
			final Currency currency) {
		if (item == null || ShopCatalog.findById(item.getId()) == null
				|| currency == null)
			return PurchaseResult.UNKNOWN_ITEM;
		if (isMaxed(item))
			return item.isRepeatable() ? PurchaseResult.MAX_LEVEL
					: PurchaseResult.ALREADY_OWNED;
		if (!item.accepts(currency))
			return PurchaseResult.CURRENCY_NOT_ACCEPTED;
		if (!ShopCatalog.isOnSale(item, java.time.LocalDate.now()))
			return PurchaseResult.NOT_ON_SALE;

		int price = getPrice(item, currency);
		if (!spend(currency, price))
			return PurchaseResult.INSUFFICIENT_FUNDS;

		int before = getQuantity(item);
		this.owned.put(item.getId(), before + 1);

		this.logger.info("Bought " + item.getId() + " (" + (before + 1) + "/"
				+ item.getMaxLevel() + ") for " + price + " "
				+ currency.getLabel() + ".");
		for (ShopListener listener : listenersCopy())
			listener.onPurchased(item, before + 1);
		return PurchaseResult.SUCCESS;
	}

	/**
	 * Normal price of the next purchase of an entry.
	 *
	 * @param item
	 *            Entry.
	 * @param currency
	 *            Currency to pay with.
	 * @return Price, or 0 if it is maxed, owned, or the currency is not
	 *         accepted.
	 */
	public int getPrice(final ShopItem item, final Currency currency) {
		if (item == null || isMaxed(item) || !item.accepts(currency))
			return 0;
		return item.getPrice(currency, getQuantity(item));
	}

	/**
	 * Checks a price against the right balance without changing anything.
	 *
	 * @param currency
	 *            Currency of the price.
	 * @param price
	 *            Price to check.
	 * @return true if the player can currently afford it.
	 */
	public boolean canAfford(final Currency currency, final int price) {
		if (currency == Currency.DIAMONDS)
			return DiamondManager.getInstance().canAfford(price);
		return CurrencyManager.getInstance().canAfford(price);
	}

	/**
	 * @param item
	 *            Shop entry.
	 * @return true if the player has at least one.
	 */
	public boolean isOwned(final ShopItem item) {
		return getQuantity(item) > 0;
	}

	/**
	 * @param item
	 *            Shop entry.
	 * @return How many the player has: 0 or 1 for ships and skins, the
	 *         level for upgrades.
	 */
	public int getQuantity(final ShopItem item) {
		if (item == null)
			return 0;
		Integer quantity = this.owned.get(item.getId());
		return quantity == null ? 0 : quantity;
	}

	/**
	 * @param item
	 *            Shop entry.
	 * @return true if it cannot be bought again (owned, or max level).
	 */
	public boolean isMaxed(final ShopItem item) {
		return item != null && getQuantity(item) >= item.getMaxLevel();
	}

	// --- Listeners ---

	/**
	 * Tells a team about purchases.
	 *
	 * @param listener
	 *            Listener to add.
	 */
	public void addListener(final ShopListener listener) {
		if (listener != null && !this.listeners.contains(listener))
			this.listeners.add(listener);
	}

	/**
	 * @param listener
	 *            Listener to remove.
	 */
	public void removeListener(final ShopListener listener) {
		this.listeners.remove(listener);
	}

	// --- Internals ---


	private List<ShopListener> listenersCopy() {
		return new ArrayList<ShopListener>(this.listeners);
	}

	/**
	 * Deducts a price from the right balance.
	 *
	 * @param currency
	 *            Currency to pay in.
	 * @param price
	 *            Amount to deduct.
	 * @return true if the balance covered it and it was deducted.
	 */
	private boolean spend(final Currency currency, final int price) {
		if (price <= 0)
			return false;
		if (currency == Currency.DIAMONDS)
			return DiamondManager.getInstance().trySpend(price);
		return CurrencyManager.getInstance().trySpend(price);
	}

}
