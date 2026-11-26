package engine;

/**
 * Implements an object that stores a single game's difficulty settings.
 *
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 *
 */
public class GameSettings {

	public enum FormationLayout {
		GRID, STAIRCASE, SPLIT_WINGS, FORWARD_CENTER,
		THREE_SQUADS, FORWARD_WINGS, COMB, OFFSET_WINGS;

		public int columnOffsetX(int column, int columns) {
			return switch (this) {
				case SPLIT_WINGS, OFFSET_WINGS -> column >= columns / 2 ? 40 : 0;
				case FORWARD_CENTER, THREE_SQUADS, FORWARD_WINGS -> (column / 2) * 16;
				default -> 0;
			};
		}

		public int columnOffsetY(int column, int columns) {
			return switch (this) {
				case STAIRCASE -> column * 8;
				case FORWARD_CENTER -> column >= columns / 3 && column < 2 * columns / 3 ? 24 : 0;
				case THREE_SQUADS -> (column / 2) * 16;
				case FORWARD_WINGS -> column < 2 || column >= columns - 2 ? 32 : 0;
				case COMB -> (column % 2) * 24;
				case OFFSET_WINGS -> column >= columns / 2 ? 32 : 0;
				default -> 0;
			};
		}
	}

	public enum FiringPattern {
		RANDOM, ALTERNATING_SIDES, CENTER_WINGS, LEFT_TO_RIGHT,
		OUTSIDE_IN, INSIDE_OUT, PATROL_SEQUENCE
	}

	public enum MovementPattern {
		CLASSIC,
		DIAGONAL,
		DIAGONAL_PATROL,
		DIAMOND_PATROL,
		ELLIPSE_PATROL,
		RECTANGLE_PATROL,
		SERPENTINE_PATROL,
		FIGURE_EIGHT_PATROL,
		SIX_POINT_PATROL,
		MIXED_PATROL
	}

	/** Width of the level's enemy formation. */
	private int formationWidth;
	/** Height of the level's enemy formation. */
	private int formationHeight;
	/** Speed of the enemies, function of the remaining number. */
	private int baseSpeed;
	/** Frequency of enemy shootings, +/- 30%. */
	private int shootingFrecuency;
	/** Number of two-hit enemies in this stage's formation. */
	private int twoHitEnemyCount;
	/** Movement pattern used by the entire formation. */
	private final MovementPattern movementPattern;
	private final FormationLayout formationLayout;
	private final FiringPattern firingPattern;

	/**
	 * Constructor.
	 *
	 * @param formationWidth
	 *            Width of the level's enemy formation.
	 * @param formationHeight
	 *            Height of the level's enemy formation.
	 * @param baseSpeed
	 *            Speed of the enemies.
	 * @param shootingFrecuency
	 *            Frecuency of enemy shootings, +/- 30%.
	 */
	public GameSettings(final int formationWidth, final int formationHeight,
			final int baseSpeed, final int shootingFrecuency) {
		this(formationWidth, formationHeight, baseSpeed, shootingFrecuency, false, 0);
	}

	/** Creates stage settings with a small, configurable two-hit enemy count. */
	public GameSettings(final int formationWidth, final int formationHeight,
			final int baseSpeed, final int shootingFrecuency,
			final int twoHitEnemyCount) {
		this(formationWidth, formationHeight, baseSpeed, shootingFrecuency,
				false, twoHitEnemyCount);
	}

	/** Configures movement and health variations independently, or together. */
	public GameSettings(final int formationWidth, final int formationHeight,
			final int baseSpeed, final int shootingFrecuency,
			final boolean diagonalMovement, final int twoHitEnemyCount) {
		this(formationWidth, formationHeight, baseSpeed, shootingFrecuency,
				diagonalMovement ? MovementPattern.DIAGONAL : MovementPattern.CLASSIC,
				twoHitEnemyCount);
	}

	/** Creates settings with an explicit formation movement pattern. */
	public GameSettings(final int formationWidth, final int formationHeight,
			final int baseSpeed, final int shootingFrecuency,
			final MovementPattern movementPattern, final int twoHitEnemyCount) {
		this(formationWidth, formationHeight, baseSpeed, shootingFrecuency,
				movementPattern, twoHitEnemyCount, FormationLayout.GRID, FiringPattern.RANDOM);
	}

	/** Configures a campaign layout and a sequence of exposed single shooters. */
	public GameSettings(final int formationWidth, final int formationHeight,
			final int baseSpeed, final int shootingFrecuency,
			final MovementPattern movementPattern, final int twoHitEnemyCount,
			final FormationLayout formationLayout, final FiringPattern firingPattern) {
		if (twoHitEnemyCount < 0
				|| (twoHitEnemyCount > 0
						&& twoHitEnemyCount >= formationWidth * formationHeight))
			throw new IllegalArgumentException(
					"Two-hit enemies must leave at least one ordinary enemy");
		this.twoHitEnemyCount = twoHitEnemyCount;
		this.formationWidth = formationWidth;
		this.formationHeight = formationHeight;
		this.baseSpeed = baseSpeed;
		this.shootingFrecuency = shootingFrecuency;
		this.movementPattern = movementPattern;
		this.formationLayout = java.util.Objects.requireNonNull(formationLayout);
		this.firingPattern = java.util.Objects.requireNonNull(firingPattern);
	}

	/**
	 * Constructor that also sets how the formation moves.
	 *
	 * @param formationWidth
	 *            Width of the level's enemy formation.
	 * @param formationHeight
	 *            Height of the level's enemy formation.
	 * @param baseSpeed
	 *            Speed of the enemies.
	 * @param shootingFrecuency
	 *            Frecuency of enemy shootings, +/- 30%.
	 * @param diagonalMovement
	 *            True if the whole formation moves diagonally.
	 */
	public GameSettings(final int formationWidth, final int formationHeight,
			final int baseSpeed, final int shootingFrecuency,
			final boolean diagonalMovement) {
		this(formationWidth, formationHeight, baseSpeed, shootingFrecuency,
				diagonalMovement, 0);
	}

	/**
	 * @return the formationWidth
	 */
	public final int getFormationWidth() {
		return formationWidth;
	}

	/** @return Number of two-hit enemies to spawn. */
	public final int getTwoHitEnemyCount() {
		return this.twoHitEnemyCount;
	}

	/**
	 * @return the formationHeight
	 */
	public final int getFormationHeight() {
		return formationHeight;
	}

	/**
	 * @return the baseSpeed
	 */
	public final int getBaseSpeed() {
		return baseSpeed;
	}

	/**
	 * @return the shootingFrecuency
	 */
	public final int getShootingFrecuency() {
		return shootingFrecuency;
	}

	/**
	 * @return true if the formation moves diagonally
	 */
	public final boolean isDiagonalMovement() {
		return this.movementPattern == MovementPattern.DIAGONAL;
	}

	public final MovementPattern getMovementPattern() {
		return this.movementPattern;
	}

	public final FormationLayout getFormationLayout() {
		return this.formationLayout;
	}

	public final FiringPattern getFiringPattern() {
		return this.firingPattern;
	}

}
