package shop;

/**
 * Lets another team react when the player buys or changes something,
 * instead of checking every frame. Register it with
 * engine.PurchaseManager.getInstance().addListener(...).
 *
 * Every method has an empty default, so implement only what you need.
 * Called on the game thread, after the change.
 *
 * @author GoG - Currency System
 */
public interface ShopListener {

	/**
	 * Something was bought.
	 *
	 * @param item
	 *            Entry that was bought.
	 * @param level
	 *            How many the player has now: 1 for ships and skins, the new
	 *            level for upgrades.
	 */
	default void onPurchased(final ShopItem item, final int level) {
	}
}
