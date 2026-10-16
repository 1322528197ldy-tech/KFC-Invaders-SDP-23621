MidBoss Class Analysis

This document analyzes the operations, constants, and behaviors of the MidBoss class.

Important Notes (Must Read Before Modifying)

1. Boss Movement Speed (baseSpeed)

The initial movement speed is derived directly from GameSettings.getBaseSpeed().

Modifying baseSpeed in global settings will affect the MidBoss speed proportionally.

2. Boss Shooting Rate (Cooldown)

Shooting frequency dynamically speeds up as currentShootingFrequency decreases by DECLINE_RATE after every shot.

Capped at MIN_SHOOTING_RATE to prevent infinite speed scaling.

Detailed Code & Component Explanation

1. Constants & State Variables

private static final int MID_BOSS_POINTS = 2500;    // Bonus score on destruction
private static final int MID_BOSS_POINTS_HIT = 150; // Score granted per bullet hit
private static final int MID_BOSS_HP = 2;          // Total health points
private static final int MIN_SHOOTING_RATE = 350;    // Minimum cooldown limit (ms)
private static final int DECLINE_RATE = 30;          // Cooldown reduction per shot
private static int BULLET_SPEED = 4;                 // Bullet velocity


2. Constructor & Initialization

public MidBoss(final int positionX, final int positionY, final SpriteType spriteType)


Sets up initial coordinates, sprite type, point rewards, and health points (MID_BOSS_HP = 2).

3. Collision & Damage (manageCollisionsBoss, takeDamage)

public final int manageCollisionsBoss(final Bullet bullet)


Reduces HP on bullet collision (takeDamage()).

Awards hit score (MID_BOSS_POINTS_HIT) and kill bonus (MID_BOSS_POINTS) if destroyed.

4. Movement Logic (moveBoss)

public final void moveBoss(final int screenWidth, final int screenHeight, final GameSettings gameSettings)


Moves standard horizontal/vertical bouncing patterns within screen bounds.

5. Shooting Logic (shoot)

public final boolean shoot(final Set<Bullet> bullets, final GameSettings gameSettings)


Fires standard single bullets at intervals controlled by shootCooldown.