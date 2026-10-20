# Endless-only integration

All connection code belongs to this folder and `src/screen/EndlessMode.java`. No source under `engine`, `entity`, `item`, `shop`, or other screen classes is modified. The original campaign, menus and high-score screen remain unchanged. Endless has its own record view, opened with H while paused.

`GameplaySession` connects the existing ship/skin selection, item implementation, equipment, wallets, achievements, bosses, VFX, meteor system and audio calls. It owns only the endless session's lifecycle and accounting. Definitions, prices, effect behavior, native shooting patterns and save managers remain in their original modules.

`SharedAccess` adapts the original APIs without editing them. It creates an independent native ItemSystem instance, delegates to native Ship/Boss shooting and movement, and adapts their wall clocks to active endless time. Compatibility access to private factories/clock fields is isolated here. If those APIs are renamed by a future upstream update, this adapter may need adjustment; arbitrary future systems cannot be connected without a contract.

Shared catalog/stat changes apply after syncing and restarting/rebuilding. New standalone features can register an implementation of `endless.GameplayExtension` in `res/META-INF/services/endless.GameplayExtension`, one fully qualified class name per line. The feature receives lifecycle events, active-time updates, drawing callbacks and optional enemy factories/health rules. Providers must respect `session.isPersistent()` for progress writes.

Every three cleared waves still offers three choices and one reroll; every fifth wave has a boss-only encounter and a safe introduction. Existing native mid/final bosses supply their movement and attacks. Generic combat achievements use the original manager; endless waves never count as campaign victories. Coins enter the shared wallet, while pending diamonds follow the existing cash-out risk rule. Pause then Q extracts and ends the run; death forfeits pending diamonds.

QA reads loadout without migration writes and does not award currency or achievements. Its record file remains separate. Pause can open the original settings, achievement and shop/customization screens (purchases/customization are disabled in QA). Purchases apply to the next run. Audio hooks are connected, but this checkout still needs the original team's WAV resources to produce sound.

Run `tests/endless/verify-connected.ps1` from the repository root. It compiles the unchanged base game together with this module, then runs the connected-service, endless regression, item/reward and offscreen UI checks with isolated save directories. It does not edit or run migrations on the player's saves.
