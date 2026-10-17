# Two-hit enemies

Stage 6 starts with one yellow enemy at the bottom of the leftmost column.
A standard hit turns it white; the next standard hit defeats it. Surviving hits
do not award points, drops or kill achievements. Movement and shooting are unchanged.

The last argument of `SETTINGS_LEVEL_6` in `engine/Core.java` configures the count:
`new GameSettings(7, 7, 10, 1000, 1)`. Zero disables the variation; the count must
leave at least one ordinary enemy. Other stages default to zero.

One elite is intentional for the initial Stage 6 balance test, following KFC's
suggested starting count. Increase it after gameplay testing; it is not a final
balance decision.

The diagonal and two-hit settings are independent. Existing four-argument,
five-argument boolean, and five-argument count constructors remain supported.
Use `new GameSettings(7, 7, 10, 1000, true, 2)` to enable diagonal movement
and two elites together, for example when configuring the future Stage 9.

This uses the current seven-stage configuration. When the ten-stage progression
is integrated, connect the count to its Stage 6 settings. No boss behaviour or
Stage 9 placement is included; the settings now support the future combination.

## Validation

Java 17 compilation with `-Xlint:all` and standalone regression checks cover
both variations separately and together, including shared diagonal movement.
Stage 6 was also checked manually by the feature requester. Full item interaction
and difficulty testing remains to be done.

Compile `src` and `tests/TwoHitEnemyTest.java` together into an isolated build
directory, then run `TwoHitEnemyTest` with that directory and `res` on the classpath.
The tests use the game's profile persistence, so do not use an existing game build
directory containing real player progress.

For manual testing, temporarily change the initial `GameState` level in `Core`
from 1 to 6. Check both hits, rewards, ordinary enemies, restart and stage advance,
then restore the initial level to 1 before committing.
