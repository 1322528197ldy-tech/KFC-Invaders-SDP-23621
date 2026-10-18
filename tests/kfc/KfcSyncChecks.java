import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import engine.*;
import entity.*;
import screen.*;
import shop.*;
import item.ItemSystem;
import item.ItemAPI;
import sun.misc.Unsafe;

/** Regression checks for the October 16 upstream merge. Use temporary classes/save directories. */
public class KfcSyncChecks extends KfcBossChecks {
    static GameScreen stage(int level) throws Exception {
        GameSettings settings = (GameSettings) f(Core.class, "SETTINGS_LEVEL_" + level).get(null);
        GameScreen screen = new GameScreen(new GameState(level, 20, 3, 0, 0, 0, 0, 0),
                settings, false, 448, 520, 60);
        screen.initialize();
        return screen;
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Unsafe unsafe = (Unsafe) f(Unsafe.class, "theUnsafe").get(null);
        Core.getDrawManager().setFrame((engine.Frame) unsafe.allocateInstance(Offscreen.class));
        Core.getInputManager();
        PurchaseManager purchases = PurchaseManager.getInstance();
        Map<String, Integer> owned = (Map<String, Integer>) get(purchases, "owned");
        owned.put(ShopCatalog.findByRef(ShopCategory.SKIN, Skin.CRIMSON_HULL.name()).getId(), 1);
        f(PurchaseManager.class, "selectedSkin").set(purchases, Skin.CRIMSON_HULL);
        owned.put(ShopCatalog.findByRef(ShopCategory.SHIP, Ship.ShipType.FAST_MOVE.name()).getId(), 1);
        Core.setSelectedShipType(Ship.ShipType.FAST_MOVE);

        for (int level : new int[] {1, 5, 10}) {
            GameScreen screen = stage(level);
            Ship ship = (Ship) get(screen, "ship");
            ok(ship.getType() == Ship.ShipType.FAST_MOVE, "selected purchased ship Stage " + level);
            ok(get(screen, "skin") == Skin.CRIMSON_HULL, "selected purchased skin Stage " + level);
            call(screen, "update");
            f(GameScreen.class, "paused").set(screen, true);
            call(screen, "draw");
            ok((boolean) get(screen, "paused"), "pause menu renders Stage " + level);
            CurrencyManager wallet = CurrencyManager.getInstance();
            wallet.reset();
            wallet.addCoins(17);
            f(GameScreen.class, "coinsCollectedThisLevel").set(screen, 7);
            call(screen, "restartLevel");
            ok(wallet.getCoins() == 10, "restart refunds attempt coins Stage " + level);
            ok((int) get(screen, "score") == 20, "restart restores score Stage " + level);
            ok(!(boolean) get(screen, "paused"), "restart resumes Stage " + level);
            if (level != 1)
                ok(get(screen, "midBoss") != null && !((Boss) get(screen, "midBoss")).isDestroyed(),
                        "restart recreates living boss Stage " + level);
        }

        GameScreen screen = stage(1);
        ItemSystem items = (ItemSystem) get(screen, "items");
        items.update((Ship) get(screen, "ship"), true);
        items.api().tryGrant(new ItemAPI.GrantRequest("sync-score", "score_boost",
                ItemAPI.GrantSource.REWARD, ItemAPI.GrantTiming.NOW));
        items.useSlot(0);
        EnemyShip enemy = ((EnemyShipFormation) get(screen, "enemyShipFormation")).iterator().next();
        int bonus = items.scoreBonus(enemy.getPointValue());
        ok(bonus > 0, "score boost activated through item API");
        ((Set<Bullet>) get(screen, "bullets")).add(BulletPool.getBullet(
                enemy.getPositionX() + 8, enemy.getPositionY() + 4, -6));
        call(screen, "manageCollisions");
        ok((int) get(screen, "score") == 20 + enemy.getPointValue() + bonus,
                "normal kill retains boosted score");
        ok(((java.util.List<?>) get(screen, "scoreBoostPopups")).size() == 1,
                "normal kill retains delayed boost popup after conflict resolution");

        boolean[] keys = (boolean[]) f(InputManager.class, "keys").get(null);
        Screen[] menus = {new SettingsScreen(448, 520, 60), new ShopScreen(448, 520, 60),
                new ShipSelectScreen(448, 520, 60), new AchievementsScreen(448, 520, 60)};
        for (Screen menu : menus) {
            Arrays.fill(keys, false);
            menu.initialize();
            finishDelay(menu);
            call(menu, "update");
            call(menu, "draw");
            f(Screen.class, "isRunning").set(menu, true);
            keys[KeyEvent.VK_ESCAPE] = true;
            finishDelay(menu);
            call(menu, "update");
            ok(!(boolean) f(Screen.class, "isRunning").get(menu)
                    && (int) f(Screen.class, "returnCode").get(menu) == 1,
                    menu.getClass().getSimpleName() + " renders and ESC returns to menu");
        }
        Arrays.fill(keys, false);
        Core.setSelectedShipType(Ship.ShipType.STANDARD);
        System.out.println("TOTAL " + checks + " sync checks passed");
    }
}
