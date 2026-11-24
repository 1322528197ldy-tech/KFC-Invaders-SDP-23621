# Window size and responsive navigation

Settings now provides Original (448 x 520), 150%, 200% and Fit screen. The game
keeps its original logical playfield; only presentation and pointer coordinates
scale. Requested sizes are clamped to the monitor work area. The preference is
saved separately in `window-settings.properties` beside the JAR and is ignored
by Git. Delete that file to restore the original default.

The school-to-team opening remains skippable and keeps the artwork's aspect
ratio. Its fading background extends into the presentation margins.

## Navigation

- Child pages have Back controls; the main menu has no extra navigation button.
- Campaign and Endless Quit controls pause and ask for confirmation, initially
  selecting No. Escape/P cancels the confirmation; a second fresh press resumes.
- Cash out retains its existing meaning: bank pending diamonds and end the run.
  The results page retains its existing score-saving behavior.
- A fresh Escape returns from a child page immediately, even during its initial
  activation guard. A held key cannot back out of successive pages.
- Quick Escape taps are retained between updates. P acts once per physical
  press in campaign mode, rather than repeatedly toggling while held.
- Mouse hover appears in the same update. Menu move sounds run on one daemon
  worker with at most one pending sound, so slow audio cannot block hover.
- Losing focus or minimizing clears held/queued input in both input handlers.

## Build and verification

Use JDK 17+ and Python 3. The test runner creates a production JAR and runs it
with separate test-only fixtures. Each suite receives its own temporary copy,
working directory and saves. It never points gameplay tests at a player's JAR.

```sh
# Headless display/input checks, including three seeded 60,000-frame runs
python tests/display/verify-display.py

# Include the existing campaign, boss, collision, item and economy checks
python tests/display/verify-display.py --full --output /path/to/test-results

# On a desktop session, also open and dispose native test windows
python tests/display/verify-display.py --full --native --output /path/to/test-results
```

On Windows, use a Windows output path such as `C:/Temp/kfc-checks`.
The runner's output includes logs, JSON results and generated previews. Native
checks are intended for a Windows desktop; the headless checks are portable.

Coverage includes all ten campaign stages at four presentation sizes; movement,
shooting, pause/resume and state preservation; nested entry/Settings test-mode dialogs;
short taps, held keys and focus loss; confirmation defaults and cancellation;
settings, original-size restoration, artwork margins and coordinate mapping.
The `--full` suites additionally cover campaign routes/clear/restart, bosses,
collisions, rewards, shop effects and economy settlement. Automated checks and
short native startup runs are not a complete manual playthrough.

## Integration notes

`Core` retains the latest main's 448 x 520 logical dimensions. `Frame` keeps its
DPI-aware Canvas, native-resolution raster and existing pointer mapping. The
new size presets change the outer window only; Original is the initial default,
and the latest free window resizing remains available. Offscreen Swing helpers
are test fixtures only. `DrawManager` presents a completed
buffer and requests the appropriate surround color for the opening.

`Screen` shares child-page navigation and fresh Escape handling. `GameScreen`
changes only pause/quit input integration. `EndlessMode.Run`, combat values,
reward rules, the Endless unlock gate and the supplied artwork are unchanged.
The integration is based on KFC main `8bf0fd535f7529374a9bb51e4933698186e80a54`
and preserves its stages 6-9 movement, language selection, HD renderer and
Settings-based tester profile. Window labels and navigation follow the selected
language. Test-code entry survives focus loss, and a quick Escape closes only
the dialog before a subsequent fresh press returns from Settings.
