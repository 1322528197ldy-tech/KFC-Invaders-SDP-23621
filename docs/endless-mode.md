# Endless mode

## Branch baseline

`kfc/endless-mode` starts at teacher repository
`oh-gnues/Invaders-SDP-23621` main commit
`e0cef9023f51117891f752014f7510b958eef6e4` (checked 2026-10-09).
It does not start from the older KFC fork main or merge unfinished boss branches.

## Play

Requires JDK 17. From the repository root on Windows:

```powershell
.\run.ps1
```

On Linux/macOS:

```sh
mkdir -p bin
javac -encoding UTF-8 -Xlint:all -d bin $(find src -name '*.java')
java -cp bin:res engine.Core
```

Select **Endless** in the main menu. There is a three-second preparation period.

| Input | Action |
| --- | --- |
| A / D or left / right | Move |
| Hold Space | Fire |
| P or Escape | Pause / resume |
| Q while paused | End run and save personal bests |
| Enter on results | Fresh endless run |
| Escape on results | Main menu |

## Rules and balance

- Score is `floor(active survival seconds) * 10 + enemies defeated * 100`.
  A boss counts as one defeated enemy. Countdown, intermission and pause time
  do not earn points or advance the boss clock. Damage upgrades do not multiply scores.
- Start with three lives. A hit grants 1.5 seconds of protection, shown in cyan.
  Losing all lives or allowing the formation to reach the player ends the run.
- Waves continue beyond the campaign limit. Clear a wave for a two-second rest;
  every third cleared wave restores one missing life (maximum three).
- Formations start at 12 enemies and grow to at most 35. Movement difficulty
  increases 5% per 30 active seconds; movement and shot intervals have playable
  lower limits. Enemy health grows by 0.5 per three active minutes, so later runs
  continue to scale after density/speed reach their limits.
- After 90 active non-boss seconds OR 30 regular kills since the last boss defeat,
  a boss appears immediately, even mid-wave. The regular formation is suspended
  during the duel; existing bullets are cleared on entry for a fair transition.
- Bosses bounce horizontally and alternate randomly between three-shot fans
  and aimed shots. Health increases each encounter. Defeating one clears bullets,
  resets both trigger counters, grants a two-second rest, and resumes the formation.
- Each boss grants exactly one random upgrade: damage +10%, movement speed +10%,
  or +1 projectile. Speed is capped at 360 px/s and projectiles at five; capped
  categories leave the reward pool, so every reward remains useful. Damage stacks.
- Local best score, longest active time and most kills are independent records,
  saved atomically to `endless-records.properties` on death or confirmed run end.
  Missing/corrupt files and write failures are handled without ending the game.
  Closing the operating-system window exits immediately without saving the active run;
  use pause then Q to record an unfinished run.

## Integration boundary

The KFC requirements describe unlocking after Stage 10, but this teacher baseline
has only seven campaign stages and no integrated final boss. For this playable
feature branch, **Endless is available immediately**. The Stage 10 unlock gate is
explicitly deferred until that campaign feature is integrated; this implementation
does not pretend that clearing Stage 7 is equivalent to clearing Stage 10.

Survival simulation and results are separate from `GameScreen` and `ScoreScreen`.
Campaign scores, pending diamonds and currency files remain separate. Endless
currently does not award campaign coins, diamonds or achievement progress.
When integrating Stage 10, gate `MenuItem.ENDLESS` on persisted campaign completion;
keep screen return code 8 and the independent simulation.

Shared-file changes: `Core` routes menu code 8; `MenuItem` adds the entry;
`DrawManager` fits all eight menu rows, initializes the fallback selected font,
and disposes drawing contexts; `InputManager` latches short key presses for the
endless overlays. Existing held-key controls remain available to other screens.

## Verification

```powershell
.\run.ps1 -Test
```

```sh
javac -encoding UTF-8 -Xlint:all -d bin $(find src tests -name '*.java')
java -ea -Djava.awt.headless=true -cp bin:res engine.EndlessRunTest
```

The dependency-free tests cover countdown/pause, scoring, difficulty, both boss
triggers, one reward per boss, collision consumption, invulnerability, death,
formation breach, waves beyond 10, repairs, clean retries, persistence and a
60,000-frame simulation. Expected warning logs exercise corrupt/unwritable saves.
CI runs the same checks on this feature branch and pull requests to main.

Manual acceptance: menu entry fits; enter mode; move/fire; pause and verify time
and bullets stay still; resume; pause and Q; verify results; Enter retries from
wave 1 with three lives; Escape returns to menu; ordinary Play still opens campaign.
