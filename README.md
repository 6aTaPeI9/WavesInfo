# WaveInfo

A Mindustry (v159.7+) mod that overlays enemy information directly on the battlefield: a wave-by-wave enemy list and the actual paths enemies will take to reach your base.

## Why

In survival and campaign maps, it's often hard to plan defenses: you don't know what's coming in the next waves, where enemies will spawn, or which route they will take. WaveInfo shows all of that at a glance, so you can prepare the right defenses for the right threats.

## What it does

- **Wave list** — a scrollable panel showing every upcoming wave. Units in each wave are grouped by movement type (Ground / Air / Naval / Legs) so you can immediately see what kind of threat is coming. Hover an icon for details: unit name, spawn count, shield amount and status effect.
- **Enemy path overlays** — draws the real paths enemies will follow from their spawn points to your core, using the game's own pathfinding (flow fields). Each movement type has its own color and can be toggled independently:
  - **Ground** (red) — walking units
  - **Naval** (blue) — water units
  - **Legs** (green) — mech/spider units
  - **Hover** (orange) — hovering units
  - **Flying** (purple) — flying units, drawn as straight lines from their actual spawn point off the map edge to their target

## Usage

A small button appears below the wave counter in the top-left corner during gameplay. Click it to open or close the panel; the checkboxes at the bottom enable or disable each path overlay. The panel can be dragged anywhere on screen.

Paths update as the map changes (buildings, walls, water) and only appear once the game's pathfinding has finished computing them.
