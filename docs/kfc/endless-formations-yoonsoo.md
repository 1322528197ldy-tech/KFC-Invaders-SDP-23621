# Endless enemy formations and patterns — Yoonsoo

## Base and integration

Verified against GitHub on November 10, 2026:

- Original local IntelliJ project: `f65c093` (October 19).
- Course `oh-gnues/Invaders-SDP-23621` main: `b7ffbb3` (October 28).
- KFC team main: `a643ec6` (November 3), already contains course main.
- KFC Endless branch: `a83cdc5` (October 31).

Branch `codex/endless-formations` starts from team main and locally integrates the existing Endless branch. The original `codex/yoonsoo-stage-progression` branch remains available. GitHub review is requested before any integration into team `main`; this work does not merge the PR or enable automatic merging. Existing Endless integration PR #30 remains relevant and should be coordinated by the team lead.

## Player-visible behavior

Five layouts cycle through ordinary waves: grid, filled triangle, layered V, diamond, and staggered ranks. Every fifth wave remains a boss-only encounter and does not consume a formation slot. Formation and movement/fire schedules produce a deterministic 15-combination cycle after opening exceptions. Movement and fire currently use the same three-step index, so this does not enumerate all 45 possible combinations. Initial waves introduce the attacks gradually.

| Wave | Formation | Movement | Shooting |
| --- | --- | --- | --- |
| 1 | Grid | Horizontal sweep | Single straight shot |
| 2 | Triangle | Zigzag | Single straight shot |
| 3 | Layered V | Sine wave | Aimed shot |
| 4 | Diamond | Horizontal sweep | Aimed shot |
| 5 | Existing boss | Existing native pattern | Existing native pattern |
| 6 | Staggered ranks | Zigzag | Aimed shot |
| 7 | Grid | Sine wave | Three-shot fan |
| 8 | Triangle | Horizontal sweep | Single straight shot |

Zigzag adds a shallow shared vertical cycle. Sine wave offsets columns with different phases, producing a ripple through the ranks. Oscillation offsets return to zero each cycle, so only wall turns cause permanent descent. Surviving enemies retain their original pattern phase after neighboring ships die.

Only exposed enemies shoot. Aimed shots target the player's position when fired, with horizontal velocity limited to 55% of downward velocity; they never home. Fan volleys have three lanes and a 60% longer firing interval. Ordinary incoming shots are capped at 18. Enemy count stays within the existing 12–35 range, and movement/projectile speed caps remain in place.

Pause, countdown and reward selection freeze the simulation. The Freeze item freezes the movement clock as well as both movement axes. Existing health scaling, every-three-wave rewards, every-five-wave bosses, scoring, shared items and QA save isolation remain connected.

## Implementation and ownership

- `src/endless/WavePattern.java`: Endless-only layout generation, pattern schedule, vertical offsets and shot pacing.
- `src/screen/EndlessMode.java`: wave spawning, movement, exposed-shooter selection, bounded aimed/fan volleys and current-pattern HUD label.
- `tests/endless/PatternChecks.java`: layout, wall/HUD bounds, overlap, movement, pause/freeze, projectile behavior, boss transitions and offscreen screenshots.
- `tests/endless/ShopEffectsChecks.java`: isolate the lightning assertion from kills that may occur during its earlier real firing exercise.
- `tests/endless/verify-connected.ps1`: include pattern checks and use the isolated temp directory for JAR packaging.

The HUD retains the next boss wave number alongside the new pattern label. Follow-ups for team review: separate movement/fire selection if more variety is wanted; preserve exact top/bottom diamond symmetry when rounding row counts; connect the full Endless test suite to CI (current CI only compiles and smoke-tests the title screen). These follow-ups are not required for the existing deterministic schedule to run.

Campaign formation classes and native boss attack code are unchanged by this feature. The base integration includes the Endless menu hook from the existing team branch.

## Try it in IntelliJ

1. Open the existing `KFC-Team-Development` project and use branch `codex/endless-formations`.
2. Run the existing **Invaders - Team Development** configuration (`engine.Core`, project root as working directory), using JDK 17 or later.
3. Choose **Endless Mode**. After Stage 10 is cleared, **START RUN** starts normal play.
4. For an immediate isolated QA run, click the small **QA lock**, type `000`, then press Enter. This uses the pre-existing team test gate and does not unlock campaign progress.
5. Move with arrow keys, fire with Space, pause with P/ESC. Items use 1–3; reward cards also accept 1–3 only while the reward panel is open. The pattern name appears in the HUD. Each ordinary wave changes the layout.

The separately packaged executable uses its own save location beside its JAR; the source project remains the place to edit and rebuild the feature.

## Validation

Run `powershell -ExecutionPolicy Bypass -File tests/endless/verify-connected.ps1` from the project root with JDK 17+ on PATH. This compiles production sources with lint, runs connected services, Endless regression/soak, item/reward, UI, shop-effect and pattern suites, and checks packaged menu/password access from an empty working directory.

Pattern tests cover spawning through wave 100 at 432px and 448px widths, 50 seconds of accelerated movement for 20 representative waves, all five layouts/three movements/three attacks, non-overlapping slots and moving sprites, aimed/fan trajectories, last-survivor firing, pause/Freeze/reward behavior and boss waves 5/10/15. Existing regression tests also clear fifteen waves through real projectile collisions and run a 60,000-frame soak.

Offscreen previews are written to the verification directory's `screenshots/endless-formations.png`. Automated bounds checks and screen rendering have passed; longer human playtesting should tune attack pacing and late-wave health with Jeremie.

## Suggested Jira wording

**Title:** Add varied enemy formations and attack patterns to Endless Mode

**Description:** Endless ordinary waves now rotate between grid, triangle, layered V, diamond and staggered formations. Horizontal sweep, zigzag and sine movement combine with straight, bounded aimed and three-way fan attacks. Exposed-shooter selection, volley pacing and a projectile cap preserve dodge space. Existing bosses, reward checkpoints and Freeze/pause behavior remain connected.

**Acceptance criteria:** All layouts appear in normal Endless progression; enemies spawn without overlap and stay inside lateral boundaries; aimed shots do not home; fan volleys provide dodge lanes; pause/Freeze stop pattern motion; boss/reward checkpoints still work; connected regression and pattern checks pass.
