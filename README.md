# GTNH Kanban

An in-game project board for Minecraft 1.7.10 Forge. The earlier board release was manually verified in GTNH 2.9 RC2; the new assignment and material expansion features need an in-game check in that pack.

## Use

Open with `/kanban` or the configurable **K** hotkey, including in survival. Project owners manage membership; project members can edit cards.

- **Assignees:** open a saved card, click Assignees, then toggle any current project members. Several members can share a card. Removing a member from the project clears their assignments.
- **Checklist:** add items from the NEI catalog. Edit a root quantity and click Apply or press Enter. X removes a row and its descendants.
- **Automatic breakdown:** adding an item to the checklist breaks it down through every recipe level, from NEI, until each branch reaches a base material: ingots, nuggets, gems, dusts, fluids, sand, gravel, cobblestone, stone and glass. Ores are never part of a breakdown, and no recipe that consumes ore (raw, crushed, impure or purified) is used. Buckets, cells and other fluid containers are listed as needed items but never broken down. GT crafting tools (hammer, file, wrench, saw...) and other inputs a recipe uses without consuming are never broken down or counted as materials; GT programmed circuits are left out. While NEI is searched, progress shows at the bottom of the card. Very large trees stop at the card limits (4096 rows, 16 levels) and say so.
- **Recipe choice:** the **Breakdown:** button on a card cycles the preference: *Crafting table first* (default; then the lowest-voltage GT machine), *Lowest voltage first* (GT machines by EU/t, then crafting table), or *Cheapest total* (fewest base materials, comparing recipes six levels deep). Decomposition handlers such as disassemblers, recyclers, scanners and replicators are never used.
- **Base materials:** cards with a breakdown show a collapsible *Base materials for this card* section listing the summed totals of every unfinished branch, with how many you carry (or have in the open container). Rows marked done are left out. A separate collapsible *Tools & equipment* section lists each kind of tool needed once (any material works). Item branches start collapsed, show only consumed materials, and open with their arrows.
- **Regenerate all:** the card's **Regenerate all** button re-runs the breakdown of every item on the card with the current recipe preference, one item at a time. Items that are now base materials lose their old breakdown. Use it after changing the preference or updating the mod.
- **Manual recipes:** click Recipe beside any row to choose a specific recipe and ingredient alternatives (Use this recipe, one level), re-run the full breakdown below that row with the current preference (Auto breakdown), or Remove the branch.
- **Quantities:** recipe ingredients follow parent quantities, rounded up to whole recipe batches. Fluids use mB. Inputs exposed by NEI as nonconsumed are marked reusable. Unsupported or probabilistic recipe outputs show an explanation rather than an incomplete material breakdown.
- **HUD:** pin an in-progress card to see its base-material totals. Item rows show available/required counts from the player inventory plus the open chest or storage container, and turn green when enough are available. Fluid rows show required mB; machine tank contents are not counted.

Assignments, recipe choices, material trees and manual completion flags are saved with the world. Completing ingredients does not automatically complete their parent item.

## Build and install

From this directory:

```sh
./gradlew clean build
```

Install `build/libs/gtnhkanban-<version>.jar` in the GTNH server and every player's client `mods/` directory. Do not install the `-dev` or `-sources` JAR. Replace the older Kanban JAR, then restart the server and clients.

Update all clients and the server together for the assignment/material-tree release because the network messages changed. Existing flat checklist saves load with no recipe expansion and no card assignees.
