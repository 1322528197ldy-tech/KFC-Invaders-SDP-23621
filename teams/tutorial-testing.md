# Tutorial stage

Every new game starts with a safe tutorial before Stage 1. Use A/D or the
left/right arrows to move, then SPACE to shoot. Clear the one-enemy practice
wave and press ENTER to start Stage 1. ESC returns to the main menu.
Practice does not award points or consume lives in the normal game.

## Manual checks

1. Run `engine.Core` with `res` on the classpath (in IntelliJ, mark `res` as
   Resources Root). Select Play. The tutorial should appear before Stage 1.
2. Press SPACE without moving: the movement step must remain active.
3. Move with A/D or the arrow keys: the shooting prompt and practice enemy appear.
4. Shoot away from the enemy: the wave must not complete until a shot hits it.
5. Align below the enemy and shoot: the completion message explains later enemies
   and bosses. Holding SPACE must not skip this message.
6. Press ENTER: Stage 1 begins with a fresh score and three lives.
7. Start a new game and press ESC during the tutorial: return to the menu without
   entering Stage 1 or the score screen. Starting again resets the tutorial.
8. Move against each edge: the player must stay inside the play area.

## Integration

`src/engine/Core.java` is a shared file. This change inserts `TutorialScreen`
before the existing game loop; mention this in the PR and obtain the required
shared-file review. No stage balance or boss behavior is changed.
