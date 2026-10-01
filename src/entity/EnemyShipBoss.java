package entity;

import java.awt.Color;

import engine.Cooldown;
import engine.Core;
import engine.DrawManager.SpriteType;

public class EnemyShipBoss extends Entity {

    /** Point value of a Mid Boss. */
    private static final int MID_BOSS_POINTS = 1000;


    /** Cooldown between sprite changes. */
    private Cooldown animationCooldown;
	/** Checks if the ship has been hit by a bullet. */
    private boolean isDestroyed;
    /** Values of the ship, in points, when destroyed. */
	private int pointValue;


	/** To calculate the health points. */
	private int hp;
	/** The health points of the Mid Boss. */
	private static final int MID_BOSS_HP = 2;
	/** The health points of the Final Boss. */
	private static final int FINAL_BOSS_HP = 2;


	/** Point value awarded for hitting the Mid Boss. */
	private static final int MID_BOSS_POINTS_HIT = 100;
	/** Point value awarded for hitting the Final Boss. */
	private static final int FINAL_BOSS_POINTS_HIT = 250;

    /**
	 * Constructor for MidBoss Ship.
	 * 
	 * @param positionX
	 *            Initial position of the ship in the X axis.
	 * @param positionY
	 *            Initial position of the ship in the Y axis.
	 * @param spriteType
	 *            Sprite type, image corresponding to the ship.
	 */
    public EnemyShipBoss(final int positionX, final int positionY,
			final SpriteType spriteType) {
		super(positionX, positionY, 16 * 2, 10 * 2, Color.ORANGE);

		this.spriteType = spriteType;
		this.animationCooldown = Core.getCooldown(500);
		this.isDestroyed = false;

		switch (this.spriteType) {
		case MidBoss_1:
		case MidBoss_2:
			this.pointValue = MID_BOSS_POINTS;
			this.hp = MID_BOSS_HP;
			break;
		default:
			this.pointValue = 0;
			break;
		}
	}

    /**
	 * Getter for the score bonus if this ship is destroyed.
	 * 
	 * @return Value of the ship.
	 */
	public final int getPointValue() {
		return this.pointValue;
	}

	/**
	 * Updates attributes, mainly used for animation purposes.
	 */
	public final void update() {
		// Toggle boss sprite when animation cooldown expires to create an animation effect.
		if (this.animationCooldown.checkFinished()) {
			this.animationCooldown.reset();

			switch (this.spriteType) {
			case MidBoss_1:
				this.spriteType = SpriteType.MidBoss_2;
				break;
			case MidBoss_2:
				this.spriteType = SpriteType.MidBoss_1;
				break;
			default:
				break;
            }
        }
    }

	/**
	 * Checks if the ship has been destroyed.
	 * 
	 * @return True if the ship has been destroyed.
	 */
	public final boolean isDestroyed() {
		return this.isDestroyed;
	}

	/**
	 * Destroys the ship, causing an explosion.
	 */
	public final void destroy() {
		this.isDestroyed = true;
		this.spriteType = SpriteType.Explosion;
	}

	/**
	 *  Applies damage to the ship, reducing its health points. 
	 *  If health points reach zero, the ship is destroyed.
	 */
	public final void takeDamage() {
		this.hp--;
		if (this.hp <= 0) {
			this.destroy();
		}
	}

	/**
	 * Manages the collision between a bullet and the boss ship.
	 * 
	 * @param bullet
	 *            The bullet that collided with the boss ship.
	 * @return The score earned from the collision.
	 */
	public final int manageCollisionsBoss(final Bullet bullet) {
        if (this.isDestroyed()) {
            return 0;
        }

        this.takeDamage();

        int scoreEarned;
        if (this.spriteType == SpriteType.MidBoss_1 || this.spriteType == SpriteType.MidBoss_2) {
            scoreEarned = MID_BOSS_POINTS_HIT;
        } else {
			scoreEarned = FINAL_BOSS_POINTS_HIT;
		}

        if (this.isDestroyed()) {
            scoreEarned += this.getPointValue();
        }

        return scoreEarned;
    }
}