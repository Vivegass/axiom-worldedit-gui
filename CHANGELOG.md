# Changelog

## 0.3.0+mc26.3

First release on Modrinth and CurseForge, for Minecraft 26.3, Axiom 6.1.5 and WorldEdit 7.4.6.

- **Brushes tab**: all WorldEdit brushes as tool buttons in their own *WorldEdit* tab, grouped, with icons.
  forest, set and item have a *Plain / Apply / Paint* mode instead of separate entries.
- **Brushes paint like Axiom's**: hold the right mouse button to paint, Ctrl + middle mouse drag for the
  radius, shape preview under the cursor, settings apply by themselves, no gaps when moving fast.
  New brushes start with the active block; shape and radius carry over between brushes (optional).
- **Block lists for patterns**: Axiom's block list with percentages, drag and drop from the hotbar,
  Inventory, Active Block and Palette.
- **Dropdowns and sliders**: tree types, shapes, biomes, items and other fixed values are searchable
  dropdowns; sizes, densities and iterations are sliders.
- **Undo**: biome brushes, fallen trees and other generated features, and blocks that Minecraft reshapes
  next to an edit (snowy grass, fences) are now undone with Ctrl+Z.
- Fixed: the extinguish brush removed every block instead of only fire.
- Fixed: a crash when picking a value from a dropdown.

## 0.2.0

First version on GitHub: WorldEdit menu, operations window, forms for all commands, console, brush tool,
selection sync both ways, shared undo history.
