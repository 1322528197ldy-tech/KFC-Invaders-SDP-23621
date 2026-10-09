package engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Deterministic survival simulation. All clocks advance only during active play. */
public final class EndlessRun {
    public enum Phase { READY, PLAYING, INTERMISSION, PAUSED, OVER }

    /** Geometry shared with rendering; positions use sub-pixel precision. */
    public static final class Body {
        public double x, y;
        public final int width, height;
        public final boolean boss;
        public double hp, maxHp;
        private double vx, vy, damage;

        private Body(double x, double y, int width, int height, boolean boss) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.boss = boss;
            hp = maxHp = 1;
        }
    }

    private final int width, height;
    private final Random random;
    private final List<Body> enemies = new ArrayList<Body>();
    private final List<Body> shots = new ArrayList<Body>();
    private final Body player;
    private Phase phase = Phase.READY, resumePhase;
    private double elapsed, bossClock, countdown = 3, shotClock, enemyShotClock;
    private double invulnerability, moveRemainder, noticeClock;
    private int kills, cycleKills, bosses, wave = 1, lives = 3, projectiles = 1;
    private int direction = 1, damageUpgrades, speedUpgrades;
    private double damage = 1, playerSpeed = 180;
    private String notice = "", endReason = "";

    public EndlessRun(int width, int height, Random random) {
        this.width = width;
        this.height = height;
        this.random = random;
        player = new Body(width / 2.0 - 13, height - 52, 26, 16, false);
        spawnWave();
    }

    /** Bounded fixed steps keep collision detection stable even after a slow frame. */
    public void update(double seconds, int movement, boolean fire) {
        if (!Double.isFinite(seconds) || seconds <= 0) return;
        double remaining = Math.min(seconds, 0.1);
        while (remaining > 0.000001) {
            double step = Math.min(remaining, 1.0 / 120);
            tick(step, Math.max(-1, Math.min(1, movement)), fire);
            remaining -= step;
        }
    }

    private void tick(double dt, int movement, boolean fire) {
        if (phase == Phase.PAUSED || phase == Phase.OVER) return;
        if (phase == Phase.READY || phase == Phase.INTERMISSION) {
            countdown -= dt;
            if (countdown <= 0) phase = Phase.PLAYING;
            return;
        }
        elapsed += dt;
        if (getBoss() == null) bossClock += dt;
        invulnerability = Math.max(0, invulnerability - dt);
        noticeClock = Math.max(0, noticeClock - dt);
        player.x = Math.max(8, Math.min(width - player.width - 8,
                player.x + movement * playerSpeed * dt));
        shotClock -= dt;
        if (fire && shotClock <= 0) {
            for (int i = 0; i < projectiles; i++) {
                Body shot = new Body(player.x + 10 + (i - (projectiles - 1) / 2.0) * 10,
                        player.y - 10, 6, 10, false);
                shot.vy = -420;
                shot.damage = damage;
                shots.add(shot);
            }
            shotClock = 0.30;
        }
        // Trigger immediately, including mid-wave. Remaining enemies resume afterwards.
        if (getBoss() == null && (bossClock >= 90 || cycleKills >= 30)) spawnBoss();
        moveEnemies(dt);
        enemyShotClock -= dt;
        if (enemyShotClock <= 0 && !enemies.isEmpty()) {
            fireEnemyShots();
            enemyShotClock = Math.max(0.32, 1.35 / getDifficulty());
        }
        updateShots(dt);
        if (phase == Phase.OVER) return;
        for (Body enemy : enemies) {
            if (!enemy.boss && enemy.y + enemy.height >= player.y) {
                lives = 0;
                finish("FORMATION BREACHED");
                return;
            }
        }
        if (enemies.isEmpty()) {
            wave++;
            shots.clear();
            spawnWave();
            phase = Phase.INTERMISSION;
            countdown = 2;
            if ((wave - 1) % 3 == 0 && lives < 3) {
                lives++;
                notice = "REPAIR: +1 LIFE";
                noticeClock = 5;
            }
        }
    }

    private void spawnWave() {
        int count = 12 + Math.min(23, (wave - 1) * 2);
        int columns = Math.min(7, 4 + (wave - 1) / 3);
        double left = (width - columns * 40) / 2.0;
        for (int i = 0; i < count; i++) {
            Body enemy = new Body(left + (i % columns) * 40,
                    118 + (i / columns) * 29, 24, 16, false);
            // Health keeps progressing after speed/density reach playable limits.
            enemy.hp = enemy.maxHp = 1 + Math.floor(elapsed / 180) * 0.5;
            enemies.add(enemy);
        }
        direction = 1;
        enemyShotClock = 1.2;
        moveRemainder = 0;
        invulnerability = 1.5;
    }

    private void spawnBoss() {
        Body boss = new Body(width / 2.0 - 32, 120, 64, 28, true);
        boss.hp = boss.maxHp = 18 + bosses * 8.0 + Math.floor(elapsed / 90) * 2;
        boss.vx = 85 + Math.min(85, bosses * 8);
        enemies.add(boss);
        shots.clear();
        enemyShotClock = 1;
        notice = "BOSS INCOMING";
        noticeClock = 2;
    }

    private void moveEnemies(double dt) {
        Body boss = getBoss();
        if (boss != null) {
            boss.x += boss.vx * dt;
            if (boss.x < 16 || boss.x + boss.width > width - 16) {
                boss.x = Math.max(16, Math.min(width - 16 - boss.width, boss.x));
                boss.vx = -boss.vx;
            }
            return; // Freeze the formation during the boss duel.
        }
        moveRemainder += dt;
        double interval = Math.max(0.08, 0.50 / getDifficulty());
        if (moveRemainder < interval) return;
        moveRemainder -= interval;
        boolean turn = false;
        for (Body enemy : enemies)
            if (enemy.x + direction * 8 < 12
                    || enemy.x + enemy.width + direction * 8 > width - 12) turn = true;
        if (turn) direction = -direction;
        for (Body enemy : enemies) {
            enemy.x += direction * 8;
            if (turn) enemy.y += 12;
        }
    }

    private void fireEnemyShots() {
        Body boss = getBoss();
        if (boss != null) {
            // Alternates a dodgeable fan and an aimed shot.
            if (random.nextBoolean()) {
                for (int i = -1; i <= 1; i++) addEnemyShot(boss, i * 75, 155);
            } else {
                double dx = player.x + 13 - (boss.x + boss.width / 2.0);
                double dy = player.y - boss.y;
                double length = Math.hypot(dx, dy);
                addEnemyShot(boss, dx / length * 190, dy / length * 190);
            }
        } else {
            Body enemy = enemies.get(random.nextInt(enemies.size()));
            addEnemyShot(enemy, 0, Math.min(240, 135 * getDifficulty()));
        }
    }

    private void addEnemyShot(Body source, double vx, double vy) {
        Body shot = new Body(source.x + source.width / 2.0 - 3,
                source.y + source.height, 6, 10, false);
        shot.vx = vx;
        shot.vy = vy;
        shots.add(shot);
    }

    private void updateShots(double dt) {
        boolean bossDefeated = false;
        Iterator<Body> iterator = shots.iterator();
        while (iterator.hasNext()) {
            Body shot = iterator.next();
            shot.x += shot.vx * dt;
            shot.y += shot.vy * dt;
            boolean remove = shot.y < 94 || shot.y > height || shot.x < -10 || shot.x > width;
            if (shot.vy < 0) {
                Body boss = getBoss();
                Iterator<Body> targets = enemies.iterator();
                while (targets.hasNext()) {
                    Body target = targets.next();
                    if (boss != null && !target.boss) continue;
                    if (!overlaps(shot, target)) continue;
                    target.hp -= shot.damage;
                    remove = true;
                    if (target.hp <= 0.00001) {
                        targets.remove();
                        kills++;
                        if (target.boss) {
                            bosses++;
                            cycleKills = 0;
                            bossClock = 0;
                            grantReward();
                            bossDefeated = true;
                        } else cycleKills++;
                    }
                    break; // One projectile can damage exactly one enemy.
                }
            } else if (overlaps(shot, player)) {
                remove = true;
                if (invulnerability <= 0) {
                    lives--;
                    invulnerability = 1.5;
                    if (lives == 0) {
                        finish("SHIP LOST");
                        return;
                    }
                }
            }
            if (remove) iterator.remove();
        }
        if (bossDefeated) {
            shots.clear();
            phase = Phase.INTERMISSION;
            countdown = 2;
            invulnerability = 1.5;
            enemyShotClock = 1.2;
        }
    }

    private void grantReward() {
        List<Integer> rewards = new ArrayList<Integer>();
        rewards.add(0);
        if (playerSpeed < 360) rewards.add(1);
        if (projectiles < 5) rewards.add(2);
        int reward = rewards.get(random.nextInt(rewards.size()));
        if (reward == 0) {
            damage *= 1.10;
            damageUpgrades++;
            notice = "UPGRADE: DAMAGE +10%";
        } else if (reward == 1) {
            playerSpeed = Math.min(360, playerSpeed * 1.10);
            speedUpgrades++;
            notice = "UPGRADE: SPEED +10%";
        } else {
            projectiles++;
            notice = "UPGRADE: +1 PROJECTILE";
        }
        noticeClock = 5;
    }

    private static boolean overlaps(Body a, Body b) {
        return a.x < b.x + b.width && a.x + a.width > b.x
                && a.y < b.y + b.height && a.y + a.height > b.y;
    }

    public void togglePause() {
        if (phase == Phase.OVER) return;
        if (phase == Phase.PAUSED) phase = resumePhase;
        else { resumePhase = phase; phase = Phase.PAUSED; }
    }

    public void finish(String reason) { phase = Phase.OVER; endReason = reason; }
    public Phase getPhase() { return phase; }
    public Body getPlayer() { return player; }
    public List<Body> getEnemies() { return Collections.unmodifiableList(enemies); }
    public List<Body> getShots() { return Collections.unmodifiableList(shots); }
    public boolean isPlayerShot(Body shot) { return shot.vy < 0; }
    public Body getBoss() {
        for (Body enemy : enemies) if (enemy.boss) return enemy;
        return null;
    }
    public long getSeconds() { return (long) elapsed; }
    public long getScore() { return getSeconds() * 10 + kills * 100L; }
    public int getKills() { return kills; }
    public int getWave() { return wave; }
    public int getLives() { return lives; }
    public int getBosses() { return bosses; }
    public int getProjectiles() { return projectiles; }
    public int getDamageUpgrades() { return damageUpgrades; }
    public int getSpeedUpgrades() { return speedUpgrades; }
    public int getCycleKills() { return cycleKills; }
    public int getBossSecondsLeft() { return Math.max(0, (int) Math.ceil(90 - bossClock)); }
    public int getCountdown() { return Math.max(1, (int) Math.ceil(countdown)); }
    public double getDifficulty() { return 1 + Math.floor(elapsed / 30) * 0.05; }
    public boolean isInvulnerable() { return invulnerability > 0; }
    public String getNotice() { return noticeClock > 0 ? notice : ""; }
    public String getEndReason() { return endReason; }
}
