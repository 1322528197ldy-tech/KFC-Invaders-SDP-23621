Boss Class Analysis (FinalBoss & MidBoss)

This document analyzes the operations and code blocks of the FinalBoss and MidBoss classes. Both classes function almost identically in structure, differing mainly in constant values (such as HP, points, and minimum shooting rates).

Important Notes (Must Read Before Modifying)

Boss Movement Speed (baseSpeed)

The initial movement speed of the boss is taken directly from the baseSpeed value in Core's GameSettings.

Crucial: If you increase the baseSpeed in the game settings, the boss's movement speed will increase proportionately. Please keep this in mind when adjusting the game's difficulty.

Boss Shooting Rate (Cooldown)

The boss's bullet firing frequency decreases over time (meaning the firing rate gets faster) to dynamically increase the difficulty.

However, it does not get infinitely faster; there is a hard limit set by the MIN_SHOOTING_RATE constant to cap the maximum firing speed.

The initial firing rate is set using GameSettings.getShootingFrecuency(), which can be adjusted to change the initial difficulty.

Score Adjustments (Hits & Destruction)

You can easily balance the game's scoring system by adjusting the constant values at the top of the classes.

Points per hit: Modify FINAL_BOSS_POINTS_HIT (or MID_BOSS_POINTS_HIT) to change the score granted every time a player's bullet collides with the boss.

Points per kill: Modify FINAL_BOSS_POINTS (or MID_BOSS_POINTS) to change the massive bonus score awarded upon fully destroying the boss.

Detailed Code Explanation

1. Constants & Variables

private static final int FINAL_BOSS_POINTS = 5000; // Points on kill
private static final int FINAL_BOSS_POINTS_HIT = 300; // Points per hit
private static final int FINAL_BOSS_HP = 5; // (MidBoss has 2)
private static final int MIN_SHOOTING_RATE = 250; // The minimum cooldown time limit
private static final int DECLINE_RATE = 50; // Cooldown reduction per shot
private static int BULLET_SPEED = 5; // Speed of the bullet


Defines the boss's HP, score values (both hit and kill), and shooting limits. MIN_SHOOTING_RATE is the cap for how fast the boss can shoot, and DECLINE_RATE determines how much faster it gets after each shot.

2. Constructor

public FinalBoss(final int positionX, final int positionY, final SpriteType spriteType)


Initializes the boss object with its starting position, sprite image, HP, point value, and animation cooldown.

3. Update (Animation)

public final void update()


Swaps the sprite image (e.g., FinalBoss1 ↔ FinalBoss2) every 500ms when the animationCooldown finishes. This creates a continuous moving or glowing animation effect for the boss.

4. Taking Damage & Destruction (takeDamage, destroy)

public final void takeDamage() {
    this.hp--;
    if (this.hp <= 0) {
        this.destroy();
    }
}


Decreases HP by 1 when hit by a player's bullet. If HP reaches 0 or below, it calls the destroy() method, sets isDestroyed = true, and changes the sprite to an explosion.

5. Collision Management (manageCollisionsBoss)

public final int manageCollisionsBoss(final Bullet bullet)


Handles collisions with player bullets. Returns 0 if the boss is already destroyed.

Otherwise, it grants hit points (FINAL_BOSS_POINTS_HIT). If this hit destroys the boss, it adds the massive kill points (getPointValue()) to the returned score.

6. Movement Logic (moveBoss)

public final void moveBoss(final int screenWidth, final int screenHeight, final GameSettings gameSettings)


Initial Speed: Assigns gameSettings.getBaseSpeed() to both speedX and speedY. (This means any increase in base speed directly speeds up the boss).

Bouncing: Reverses direction (speedX = -speedX or speedY = -speedY) when the boss hits the left/right screen edges or the specified top/bottom boundaries, making it bounce smoothly around the screen.

7. Shooting Logic (shoot)

public final boolean shoot(final Set<Bullet> bullets, final GameSettings gameSettings)


Retrieves the initial shooting frequency from gameSettings.

Every time a bullet is fired, currentShootingFrequency decreases by DECLINE_RATE, making the next shot arrive sooner.

Prevents the firing rate from dropping below the MIN_SHOOTING_RATE limit.

Spawns a new bullet using BulletPool and adds it to the game screen.