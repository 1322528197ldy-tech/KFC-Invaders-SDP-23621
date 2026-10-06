# Player Ship Designs: Fast Attack and Big Bullet

Team A1 · Player & Enemy Ship Variety · Related: DR-2 (player ship types), DR-5 (ship-specific sprites)

This document introduces two new player ship sprites for the selectable ship roster. Both are drawn on a 13x8 grid, the same size as the current `Ship` sprite (26x16 px in game), so they keep the existing ship size and hitbox.

| Ship | Concept | Grid |
|---|---|---|
| Fast Attack | High rate of fire | 13x8 |
| Big Bullet | Large projectile | 13x8 |

In the grids below, `█` is a filled pixel and `·` is empty.

## Fast Attack

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

An arrowhead hull narrows to a three-pixel nose. Below it, a single-pixel spine runs down the centre between two swept-back wings.

## Big Bullet

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

A heavy two-row base with a small fin at each end. Above it, a funnel widens upward into a seven-pixel mouth with two raised tips, like a wide muzzle for a large shot.

## Gameplay Stats

Not finalised yet. The Standard column lists the current constants in `src/entity/Ship.java` for comparison.

| Stat | Standard (current) | Fast Attack | Big Bullet |
|---|---|---|---|
| Movement speed (`SPEED`, px per frame) | 2 | TBD | TBD |
| Shooting interval (`SHOOTING_INTERVAL`, ms) | 750 | TBD | TBD |
| Bullet speed (`BULLET_SPEED`, px per frame, negative = up) | -6 | TBD | TBD |
| Bullets per shot | 1 | TBD | TBD |
| Max health (hits) | 1 | TBD | TBD |

## Sprite Data for `res/graphics`

`FileManager.loadSprite` reads a sprite column by column: each group of 8 digits is one column from top to bottom, starting from the leftmost column (13 columns x 8 digits = 104 digits). Per DR-5, append these after the existing entries in `res/graphics`, and add the matching `SpriteType` values and `spriteMap.put` entries in `DrawManager` in the same order.

**Fast Attack**

```text
00000011000001110000111100011110001111000111100001111110011110000011110000011110000011110000011100000011
```

**Big Bullet**

```text
00000000000001110000001101100011001100110011111100111111001111110011001101100011000000110000011100000000
```

## Open Items

- **Destruction sprites:** DR-5 requires an idle and a destruction sprite for each ship. Only the idle sprites are designed so far.
- **Big Bullet projectile:** `Bullet` is a fixed 3x5 sprite (6x10 px) and `BulletPool` reuses those objects, so a larger bullet needs its own sprite and size handling when bullets are reused.
