package engine;

/**
 * Holds the player's coin balance for the current session (GoG - Currency
 * System). Collected coins from enemy drops are added here, and any screen
 * reads the balance from this single shared instance.
 *
 * This first version keeps the balance in memory only; saving it to disk
 * is added in the next step.
 *
 * @author GoG - Currency System
 */
public final class CurrencyManager {

	/** Singleton instance. */
	private static CurrencyManager instance;

	/** Current coin balance. Never negative. */
	private int coins;

	/**
	 * Private constructor, starts from zero.
	 */
	private CurrencyManager() {
		this.coins = 0;
	}

	/**
	 * Controls access to the currency manager.
	 *
	 * @return shared instance of CurrencyManager.
	 */
	public static CurrencyManager getInstance() {
		if (instance == null)
			instance = new CurrencyManager();
		return instance;
	}

	/**
	 * Adds coins to the current balance, e.g. when the player collects a
	 * dropped coin.
	 *
	 * @param amount
	 *            Amount of coins to add. Ignored if not positive.
	 */
	public void addCoins(final int amount) {
		if (amount > 0)
			this.coins += amount;
	}

	/**
	 * @return current coin balance.
	 */
	public int getCoins() {
		return this.coins;
	}

	/**
	 * Attempts to spend coins, e.g. for a shop purchase. Checks-then-spends
	 * in one call so a caller never needs to call getCoins() first.
	 *
	 * @param amount
	 *            Amount of coins to spend. Must be positive.
	 * @return true if the balance had enough coins and the amount was
	 *         deducted; false if funds were insufficient and nothing
	 *         changed.
	 */
	public boolean trySpend(final int amount) {
		if (amount <= 0)
			throw new IllegalArgumentException("amount must be positive");
		if (this.coins < amount)
			return false;
		this.coins -= amount;
		return true;
	}

	/**
	 * Resets the balance to zero. Useful for tests.
	 */
	public void reset() {
		this.coins = 0;
	}
}
