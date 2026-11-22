# Brush icons: spec

Icons for the "WorldEdit" brushes window (`src/main/java/dev/axiomwegui/ui/BrushesWindow.java`).
Each icon is one SVG file, `src/main/resources/assets/axiom_worldedit_gui/icons/<name>.svg`.
The addon draws it with ImGui lines and fills (`VectorIcon.java`); there is no raster step.

## Style

Match Axiom's own tool icons (see `docs/axiom-tools-reference.webp`, the grid at the top left):
white, flat, one colour, simple pictograms that read at 32 px on a blue button.

- `viewBox="0 0 32 32"`, the drawing inside about 2..30 so it does not touch the button edge.
- One colour. Colours in the file are ignored; anything that is not `none` is drawn in the theme's text colour.
- Mostly strokes: `fill="none" stroke="#fff" stroke-width="2"` (1.5 to 2.5 is fine). Small solid shapes may use a fill.
- At most ~12 shapes per icon; no hairlines thinner than 1.5.

## Allowed SVG

`path` (M L H V C S Q T A Z, absolute or relative), `line`, `polyline`, `polygon`, `circle`, `ellipse`,
`rect` (no rounded corners), nested in `g`. Attributes `fill`, `stroke`, `stroke-width` (also in `style`).

Not supported: `transform`, `text`, `use`, gradients, masks, clip paths, opacity, `stroke-dasharray`.
Filled areas must be single closed contours without holes (a filled shape with a hole draws wrong);
make holes with strokes instead.

## Icons to draw (15)

| file | brush | what it does |
| --- | --- | --- |
| `cylinder.svg` | cylinder | places a cylinder of blocks (a first test version exists; redo it to match the set) |
| `splatter.svg` | splatter | scatters blocks randomly inside a sphere, denser in the middle |
| `snowsmooth.svg` | snowsmooth | smooths terrain and its snow layers |
| `lower.svg` | lower | lowers terrain (pair with Axiom's raise/elevation icon: a down arrow over ground) |
| `heightmap.svg` | heightmap | raises/lowers terrain following a grayscale heightmap image |
| `morph.svg` | morph | erodes and fills at the same time, reshaping the surface |
| `erode.svg` | erode | eats away exposed blocks |
| `dilate.svg` | dilate | grows the surface outwards, filling gaps |
| `gravity.svg` | gravity | makes floating blocks fall down |
| `forest.svg` | forest | plants trees |
| `feature.svg` | feature | places a world-gen feature (flowers, rocks, ores...) |
| `snow.svg` | snow | lays snow on surfaces |
| `extinguish.svg` | extinguish | puts out fire |
| `butcher.svg` | butcher | kills mobs in range |
| `item.svg` | item | uses an item (bone meal, a shovel...) on blocks |

Brushes that do what an Axiom tool does reuse that tool's icon with a small "WE" mark and need no file:
sphere, set, clipboard, smooth, raise, deform, biome.

## Check

Open `docs/icons-preview.html` (made together with the icons): every icon at 32 px and 64 px, white on
Axiom's blue button colour and on a dark background, with its name.
