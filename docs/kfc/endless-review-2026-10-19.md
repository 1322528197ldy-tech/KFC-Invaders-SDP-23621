# Endless Mode integration review — 2026-10-19

## Review branch

This branch preserves `kfc/endless-mode` (`2e67421`) and integrates KFC main
`f65c093` (course main `2cfe243`). It is a review branch, not an approved release.
The original Endless branch and both main branches are unchanged by this work.

## Compatibility changes

- Use the existing `MidBoss` renderer instead of the removed `EnemyShipBoss`
  class. Endless combat health and attack rules still belong to `Run`; this
  replacement only restores its entity rendering and animation dependency.
- Update the existing full-life reward assertion to follow the configured
  bonus: the current shared item balance uses 200 points instead of the old
  500-point setting. Do not restore the old shared configuration to satisfy
  the test.
- Preserve the latest title-screen exit-confirmation fix and all course
  achievements, VFX and item changes.

## Validation

- Original branch: production compilation and 40 RunTest + 40 RewardItemsTest
  checks passed against its original dependencies.
- Unmodified integration with latest main: compilation failed because
  `EnemyShipBoss` was removed when campaign bosses were split.
- This compatibility branch: production compilation and both test suites
  passed (80 checks total) against current main and resources.
- These are automated checks, not manual gameplay approval. Full menu,
  mouse/keyboard, lock/unlock, retry/return and long-session gameplay remain
  to be exercised in a real window.

## Decisions required before an upstream feature PR

1. **Boss reward contract:** `teams/KFC.md` requires one random gameplay upgrade
   after every Endless boss. Current code drops a supply and instead offers
   one of three rewards every third cleared wave. Confirm the intended rule
   with the Endless owner/team, then align implementation, tests and docs.
2. **Packaged-game entry:** `TitleScreen.optionalEndless` requires the source
   file `src/screen/EndlessMode.java` to exist on disk. This hides the mode
   when running a packaged JAR without a source checkout. Decide whether this
   removable-development mechanism should remain in the submitted version.
3. **Test bypass:** the lock panel provides entry without the campaign unlock.
   It writes separate test records, but a release should gate this behind an
   explicit development option or remove it after testing.
4. **Cross-team gameplay:** verify selected ship/equipment expectations and
   the relationship between local Endless records and shared achievements.
   The current records are separate properties files; do not claim shared
   achievement integration without testing it.

## Reproduce

From this branch with JDK 17 or later:

```sh
mkdir -p bin/endless-review
javac -encoding UTF-8 -d bin/endless-review $(find src -name '*.java') tests/endless/*.java
java -Djava.awt.headless=true -cp bin/endless-review:res engine.RunTest
java -Djava.awt.headless=true -cp bin/endless-review:res engine.RewardItemsTest
java -cp bin/endless-review:res engine.Core
```

Keep balance PR #28 separate. Its gameplay approval does not follow from
these tests, and this branch does not include that proposed balance change.
