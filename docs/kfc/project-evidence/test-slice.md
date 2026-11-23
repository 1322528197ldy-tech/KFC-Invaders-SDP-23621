# Endless formations: test slice and execution record

## Scope, environment and entry conditions

Requirement: ordinary Endless waves expose five layouts and three movement/firing styles while preserving wave progression, bosses, rewards, pause/Freeze and normal-save isolation. Implementation: `src/endless/WavePattern.java`, `src/screen/EndlessMode.java`; regression sources under `tests/endless`.

Execution baseline: main `35fcd2ccfe3025137509e5bcb22cf43c2934314b`, Windows, Microsoft OpenJDK 21.0.10. The existing verification script compiled with that JDK's default target; this run does not independently prove Java 17 compatibility. CI separately specifies Java 17. These are standalone Java check runners, not JUnit tests. No coverage percentage or cyclomatic-complexity metric was measured in this run.

Run from a clean source checkout with JDK 17+ on PATH:

```powershell
java -version
git rev-parse HEAD
powershell -ExecutionPolicy Bypass -File tests/endless/verify-connected.ps1
```

The script creates disposable per-suite class/save directories under `bin/connected-check-*`, logs each runner and checks a packaged JAR from an empty working directory. Preserve its printed verification path, suite logs, compilation logs and screenshots. Do not run manual experiments against the player's real save directory.

## Designed scenarios

| ID | Type / risk | Procedure and expected result | Evidence / current result |
| --- | --- | --- | --- |
| E01 | Functional + boundary / overlap or clipping | Generate waves 1-100 at widths 432/448; five layouts have bounded, separated slots and valid counts | PatternChecks / passed automated |
| E02 | State regression / movement drift | Advance representative waves; verify sweep, zigzag and sine boundaries and survivor phase; pause/Freeze/reward stop simulation as specified | PatternChecks + RunTest / passed automated |
| E03 | Combat boundary / unavoidable projectile flood | Exercise single/aimed/fan attacks, exposed shooters and last survivor; verify bounded non-homing aim, cadence and ordinary projectile cap | PatternChecks / passed automated; fairness still needs human test |
| E04 | Integration / stuck wave, boss or reward transition | Exercise real collisions, multiwave progression, boss boundaries and reward selection; run long simulation | ConnectedSystemsChecks + RunTest / passed automated |
| E05 | Item/UI regression / broken shared behavior | Check rewards, shared items/shop effects and pause/reward key routing | RewardItemsTest + ShopEffectsChecks + EndlessUiChecks / passed automated |
| E06 | Packaging + persistence / wrong access or real-save mutation | Start isolated packaged entry from empty cwd, exercise current main access/password behavior and save separation | EntryGateChecks / passed automated |
| H01 | Human usability + balance / early waves too easy | On pinned candidate, play waves 1-9; record deaths, time, attack readability, dodge space and suggested values | Coordinator reports Jeremie reached wave 9 without bugs; early difficulty remains follow-up |
| H02 | Human integration + balance / late spikes and unreadable bosses | Play waves 10-15 including bosses, reward transitions, pause/resume and restart; attach build/environment and observations | Not established by available human-test evidence; proposed F2 |

For H01/H02 record OS/JDK/display size, source/release SHA, ship/loadout, access mode, tester/date, actual steps, expected/actual behavior and pass/fail. A bug report needs wave, reproduction steps and severity. Do not mark an unplayed scenario Passed.

## Executed result on 2026-11-24

All seven existing Endless runners completed successfully. Raw summary: [main-endless-validation.txt](main-endless-validation.txt).

| Runner | Printed successful checks |
| --- | --- |
| ConnectedSystemsChecks | 67 |
| RunTest | 63 |
| RewardItemsTest | 110 |
| EndlessUiChecks | 21 |
| ShopEffectsChecks | 29 |
| PatternChecks | 9,029,352 repeated assertions across generated/simulated cases |
| EntryGateChecks | 27 |

The large PatternChecks number is a loop assertion count, not millions of separately designed test cases or a coverage percentage. Automated simulation/offscreen rendering does not certify human playability, physical input, high-DPI layout or late-wave fairness.

## Distinguish revisions and CI

The earlier PR #43 review used its own head `f926a8be0c325eace37759c693911c57829d9f6b` and reported 36 packaged checks plus 12 display checks. Those numbers must not be copied as this main run's result; PR #43 was still open at inspection.

Current CI builds production Java, checks required resources and smoke-tests title startup. The release workflow packages and smoke-tests a JAR. Neither workflow currently runs these seven Endless suites. CI green and all gameplay scenarios passed are different claims.
