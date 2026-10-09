package screen;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.nio.file.Paths;
import java.util.Random;

import engine.DrawManager.SpriteType;
import engine.EndlessRecords;
import engine.EndlessRun;
import engine.EndlessRun.Body;
import engine.EndlessRun.Phase;

/** Menu-accessible endless run, pause/quit confirmation and results/retry flow. */
public final class EndlessScreen extends Screen {
    private final EndlessRun run;
    private final EndlessRecords records;
    private boolean saved;
    private long previousTime;

    public EndlessScreen(int width, int height, int fps) {
        super(width, height, fps);
        run = new EndlessRun(width, height, new Random());
        records = new EndlessRecords(Paths.get("endless-records.properties"));
    }

    @Override
    public void initialize() {
        previousTime = System.nanoTime();
        inputManager.consumeKeyPress(KeyEvent.VK_ESCAPE);
        inputManager.consumeKeyPress(KeyEvent.VK_P);
        inputManager.consumeKeyPress(KeyEvent.VK_ENTER);
        inputManager.consumeKeyPress(KeyEvent.VK_Q);
    }

    @Override
    public int run() {
        super.run();
        return returnCode;
    }

    private boolean down(int key) { return inputManager.isKeyDown(key); }

    @Override
    protected void update() {
        long now = System.nanoTime();
        double dt = (now - previousTime) / 1_000_000_000.0;
        previousTime = now;
        boolean escape = inputManager.consumeKeyPress(KeyEvent.VK_ESCAPE);
        boolean pause = inputManager.consumeKeyPress(KeyEvent.VK_P);
        boolean enter = inputManager.consumeKeyPress(KeyEvent.VK_ENTER);
        boolean quit = inputManager.consumeKeyPress(KeyEvent.VK_Q);
        Phase before = run.getPhase();
        if (before == Phase.OVER) {
            if (escape) { returnCode = 1; isRunning = false; }
            else if (enter) { returnCode = 8; isRunning = false; }
        } else if (before == Phase.PAUSED && quit) {
            run.finish("RUN ENDED");
        } else if (escape || pause) {
            run.togglePause();
        }
        int move = (down(KeyEvent.VK_RIGHT) || down(KeyEvent.VK_D) ? 1 : 0)
                - (down(KeyEvent.VK_LEFT) || down(KeyEvent.VK_A) ? 1 : 0);
        // Never count the frame that opens/closes an overlay as survival time.
        if (before == run.getPhase()) run.update(dt, move, down(KeyEvent.VK_SPACE));
        if (run.getPhase() == Phase.OVER && !saved) {
            records.save(run);
            saved = true;
        }
        drawManager.initDrawing(this);
        drawContents();
        drawManager.completeDrawing(this);
    }

    /** Rendering is separate so the actual UI can also be checked offscreen. */
    void drawContents() {
        text("ENDLESS / WAVE " + run.getWave(), 14, 22, Color.CYAN);
        text("SCORE " + run.getScore(), 14, 44, Color.WHITE);
        text("TIME " + time(run.getSeconds()), width - 125, 22, Color.WHITE);
        text("LIVES " + run.getLives(), width - 125, 44, Color.GREEN);
        Body boss = run.getBoss();
        if (boss == null) {
            text("BOSS IN " + run.getBossSecondsLeft() + "s OR "
                    + Math.max(0, 30 - run.getCycleKills()) + " KILLS", 14, 69, Color.GRAY);
        } else {
            text("BOSS " + (run.getBosses() + 1) + " / HP "
                    + (int) Math.ceil(boss.hp) + "/" + (int) boss.maxHp,
                    14, 69, Color.PINK);
        }
        drawManager.drawHorizontalLine(this, 83);
        for (int i = 0; i < run.getEnemies().size(); i++) {
            Body enemy = run.getEnemies().get(i);
            if (boss != null && !enemy.boss) continue;
            int x = (int) enemy.x, y = (int) enemy.y;
            if (enemy.boss) {
                drawManager.drawBox(x, y, enemy.width, enemy.height, Color.PINK);
                drawManager.drawSprite(SpriteType.EnemyShipSpecial, x + 16, y + 7, Color.PINK);
                drawManager.drawBox(x, y - 7,
                        Math.max(1, (int) (enemy.width * enemy.hp / enemy.maxHp)), 2, Color.RED);
            } else {
                SpriteType sprite = i % 3 == 0 ? SpriteType.EnemyShipA1
                        : i % 3 == 1 ? SpriteType.EnemyShipB1 : SpriteType.EnemyShipC1;
                drawManager.drawSprite(sprite, x, y, Color.WHITE);
            }
        }
        for (Body shot : run.getShots())
            drawManager.drawSprite(run.isPlayerShot(shot) ? SpriteType.Bullet : SpriteType.EnemyBullet,
                    (int) shot.x, (int) shot.y, run.isPlayerShot(shot) ? Color.GREEN : Color.PINK);
        Body player = run.getPlayer();
        drawManager.drawSprite(SpriteType.Ship, (int) player.x, (int) player.y,
                run.isInvulnerable() ? Color.CYAN : run.getLives() == 1 ? Color.PINK : Color.GREEN);
        text(run.getNotice(), 14, height - 74, Color.YELLOW);
        text("DMG +" + run.getDamageUpgrades() + " SPD +" + run.getSpeedUpgrades()
                + " SHOTS " + run.getProjectiles(), 14, height - 18, Color.GRAY);
        Phase phase = run.getPhase();
        if (phase == Phase.READY || phase == Phase.INTERMISSION) {
            overlay(phase == Phase.READY ? "ENDLESS MODE" : "GET READY");
            row("WAVE " + run.getWave() + " / " + run.getCountdown(), 0, Color.CYAN);
            row("A/D or arrows move / SPACE fires", 1, Color.WHITE);
            row("P or ESC pauses / Q ends while paused", 2, Color.GRAY);
            row("10 pts/sec + 100 pts/kill", 3, Color.GRAY);
        } else if (phase == Phase.PAUSED) {
            overlay("PAUSED");
            row("P / ESC to resume", 0, Color.CYAN);
            row("Q to end run and save score", 1, Color.WHITE);
            row("Timers and projectiles are frozen", 2, Color.GRAY);
            row("Best score: " + records.getScore(), 3, Color.GRAY);
        } else if (phase == Phase.OVER) {
            overlay(run.getEndReason());
            row("SCORE " + run.getScore() + " / BEST " + records.getScore(), 0, Color.CYAN);
            row("Time " + time(run.getSeconds()) + " / Kills " + run.getKills(), 1, Color.WHITE);
            row("Wave " + run.getWave() + " / Bosses " + run.getBosses(), 2, Color.WHITE);
            row("Best time " + time(records.getSeconds()) + " / Kills " + records.getKills(), 3, Color.GRAY);
            row("ENTER retry / ESC main menu", 4, Color.GREEN);
            row(records.getStatus(), 5, Color.GRAY);
        }
    }

    private void overlay(String heading) {
        drawManager.drawFadeOverlay(this, 225);
        text(heading, 24, height / 2 - 74, Color.GREEN);
        drawManager.drawHorizontalLine(this, height / 2 - 58);
    }

    private void row(String value, int index, Color color) {
        text(value, 24, height / 2 - 27 + index * 25, color);
    }

    private void text(String value, int x, int y, Color color) {
        drawManager.drawRegularString(value, x, y, color);
    }

    private static String time(long seconds) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }
}
