# Campaign movement checks (stages 1-4)

These standalone checks cover the movement changes in PR #40. They do not include
the later stages 6-9 movement changes or wave-based spawning.

From the repository root, with JDK 17 or later on PATH, run:

```powershell
powershell -ExecutionPolicy Bypass -File tests/campaign/verify-movement.ps1
```

The script compiles production code and the two selected suites, runs with an
offscreen renderer, and prints `PASS:` results. Build output, logs, and disposable
save files stay in a new `bin/campaign-check-*` directory. Player saves are not used.
The test renderer follows the existing Endless checks' `Unsafe` adapter and needs
no additional libraries or visible window.

On Linux/macOS, the same suites can be run with:

```sh
check_dir="$(mktemp -d)"
mkdir -p "$check_dir/classes"
find src -name '*.java' > "$check_dir/sources.txt"
javac --release 17 -encoding UTF-8 -d "$check_dir/classes" @"$check_dir/sources.txt"
javac --release 17 -encoding UTF-8 -cp "$check_dir/classes:res" \
  -d "$check_dir/classes" tests/CampaignMovementTest.java tests/TwoHitEnemyTest.java
for suite in CampaignMovementTest TwoHitEnemyTest; do
  mkdir -p "$check_dir/$suite"
  cp -R "$check_dir/classes" "$check_dir/$suite/classes"
  java -Djava.awt.headless=true -cp "$check_dir/$suite/classes:res" "$suite" || exit 1
done
```

`CampaignMovementTest` checks:

- Original stage settings, enemy counts, and spawn grids for stages 1-4.
- Three full 720-update loops for stages 2-4, plus stage 1's diagonal return trips.
- Route waypoints/geometry, bounds, and formation spacing at 448x520 and 432x497.
- Removing edge enemies during patrol. Stages 2 and 4 extend their horizontal
  turns to the walls using the outermost surviving enemies, without a position
  jump. Other stages retain their original routes through edge removal.
- Shooting after edge removal and cleanup.
- The actual `GameScreen` pause-key path and Freeze item: positions and route
  progress stop, then continue at the next phase after resume/expiry.
- Bullet collisions, stage-clear detection, next-stage initialization (including
  the stage 5 boss), and restart resetting the formation and movement progress.

`TwoHitEnemyTest` expects stage 3's ellipse patrol, retains stage 9's existing
diagonal movement expectation, and preserves the two-hit enemy assertions.

This is a focused check command, not a full-suite claim. The existing
`KfcBossChecks` and `KfcProgressionChecks` have separate `GameState` constructor
compilation errors; this change does not modify those tests.
