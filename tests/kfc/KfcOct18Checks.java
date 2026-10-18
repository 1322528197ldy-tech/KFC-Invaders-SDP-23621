import java.util.*;
import engine.*;
import entity.*;
import screen.*;
import sun.misc.Unsafe;

/** Cross-team regression checks for the October 18 merge resolution. */
public class KfcOct18Checks extends KfcBossChecks {
    static GameScreen stage(int level) throws Exception {
        GameSettings settings = (GameSettings) f(Core.class, "SETTINGS_LEVEL_" + level).get(null);
        GameScreen g = new GameScreen(new GameState(level, 0, 3, 0, 0, 0, level - 1, 25),
                settings, false, 448, 520, 60);
        g.initialize();
        return g;
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        argsDir = args[0];
        new java.io.File(argsDir).mkdirs();
        Unsafe u = (Unsafe) f(Unsafe.class, "theUnsafe").get(null);
        Core.getDrawManager().setFrame((engine.Frame) u.allocateInstance(Offscreen.class));
        Core.getInputManager();
        GameSettings finalSettings = (GameSettings) f(Core.class, "SETTINGS_LEVEL_10").get(null);
        ok(finalSettings.getBaseSpeed() == 8, "Stage 10 keeps agreed boss movement speed 8");
        ok(finalSettings.getShootingFrecuency() == 500, "Stage 10 uses boss initial interval 500ms");

        // Check loaded pixels against the actual ordered graphics stream, not just image dimensions.
        Map<DrawManager.SpriteType, boolean[][]> sprites =
                (Map<DrawManager.SpriteType, boolean[][]>) f(DrawManager.class, "spriteMap").get(null);
        String raw = java.nio.file.Files.readString(java.nio.file.Path.of(args[1]));
        StringBuilder bits = new StringBuilder();
        for (String line : raw.split("\\R"))
            if (!line.trim().startsWith("//"))
                for (char ch : line.toCharArray()) if (ch == '0' || ch == '1') bits.append(ch);
        int offset = 0;
        for (Map.Entry<DrawManager.SpriteType, boolean[][]> e : sprites.entrySet()) {
            boolean match = true;
            for (boolean[] column : e.getValue()) for (boolean pixel : column)
                match &= pixel == (bits.charAt(offset++) == '1');
            ok(match, "sprite bits/order loaded correctly: " + e.getKey());
        }
        ok(offset == bits.length(), "all graphics consumed with no missing or trailing sprite data");

        PlayerProfile profile = (PlayerProfile) get(Core.getAchievementManager(), "playerProfile");
        for (int level : new int[] {5, 10}) {
            GameScreen g = stage(level);
            finishDelay(g);
            // Force the normal meteor schedule ready: a boss must still not pass a null formation.
            MeteorManager meteors = (MeteorManager) get(g, "meteors");
            Object spawner = get(meteors, "spawner");
            f(MeteorSpawner.class, "plannedMeteors").set(spawner, 1);
            f(MeteorSpawner.class, "playStart").set(spawner, 1L);
            f(MeteorSpawner.class, "nextSpawnAt").set(spawner, 0L);
            call(g, "update");
            ok(get(meteors, "meteor") == null, "boss stage skips normal-formation meteor spawn: " + level);
            int before = profile.getFlawlessLevelsCleared();
            Boss boss = (Boss) get(g, "midBoss");
            Set<Bullet> shots = (Set<Bullet>) get(g, "bullets");
            for (int hit = 0; hit < (level == 5 ? 2 : 5); hit++) {
                shots.add(BulletPool.getBullet(boss.getPositionX() + 10, boss.getPositionY() + 4, -6));
                call(g, "manageCollisions");
            }
            call(g, "update");
            ok(g.getGameState().getLevelsCompletedRun() == level,
                    "boss clear increments carried Mantis run-level total: " + level);
            ok(profile.getFlawlessLevelsCleared() == before + 1,
                    "flawless boss clear reaches survival tiers: " + level);
            call(g, "update");
            ok(profile.getFlawlessLevelsCleared() == before + 1
                    && g.getGameState().getLevelsCompletedRun() == level,
                    "boss tier counters do not increment again: " + level);
        }

        GameScreen normal = stage(3);
        Object meteors = get(normal, "meteors");
        Object spawner = get(meteors, "spawner");
        f(MeteorSpawner.class, "plannedMeteors").set(spawner, 1);
        f(MeteorSpawner.class, "playStart").set(spawner, 1L);
        f(MeteorSpawner.class, "nextSpawnAt").set(spawner, 0L);
        finishDelay(normal);
        call(normal, "update");
        ok(get(meteors, "meteor") != null, "normal Stage 3 still spawns the VFX meteor");

        GameScreen restart = stage(5);
        CurrencyManager.getInstance().reset();
        CurrencyManager.getInstance().addCoins(25);
        f(GameScreen.class, "coinsCollectedThisLevel").set(restart, 7);
        call(restart, "restartLevel");
        ok(CurrencyManager.getInstance().getCoins() == 18, "restart refunds seven attempt coins");
        ok(restart.getGameState().getCoinsCollected() == 18,
                "restart also removes refunded coins from Mantis per-run total");
        ok(restart.getGameState().getLevelsCompletedRun() == 4,
                "restart preserves previous cleared-level total");

        AchievementsScreen screen = new AchievementsScreen(448, 520, 60);
        screen.initialize();
        f(AchievementsScreen.class, "currentPage").set(screen, 1);
        call(screen, "draw");
        image("tier-achievements.png");
        ok(!Core.getAchievementManager().getTierAchievements().isEmpty(), "new tier achievement page renders");
        System.out.println("TOTAL " + checks + " October 18 merge checks passed");
    }
}
