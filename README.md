# Axiom WorldEdit GUI

A client-side Fabric addon that brings WorldEdit into the [Axiom](https://axiom.moulberry.com/) editor.
Every WorldEdit command gets a form, WorldEdit brushes become an Axiom tool, Axiom selections of any
shape work with WorldEdit, and both mods share one undo history, so Ctrl+Z in Axiom also undoes
WorldEdit edits.

**Singleplayer only.** The addon calls WorldEdit on the integrated server. On multiplayer servers the
menu says that it is unavailable.

## Features

Everything is in the **WorldEdit** menu at the end of Axiom's menu bar.

### Operations window
- WorldEdit operations that Axiom does not have, grouped by task:
  - terrain: //naturalize, //overlay, //forest, //flora, /forestgen, /snow, /thaw, /green, /fixwater…
  - shapes and formulas: //generate and //deform with presets and a formula help, //line, //curve,
    //center, //revolve
  - world generation: //regen, //feature, //structure, biomes
  - mobs and entities, session settings (//gmask, //perf, //limit, //timeout)
- Pattern fields are checked by WorldEdit's own parser while you type. *Active block* inserts Axiom's
  active block, and *Mix…* edits the pattern as a list of blocks with weights (`30%stone,70%andesite`).
- Mask fields have *From Axiom*: it turns the mask from Axiom's Tool Masks window into WorldEdit
  syntax (`below = grass_block` → `>grass_block`, `biome = plains` → `$plains`, `y > 64` → `=y>64`).
- Selection shapes: turn the selection box into an ellipsoid, cylinder or sphere (WorldEdit builds the
  shape, Axiom shows it as the selection), or back into a box.
- Clipboard: copy Axiom's clipboard into WorldEdit's and back. //rotate and //flip are applied.
- WorldEdit schematics: load a schematic into WorldEdit's clipboard or straight into Axiom's, and save
  the clipboard. CraftScripts: run the scripts from WorldEdit's scripts folder.

### All commands
All WorldEdit commands and sub-commands (185 in WorldEdit 7.4.6) with search. Each has a form built
from WorldEdit's own command tree, so the list always matches the installed WorldEdit version.

### Console
WorldEdit's answers to the commands sent from the addon, including the late answers of commands that
finish in the background (//distr, //count, //schem load). There is a line for typing any command, and
buttons for WorldEdit's own undo and redo.

### WorldEdit brush tool
A new tool in Axiom's Tools window. Pick any of WorldEdit's brushes (sphere, cylinder, smooth, gravity,
deform, forest, feature, butcher, snow, biome, erode, dilate, apply and paint…), fill in its settings and press
*Use this brush*. Then paint with the right mouse button at the block under the cursor. An optional
WorldEdit mask limits which blocks change. One stroke is one step in the undo history.

### Selection
- Axiom's selection is what WorldEdit commands work on: boxes become WorldEdit cuboids, and free-shape
  selections (magic, lasso, freehand, expanded…) keep their exact shape.
- When a WorldEdit command changes the selection (//pos1, //expand, //shift, //sel, //paste -s…) or you
  click with the WorldEdit wand, the new selection appears in Axiom.
- While the editor is open, WorldEditCUI's selection lines are hidden, because Axiom already draws the
  same selection. This can be turned off in the menu.

### One undo history
WorldEdit edits are written into Axiom's history: Ctrl+Z, Ctrl+Y and Axiom's History window undo and
redo them in order with Axiom's own edits, including chests and other block entities. This works for
commands from the addon, commands typed in chat and brush strokes. Edits that change entities or
biomes stay in WorldEdit's history; the console marks them, and *WorldEdit undo* undoes them. The menu
item *Undo WorldEdit edits with Ctrl+Z* turns the shared history off.

## Requirements

| Component     | Version          |
|---------------|------------------|
| Minecraft     | 26.3             |
| Fabric Loader | 0.16 or newer    |
| Axiom         | 6.1.5            |
| WorldEdit     | 7.4.6 (Fabric)   |
| Java          | 25               |

WorldEditCUI is optional.

## Building

Minecraft 26.3 is not obfuscated, so the addon builds with plain `javac` against the jars that
Prism Launcher already has. No Gradle or Loom is needed.

```sh
./build.sh            # builds build/axiom-worldedit-gui-<version>.jar
./build.sh --install  # also replaces the addon in the instance's mods folder
```

The script reads these variables, with defaults for the `PWGood__26.3` Prism instance:
`JDK`, `PRISM`, `MODS`, `AXIOM`, `WORLDEDIT`, `FABRIC_API`.

## How it works

| Part | Where | What it does |
|------|-------|--------------|
| Menu and windows | `ui/`, `mixin/MainMenuBarMixin`, `mixin/EditorUIMixin` | ImGui windows drawn in Axiom's editor frame |
| Command schema | `we/WeSchema` | reads commands, arguments, defaults and flags from WorldEdit's command tree |
| Running commands | `we/DirectBackend`, `mixin/CoreMcPlayerMixin` | runs commands on the integrated server and collects WorldEdit's messages |
| Selection | `selection/`, `mixin/PlatformCommandManagerMixin`, `mixin/WorldEditMixin` | Axiom → WorldEdit before each command, WorldEdit → Axiom after it |
| Undo history | `journal/`, `mixin/LocalSessionMixin` | turns WorldEdit's change set into an Axiom history entry |
| Brushes | `brush/`, `mixin/ToolManagerMixin` | captures the brush a //brush command builds and paints with it |
| Clipboard and files | `we/ClipboardBridge`, `ui/FilesPanel` | moves clipboards between the mods, lists schematics and scripts |

## Known limits

- Singleplayer only, and not while the world is open to LAN.
- Free-shape Axiom selections cannot be expanded or contracted by WorldEdit (//expand, //contract);
  turn them into a box first.
- Edits that change entities or biomes are undone with *WorldEdit undo*, not Ctrl+Z.
- The addon relies on Axiom internals (menu bar, editor frame, history, tool list) and is built for
  Axiom 6.1.x.

## License

GPL-3.0-only, see [LICENSE](LICENSE). The addon calls WorldEdit's API, and WorldEdit is GPL-3.0.
The jar contains no code from Axiom. It only hooks into Axiom at runtime.
