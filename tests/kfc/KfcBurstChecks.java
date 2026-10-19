import java.util.HashSet;
import java.util.Set;
import engine.Cooldown;
import engine.DrawManager.SpriteType;
import engine.GameSettings;
import entity.Bullet;
import entity.FinalBoss;

/** Deterministic checks for the Stage 10 single-shot / three-projectile pattern. */
public class KfcBurstChecks extends KfcBossChecks {
    public static void main(String[] args) throws Exception {
        GameSettings settings = new GameSettings(1, 1, 8, 500);
        for (int target = 3; target <= 5; target++) {
            FinalBoss boss = new FinalBoss(100, 80, SpriteType.FinalBoss1);
            f(FinalBoss.class, "targetShotsForBurst").set(boss, target);
            Set<Bullet> bullets = new HashSet<>();
            ok(!boss.shoot(bullets, settings), "initial cooldown target " + target);
            for (int shot = 1; shot <= target; shot++) {
                bullets.clear();
                f(Cooldown.class, "time").set(get(boss, "shootCooldown"), 0L);
                ok(boss.shoot(bullets, settings), "fires cycle " + shot + " target " + target);
                ok(bullets.size() == (shot == target ? 3 : 1),
                        "correct projectile count cycle " + shot + " target " + target);
            }
            Set<Integer> positions = new HashSet<>();
            for (Bullet bullet : bullets) {
                ok(bullet.getPositionX() + bullet.getWidth() / 2
                        == boss.getPositionX() + boss.getWidth() / 2,
                        "burst uses one vertical lane");
                ok(bullet.getSpeed() > 0, "burst travels toward player");
                positions.add(bullet.getPositionY());
            }
            int muzzle = boss.getPositionY() + boss.getHeight();
            ok(positions.size() == 3 && positions.contains(muzzle)
                    && positions.contains(muzzle - 15) && positions.contains(muzzle - 30),
                    "three-projectile train has 15px spacing");
            int next = (int) get(boss, "targetShotsForBurst");
            ok(next >= 3 && next <= 5 && (int) get(boss, "shotCount") == 0,
                    "burst resets random 3–5 cycle counter");
        }
        FinalBoss boss = new FinalBoss(100, 80, SpriteType.FinalBoss1);
        Set<Bullet> bullets = new HashSet<>();
        boss.shoot(bullets, settings);
        for (int shot = 0; shot < 20; shot++) {
            f(Cooldown.class, "time").set(get(boss, "shootCooldown"), 0L);
            boss.shoot(bullets, settings);
        }
        ok((int) get(boss, "currentShootingFrequency") == 250, "final boss cooldown floor is 250ms");
        ok(boss.getWidth() == 48 && boss.getHeight() == 32, "final boss hitbox matches scaled sprite");
        int score = 0;
        for (int hit = 1; hit <= 5; hit++) {
            score += boss.manageCollisionsBoss(null);
            ok(boss.isDestroyed() == (hit == 5), "final boss requires exactly five hits: " + hit);
        }
        ok(score == 6500, "all hit points plus one defeat bonus");
        ok(boss.manageCollisionsBoss(null) == 0, "no score after defeat");
        int count = bullets.size();
        f(Cooldown.class, "time").set(get(boss, "shootCooldown"), 0L);
        ok(!boss.shoot(bullets, settings) && bullets.size() == count, "no firing after defeat");
        System.out.println("TOTAL " + checks + " burst checks passed");
    }
}
