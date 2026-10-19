# Two-hit enemies

Stage 6 starts with one yellow enemy at the bottom of the leftmost column.
A standard hit turns it white; the next standard hit defeats it. Surviving hits
do not award points, drops or kill achievements. Movement and shooting are unchanged.

The last argument of `SETTINGS_LEVEL_6` in `engine/Core.java` configures the count:
`new GameSettings(6, 3, 10, 1000, 1)`. Zero disables the variation; the count must
leave at least one ordinary enemy. Stages other than 6 and 9 default to zero.

One elite is intentional for the initial Stage 6 balance test, following KFC's
suggested starting count. Increase it after gameplay testing; it is not a final
balance decision.

The diagonal and two-hit settings are independent. Existing four-argument,
five-argument boolean, and five-argument count constructors remain supported.
Stage 9 uses `new GameSettings(7, 5, 1, 300, true, 2)` to combine diagonal
zig-zag movement with two yellow two-hit enemies, placed in the bottom row's
two leftmost columns. Its formation size, movement interval and shooting
frequency are preserved. Both elite and ordinary enemies move together.

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
