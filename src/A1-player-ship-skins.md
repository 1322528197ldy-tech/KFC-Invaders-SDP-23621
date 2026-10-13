# Player Ship Skins: Fast Attack and Big Bullet

Team A1 · Player & Enemy Ship Variety · Related: DR-5 (ship-specific sprites), [A1-player-ships.md](A1-player-ships.md)

This document proposes three alternative skins for each of the Fast Attack and Big Bullet ships. A skin changes only the sprite: every skin uses the same 13x8 grid as the original (26x16 px in game), is left-right symmetric, and keeps the ship's hitbox and stats.

| Ship | Skin | Concept |
|---|---|---|
| Fast Attack | A1 Arrow | Sharper arrowhead |
| Fast Attack | A2 Twin Fang | Twin muzzles |
| Fast Attack | A3 Stealth | Hollow outline |
| Big Bullet | B1 Cannon | Heavy barrel |
| Big Bullet | B2 Mortar | Rounded hull |
| Big Bullet | B3 Fortress | Battlements |

In the grids below, `█` is a filled pixel and `·` is empty. The original sprite is shown first for comparison.

## Fast Attack

**Original**

```text
·············
·····███·····
····█████····
···███████···
··█████████··
·████·█·████·
████··█··████
███·······███
```

### A1 · Arrow

```text
······█······
·····███·····
·····███·····
····█████····
···███████···
█·████·████·█
███·█···█·███
██·········██
```

The nose is one pixel longer and each wing ends in a small spike, for a sharper look.

### A2 · Twin Fang

```text
·············
····█···█····
····██·██····
···███████···
··█████████··
·███·███·███·
███··█·█··███
██·········██
```

The nose splits into two fangs, like twin muzzles for a rapid-fire ship.

### A3 · Stealth

```text
·············
·····███·····
····█···█····
···█··█··█···
··█···█···█··
·███··█··███·
███···█···███
██·········██
```

A hollow outline of the original arrowhead with the centre spine kept, so the silhouette stays the same but looks lighter.

## Big Bullet

**Original**

```text
·············
···█·····█···
···███████···
····█████····
·····███·····
·█···███···█·
·███████████·
·███████████·
```

### B1 · Cannon

```text
··██·····██··
··███···███··
···███████···
····█████····
·····███·····
██···███···██
█████████████
·███████████·
```

The muzzle becomes a thick U-shaped barrel and the base widens to the full 13 pixels.

### B2 · Mortar

```text
·············
····█···█····
····█████····
·····███·····
···███████···
·███████████·
█████████████
·██·█···█·██·
```

A short muzzle on a rounded hull with legs underneath, like a mortar.

### B3 · Fortress

```text
···█·····█···
···███████···
····█████····
····█████····
·····███·····
█·█·█████·█·█
█████████████
█████████████
```

A taller muzzle on a solid two-row base with battlements, like a fortress.

## Sprite Data for `res/graphics`

Same format as the ship sprites: `FileManager.loadSprite` reads each sprite column by column, 8 digits per column from top to bottom, starting from the leftmost column (13 x 8 = 104 digits). New lines are appended after the existing entries in `res/graphics`, with matching `SpriteType` values and `spriteMap.put(..., new boolean[13][8])` entries in `DrawManager` in the same order (DR-5).

**A1 · Arrow**

```text
00000111000000110000011000001100000111100111110011111000011111000001111000001100000001100000001100000111
```

**A2 · Twin Fang**

```text
00000011000001110000111000011100011110000011111000011100001111100111100000011100000011100000011100000011
```

**A3 · Stealth**

```text
00000011000001110000111000010100001000000100000001011110010000000010000000010100000011100000011100000011
```

**B1 · Cannon**

```text
00000110000001111100001111100011011100110011111100111111001111110111001111100011110000110000011100000110
```

**B2 · Mortar**

```text
00000010000001110000011100001110011011110011111000111110001111100110111100001110000001110000011100000010
```

**B3 · Fortress**

```text
00000111000000110000011111000011011101110111111101111111011111110111011111000011000001110000001100000111
```

## Open Items

- **Skin system:** The game has no skin selection yet. Choosing a skin needs a place in the menu (for example the ship select screen) and a way to store the choice; unlocking skins with currency would be an integration with the Currency System team.
- **Colour:** Every player ship is drawn in a single colour (`BASE_COLOR` in `Ship`, green). These skins change only the shape; skin colours would need a code change.
- **Destruction sprites:** Like the original ships, the skins use the shared `ShipDestroyed` sprite when hit.
