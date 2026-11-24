# Endless-only integration

Ordinary waves now rotate through five formations, three movements and three firing patterns. See [Yoonsoo's formation notes](../../docs/kfc/endless-formations-yoonsoo.md) for the schedule, balance constraints, ownership and validation.

TitleScreen's optional Endless hook detects the compiled class, so the entry works in release JARs and from other working directories. Endless has its own record view, opened with H while paused. To remove the optional mode, omit its compiled classes and rebuild.

`GameplaySession` connects the existing ship/skin selection, item implementation, equipment, wallets, achievements, bosses, VFX, meteor system and audio calls. It owns only the endless session's lifecycle and accounting. Definitions, prices, effect behavior, native shooting patterns and save managers remain in their original modules.

`SharedAccess` adapts the original APIs without editing them. It creates an independent native ItemSystem instance, delegates to native Ship/Boss shooting and movement, and adapts their wall clocks to active endless time. Compatibility access to private factories/clock fields is isolated here. If those APIs are renamed by a future upstream update, this adapter may need adjustment; arbitrary future systems cannot be connected without a contract.

Shared catalog/stat changes apply after syncing and restarting/rebuilding. New standalone features can register an implementation of `endless.GameplayExtension` in `res/META-INF/services/endless.GameplayExtension`, one fully qualified class name per line. The feature receives lifecycle events, active-time updates, drawing callbacks and optional enemy factories/health rules. Providers must respect `session.isPersistent()` for progress writes.

Every three cleared waves still offers three choices and one reroll. Boss encounters are boss-only waves with a safe introduction. The BOSS box on the entry panel (click it or press B) chooses when they happen: every 5 waves (default), every 90 active seconds, or every 30 kills. Under the second and kill rules the wave after the threshold becomes the boss wave, and counting restarts when that boss is defeated. The choice lasts for the session, including retries. All three rules are defined in `BossRule.java`: each is one enum constant naming what it counts and how many, so a rule is retuned or added there without touching `EndlessMode`. Existing native mid/final bosses supply their movement and attacks. Generic combat achievements use the original manager; endless waves never count as campaign victories. Coins enter the shared wallet, while pending diamonds follow the existing cash-out risk rule. Pause then Q extracts and ends the run; death forfeits pending diamonds.

Normal runs require the Stage 10 achievement. Open the test-mode lock at the bottom right of Settings and enter `000` to turn on the test profile for this session. The same button opens the dialog to disable testing. Switching profiles from the settings screen during a paused Endless game ends the current run when you return.

Testing unlocks the shop catalog, achievements and Endless access. Loadout changes and campaign progress stay in memory under the test profile. Endless runs use the test loadout and keep their records in a separate file. Loadout changes apply to the next run.

Run `tests/endless/verify-connected.ps1` from the repository root to compile the game and run the connected-service, Endless, item/reward, and offscreen UI checks with isolated saves.
