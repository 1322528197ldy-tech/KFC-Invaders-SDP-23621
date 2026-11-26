# Campaign movement checks (stages 1-4 and 6-9)

These checks exercise ordinary campaign stages 1-4 and 6-9.

## KAN-20 campaign layout and balance candidate

| Stage | Enemies | Layout | Firing sequence | Configured interval (ms) |
| --- | --- | --- | --- | --- |
| 1 | 15 | Grid | Random exposed column | 2000 |
| 2 | 20 | Descending staircase | Random exposed column | 2500 |
| 3 | 20 | Two separated wings | Left wing, right wing | 1500 |
| 4 | 30 | Forward center with recessed wings | Center, wings | 1100 |
| 6 | 18 | Three stepped squads | Left to right | 1000 |
| 7 | 30 | Forward wings with recessed center | Outer columns inward | 850 |
| 8 | 35 | Alternating-depth comb | Center columns outward | 700 |
| 9 | 35 | Two wings with offset depth | Sweep, alternate wings, center/wings with each route | 550 |

Each firing turn emits one straight bullet from an exposed enemy. Empty groups
are skipped immediately while surviving enemies retain their original group.
Stage 9 follows its existing diamond, serpentine and figure-eight route phases.
The interval settings feed the existing variable cooldown. Values are initial
playtest candidates. Late-stage firing is slower than the former 500/500/300 ms
settings to allow players to read the new directional sequences.

For human balance checks, compare the same commit, ship and equipment, starting
with stage 1 and playing through stage 4, then stages 6-9. Record clear time,
lives lost and collected/used items, especially rapid fire, shield and Freeze.
Jeremie can compare the stage 3-4 difficulty progression; Dongjeong can assess
the effects of bonuses. Human balance approval remains pending.

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

- Campaign layouts, enemy counts and non-overlapping spawn positions.
- Three full 720-update loops for stages 2-4, plus stage 1's diagonal return trips.
- Three cycles for stages 6-9 (720, 600, 540 and 1440 updates respectively),
  including all three stage 9 patterns and their 480-update transitions.
- Route waypoints/geometry, bounds, and formation spacing at 448x520 and 432x497.
- Removing edge enemies during patrol. Stages 2, 4 and 6-9 extend their horizontal
  turns to the walls using the outermost surviving enemies, without a position
  jump. Late campaigns are also tested for clearing either or both outer columns
  just before a turn, including each of stage 9’s three patterns.
- Shooting after edge removal and cleanup.
- Group/column firing order, single-shot cooldown, stage 9 phase changes, skipping
  several destroyed columns together, and firing after only one column remains.
- The actual `GameScreen` pause-key path and Freeze item: positions and route
  progress stop, then continue at the next phase after resume/expiry.
- Bullet collisions, stage-clear detection, next-stage initialization (including
  the stage 5 and stage 10 bosses), and restart resetting the formation and movement progress.

An optional output-directory argument to `CampaignMovementTest` renders the real
campaign screens into `campaign-formations.png` for PR layout review. These are
offscreen renders, and do not constitute an interactive human playtest.

`TwoHitEnemyTest` expects stage 3's ellipse patrol, checks stage 9's mixed
patrol, and preserves the two-hit enemy assertions.

This is a focused check command, not a full-suite claim. The existing
`KfcBossChecks` and `KfcProgressionChecks` have separate `GameState` constructor
compilation errors; this change does not modify those tests.
