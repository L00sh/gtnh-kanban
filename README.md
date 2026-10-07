# GTNH Kanban

An in-game project board for Minecraft 1.7.10 Forge. The earlier board release was manually verified in GTNH 2.9 RC2; the new assignment and material expansion features need an in-game check in that pack.

## Download

**[Download latest build — gtnh-kanban-latest.jar](https://github.com/L00sh/gtnh-kanban/releases/download/latest-build/gtnh-kanban-latest.jar)**

Updated after successful builds on `main`. This is a rolling prerelease; [release details](https://github.com/L00sh/gtnh-kanban/releases/tag/latest-build) identify the source commit and build run. The download becomes available after the first successful `main` build with this workflow.

Install the JAR on the server and on clients that want Kanban features. Use copies of the same downloaded build on both sides: the filename stays the same as new builds are published, but the internal mod version changes. Remove the previous Kanban JAR before restarting. Development branches and pull requests keep their downloads in the workflow's artifacts.

## Use

Open with `/kanban` or the configurable **K** hotkey, including in survival. Project owners manage membership on the **Members** tab: typing a name suggests whitelisted players (or, without a whitelist, players the server has seen); Tab completes, Enter adds, and each member has a **Remove** button. Project members can edit cards.

- **Project tabs:** up to 5 projects can be open at once as tabs next to **Projects**; click one to switch boards, its **x** (or a right-click) closes it. Opening a sixth project asks you to close a tab first. When the row gets crowded, tabs shrink and show their full name on hover.
- **Board:** columns run left to right in the order set in **Board settings** (defaults: Backlog, Ready, In Progress, Review, Done) and share the window's width equally. Each column shows its card count, has a **+** to create a card there, and scrolls with the mouse wheel or its scrollbar (drag the thumb or click the track) once it holds more cards than fit. Drag cards to reorder them within a column or to move them to a position in another column; a line shows where the card will land, and dragging near a column's top or bottom edge scrolls it. The order is saved and shared with every member. If the columns no longer fit at their minimum width, shift + wheel or the arrows scroll sideways.
- **Cards:** each card has a per-project number (#1, #2, ... never reused), title, optional item icon, type, priority (None, Low, Medium, High), description, creator and creation time (the card shows its age; hover for the full date and time). A progress bar counts finished plain tasks and finished top-level checklist items.
- **Tasks and comments:** add plain tasks ("do x") on the card screen; click to tick them off. **Comments** opens the card's comment thread; authors can delete their own comments and the project owner can delete any.
- **Board settings** (any project member; shared by every project on the server): rename, reorder, add and remove columns, and add, rename, recolor and remove card types. Removing a column moves its cards to the first column; removing a type clears it from cards. Cards are never deleted by a settings change. Default types: Bug, Feature, Power Failure, Planning, Future, Maintenance.
- **Icons:** pick any NEI item as a project icon (board **Icon** button, shown in the project list and board header) or a card icon (button beside the card title).
- **Assignees:** open a saved card, click Assignees, then toggle any current project members. Several members can share a card. Removing a member from the project clears their assignments.
- **Checklist:** add items from the NEI catalog. Edit a root quantity and click Apply or press Enter. X removes a row and its descendants.
- **Automatic breakdown:** adding an item to the checklist breaks it down through every recipe level, from NEI, until each branch reaches a base material: ingots, nuggets, gems, dusts, fluids, sand, gravel, cobblestone, stone and glass. Logs, saplings and seeds are needed as they are, like buckets, since their only recipes are growing them. Ores are never part of a breakdown, and no recipe that consumes ore (raw, crushed, impure or purified) is used. Buckets, cells and other fluid containers are listed as needed items but never broken down. GT crafting tools (hammer, file, wrench, saw...) and other inputs a recipe uses without consuming are never broken down or counted as materials; GT programmed circuits are left out. While NEI is searched, progress shows at the bottom of the card. Very large trees stop at the card limits (4096 rows, 16 levels) and say so.
- **Recipe choice:** the **Recipe** dropdown on a card picks which recipe type the breakdown prefers: any crafting table, machine or other handler NEI knows (type to filter the list). Items with no recipe of that type fall back to crafting table, then GregTech machines from the lowest voltage up, then furnace. The default is the crafting table. Decomposition handlers such as disassemblers, recyclers, scanners and replicators are never used.
- **Base materials:** cards with a breakdown show a collapsible *Base materials for this card* section listing the summed totals of every unfinished branch, with how many you carry (or have in the open container). Rows marked done are left out. Hover a material (or tool) to see what in this card's breakdown uses it and how much each needs. A separate collapsible *Tools & equipment* section lists each kind of tool needed once (any material works). Item branches start collapsed, show only consumed materials, and open with their arrows.
- **Regenerate all:** the card's **Regenerate all** button re-runs the breakdown of every item on the card with the current recipe preference, one item at a time. Items that are now base materials lose their old breakdown. Use it after changing the preference or updating the mod.
- **Manual recipes:** click Recipe beside any row to choose a specific recipe and ingredient alternatives (Use this recipe, one level), re-run the full breakdown below that row with the current preference (Auto breakdown), or Remove the branch.
- **Quantities:** recipe ingredients follow parent quantities, rounded up to whole recipe batches. Fluids use mB. Inputs exposed by NEI as nonconsumed are marked reusable. Unsupported or probabilistic recipe outputs show an explanation rather than an incomplete material breakdown.
- **HUD:** pin any card to see its base-material totals. Item rows show available/required counts from the player inventory plus the open chest or storage container, and turn green when enough are available. Fluid rows show required mB; machine tank contents are not counted.

Assignments, recipe choices, material trees and manual completion flags are saved with the world. Completing ingredients does not automatically complete their parent item.

## Window art

The board, project and member screens draw a pixel-art window (panel plus folder tabs) from `src/main/resources/assets/gtnhkanban/textures/gui/frame.png`. That atlas is generated from `art/frame-mockup.png` (drawn at 2 image pixels per art pixel); after editing the mockup, regenerate it with `python art/build_frame_atlas.py` (needs Pillow). Hover and pressed tab states are currently lightened/darkened copies of the normal tab; replace those pieces in the script with hand-drawn art to restyle them.

## Build and install

From this directory:

```sh
./gradlew clean build
```

Install `build/libs/gtnhkanban-<version>.jar` in the GTNH server's `mods/` directory. Client installation is optional: players without Kanban can join and play normally, but need the same JAR in their client `mods/` directory to use the board GUI, checklist, HUD and hotkey. Running `/kanban` without the client mod shows an installation message. Do not install the `-dev` or `-sources` JAR. Replace the older Kanban JAR, then restart the server and clients.

Update the server and all clients that have Kanban installed together. Installed clients must match the server build; incompatible versions are rejected during connection. Clients without Kanban are accepted. On Forge/GTNH servers, a client with Kanban still requires the server to have Kanban. Optional installation applies only to this mod; other GTNH mods retain their own requirements. Existing flat checklist saves load with no recipe expansion and no card assignees. Saves from before configurable columns load with To do, In progress and Done cards in Backlog, In Progress and Done, numbered in board order, with no type, priority, creator or creation time.
