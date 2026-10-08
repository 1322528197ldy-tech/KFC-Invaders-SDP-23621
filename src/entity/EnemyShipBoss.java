package entity;

import java.awt.Color;
import java.util.Set;

import engine.Cooldown;
import engine.Core;
import engine.DrawManager.SpriteType;
import engine.GameSettings;

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

	/* Ship movement speeds */
	private int speedX = 0;
	private int speedY = 0;

	/* Boss shooting cooldown */
    private Cooldown shootCooldown;
	private int currentShootingFrequency;
	/* Minimum shooting rate */
	private static final int MIN_SHOOTING_RATE = 500;
	/* Shooting rate decline per shot */
	private static final int DECLINE_RATE = 30;
	/* Bullet speed */
	private static int BULLET_SPEED = 5;

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

	/**
	 * Moves the ship the specified distance.
	 * 
	 * @param distanceX
	 *            Distance to move in the X axis.
	 * @param distanceY
	 *            Distance to move in the Y axis.
	 */
	public final void move(final int distanceX, final int distanceY) {
		this.positionX += distanceX;
		this.positionY += distanceY;
	}

	/**
 	* Moves the boss back and forth within the screen boundaries (top, bottom, left, right)
 	* by applying the baseSpeed from Core, making it smoothly bounce off the edges.
 	* 
 	* @param screenWidth  The width of the screen (this.width in GameScreen)
 	* @param screenHeight The height of the screen (this.height in GameScreen)
 	* @param gameSettings The core settings for difficulty and speed
 	*/
    public final void moveBoss(final int screenWidth, final int screenHeight, 
            final GameSettings gameSettings) {
        if (this.isDestroyed()) {
            return;
        }

		if (this.speedX == 0 && this.speedY == 0) {
			this.speedX = gameSettings.getBaseSpeed();
			this.speedY = gameSettings.getBaseSpeed();
		}
       
        this.move(this.speedX, this.speedY);

        if (this.positionX <= 0) {
            this.positionX = 0;
            this.speedX = -this.speedX;
        } else if (this.positionX >= screenWidth - this.width) {
            this.positionX = screenWidth - this.width;
            this.speedX = -this.speedX;
        }

        int topBoundary = 40;
        int bottomBoundary = screenHeight - 200 - this.height;

        if (this.positionY <= topBoundary) {
            this.positionY = topBoundary;
            this.speedY = -this.speedY;
        } else if (this.positionY >= bottomBoundary) {
            this.positionY = bottomBoundary;
            this.speedY = -this.speedY;
        }
	}


    /**
     * Manages the shooting of bullets by the boss ship, ensuring 
	 * that it adheres to a cooldown period between shots.
     * 
     * @param bullets general bullet set to add the new bullet to
     * @param gameSettings Core settings for difficulty and shooting frequency
     * @return bullet firing success status (true: fired, false: on cooldown or destroyed)
     */
    public final boolean shoot(final Set<Bullet> bullets, final GameSettings gameSettings) {
        if (this.shootCooldown == null) {
			this.currentShootingFrequency = gameSettings.getShootingFrecuency();
            this.shootCooldown = Core.getCooldown(gameSettings.getShootingFrecuency());
            this.shootCooldown.reset();
        }
        if (!this.isDestroyed() && this.shootCooldown.checkFinished()) {

			if (this.currentShootingFrequency > MIN_SHOOTING_RATE) {
				this.currentShootingFrequency -= DECLINE_RATE;
				if (this.currentShootingFrequency < MIN_SHOOTING_RATE) {
                	this.currentShootingFrequency = MIN_SHOOTING_RATE;
            	}
			}

			this.shootCooldown = Core.getCooldown(this.currentShootingFrequency);
			this.shootCooldown.reset();

			bullets.add(BulletPool.getBullet(this.positionX + this.width / 2, 
                    this.positionY + this.height, BULLET_SPEED));
            return true;
        }
        return false;
    }
}