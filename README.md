# GTNH Kanban

An in-game project board for Minecraft 1.7.10 Forge. The earlier board release was manually verified in GTNH 2.9 RC2; the new assignment and material expansion features need an in-game check in that pack.

## Use

Open with `/kanban` or the configurable **K** hotkey, including in survival. Project owners manage membership; project members can edit cards.

- **Assignees:** open a saved card, click Assignees, then toggle any current project members. Several members can share a card. Removing a member from the project clears their assignments.
- **Checklist:** add items from the NEI catalog. Edit a root quantity and click Apply or press Enter. X removes a row and its descendants.
- **Recipe materials:** click Recipe beside an item or fluid, choose the recipe and any alternative ingredients, then Use this recipe. Ingredients stay nested beneath the original requirement. Expand each child as needed. Arrows collapse or reopen a branch. Remove expansion keeps the parent and discards its material subtree.
- **Quantities:** recipe ingredients follow parent quantities, rounded up to whole recipe batches. Fluids use mB. Inputs exposed by NEI as nonconsumed are marked reusable. Unsupported or probabilistic recipe outputs show an explanation rather than an incomplete material breakdown.
- **HUD:** pin an in-progress card to see its current material leaves. Item rows show available/required counts from the player inventory plus the open chest or storage container, and turn green when enough are available. Fluid rows show required mB; machine tank contents are not counted.

Assignments, recipe choices, material trees and manual completion flags are saved with the world. Completing ingredients does not automatically complete their parent item.

## Build and install

From this directory:

```sh
./gradlew clean build
```

Install `build/libs/gtnhkanban-<version>.jar` in the GTNH server and every player's client `mods/` directory. Do not install the `-dev` or `-sources` JAR. Replace the older Kanban JAR, then restart the server and clients.

Update all clients and the server together for the assignment/material-tree release because the network messages changed. Existing flat checklist saves load with no recipe expansion and no card assignees.
