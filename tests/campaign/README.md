# Campaign movement checks (stages 1-4 and 6-9)

These checks exercise ordinary campaign stages 1-4 and 6-9.

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

- Original stage settings, enemy counts, and spawn grids for ordinary stages.
- Three full 720-update loops for stages 2-4, plus stage 1's diagonal return trips.
- Three cycles for stages 6-9 (720, 600, 540 and 1440 updates respectively),
  including all three stage 9 patterns and their 480-update transitions.
- Route waypoints/geometry, bounds, and formation spacing at 448x520 and 432x497.
- Removing edge enemies during patrol. Stages 2, 4 and 6-9 extend their horizontal
  turns to the walls using the outermost surviving enemies, without a position
  jump. Late campaigns are also tested for clearing either or both outer columns
  just before a turn, including each of stage 9’s three patterns.
- Shooting after edge removal and cleanup.
- The actual `GameScreen` pause-key path and Freeze item: positions and route
  progress stop, then continue at the next phase after resume/expiry.
- Bullet collisions, stage-clear detection, next-stage initialization (including
  the stage 5 and stage 10 bosses), and restart resetting the formation and movement progress.

`TwoHitEnemyTest` expects stage 3's ellipse patrol, checks stage 9's mixed
patrol, and preserves the two-hit enemy assertions.

This is a focused check command, not a full-suite claim. The existing
`KfcBossChecks` and `KfcProgressionChecks` have separate `GameState` constructor
compilation errors; this change does not modify those tests.
