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
}