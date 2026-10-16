# KFC upstream sync — 2026-10-16

## Inputs

- KFC main: `3af4d0e53fc4bc9a15f5964c4dea2f1725b9db4c`
- Jeremie's combined testing branch: `370829a69395c7be40f42e45f24f4c8df11ddaf8`
- Course main: `3f1c5b32132a72eb2f517c046ea4bf23845842ba`

This sync retains both histories. It brings the existing tutorial, ten-stage settings
and temporary Stage 5/10 bosses together with the course shop, skins, audio,
economy and item fixes. It does not merge internal boss PRs #22/#23, the Endless
branch, or change upstream PR branches #140–#142.

## Conflict resolution

`GameScreen.java` initialization keeps both the boss/formation choice and the
selected shop skin. Collision handling keeps boss score/achievement recording
and the normal-enemy item score boost, delayed score popup, drops and VFX.
The new restart coin refund and animated skin updates are retained.
`Core.java` keeps ten stages/tutorial routing and uses upstream PurchaseManager
for the selected ship. DrawManager retains upstream font/UI and boss sprites.

## Validation

- Java 17 production compilation with `-Xlint:all`: passed, 66 source files.
- `KfcTutorialChecks`: 14 checks passed.
- `KfcBossChecks`: 62 checks passed, including initialization/rendering of stages 1–10.
- `KfcSyncChecks`: 27 checks passed. Purchased ship/skin, pause rendering,
  restart score/coin restoration and boss recreation, real score-boost item
  activation and delayed popup, Settings/Shop/Customize/Achievements rendering
  and ESC return were checked.
- Real Swing startup under Xvfb: reached the title screen and ran for 10 seconds
  without an application exception.
- Save files and test classes were isolated in a temporary directory.

Reproduce with Java 17. Compile production sources to a fresh temporary `classes`
directory, compile `KfcTutorialChecks`, `KfcBossChecks`, and `KfcSyncChecks` against
it, and run each class with that directory and the absolute `res` path on the
classpath. The first two accept a screenshot output directory. Set the working
directory to a temporary directory so saves do not affect an existing profile.
The offscreen fixtures use Unsafe and produce test-only compiler warnings.
`KfcProgressionChecks` is the standalone normal-wave PR #141 fixture; on this
combined boss tree, use `KfcBossChecks` for stage progression coverage instead.

Automated checks and startup are not a continuous human playthrough. Final-boss
replacement and burst behavior, new enemy designs, Endless integration, and the
achievement team's level-7-to-10 condition update remain separate follow-ups.
