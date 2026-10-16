# Boss encounter integration

Stages 5 and 10 use a Stage 5 two-hit / Stage 10 five-hit bouncing boss. Stage 10 is a mid-boss placeholder, not a completed dedicated final boss. Boss movement settings are pixels per update (5 and 8); normal formations use movement intervals.

Each hit gives 100 points and defeat gives 1000 once: two hits total 1200. Shooting starts after the stage cooldown and accelerates by 30ms per shot to a 500ms floor. Freeze blocks movement, following the existing item policy.

Check boss defeat, transition to Stage 6/final score, one kill credit, pending diamonds once, first-boss and Stage 10 achievements, item shield/freeze and game-over animation. Death must not award clear diamonds. Normal enemy/bonus coin drops, item drops, particles and shake must remain intact.

The ship selection screen currently previews ship variants but does not connect a selected model to a run in upstream. This PR preserves that baseline; Fleet Master roster/selection wiring remains with the owning teams.

## Automated offscreen checks (Java 17)

Run in a fresh temporary classes directory so generated player profiles are isolated from real saves. The harness uses an offscreen frame adapter; it is not a human playthrough. Compile production sources first, then `tests/kfc/KfcBossChecks.java` against them, and run `KfcBossChecks <temporary-screenshot-directory>` with `res` on the classpath. Test-only Unsafe warnings are expected on Java 17.

October 16: Stage 5 uses MidBoss (2 HP, total score 1200); Stage 10 uses FinalBoss (5 HP, total score 6500). Both keep the shared boss achievement, pause, restart and stage-clear integration.
