package dev.axiomwegui.ui;

import dev.axiomwegui.ui.CommandForm.Preset;
import java.util.List;
import java.util.Map;

/**
 * Ready-made argument values. Expressions use WorldEdit's normalized coordinates: x, y and z run from
 * -1 to 1 across the selection unless -r, -o or -c is set.
 */
final class Presets {
    private Presets() {
    }

    static final List<Preset> GENERATE_SHAPES = List.of(
        new Preset("Sphere", "x^2+y^2+z^2<1"),
        new Preset("Hollow sphere shell", "x^2+y^2+z^2<1 && x^2+y^2+z^2>0.8"),
        new Preset("Dome (upper half)", "x^2+y^2+z^2<1 && y>0"),
        new Preset("Torus", "(0.7-sqrt(x^2+z^2))^2+y^2<0.3^2"),
        new Preset("Cylinder (vertical)", "x^2+z^2<1"),
        new Preset("Cone", "x^2+z^2<((1-y)/2)^2"),
        new Preset("Pyramid", "abs(x)+abs(z)<(1-y)/2*2 && y>-1"),
        new Preset("Arch", "(x^2+y^2<1) && (x^2+y^2>0.6) && y>0"),
        new Preset("Sine wave floor", "y<sin(x*3.14159)*0.4-0.4"),
        new Preset("Ripples", "y<cos(sqrt(x^2+z^2)*12)*0.15-0.5"),
        new Preset("Perlin noise blob", "perlin(1,x*2,y*2,z*2,1,3,0.5)>0.1"),
        new Preset("Voronoi cells", "voronoi(1,x*4,y*4,z*4,1,1)>0.2"),
        new Preset("Checkerboard", "(floor(x*4)+floor(y*4)+floor(z*4))%2==0"),
        new Preset("Helix", "(x-cos(y*6.28)*0.6)^2+(z-sin(y*6.28)*0.6)^2<0.05")
    );

    static final List<Preset> DEFORM = List.of(
        new Preset("Sink by 0.2", "y-=0.2"),
        new Preset("Raise by 0.2", "y+=0.2"),
        new Preset("Stretch wider", "x*=0.8;z*=0.8"),
        new Preset("Wave", "y+=sin(x*3.14159)*0.2"),
        new Preset("Twist", "a=atan2(z,x)+y*0.5;r=sqrt(x^2+z^2);x=r*cos(a);z=r*sin(a)"),
        new Preset("Bend", "y+=x^2*0.3"),
        new Preset("Mirror X", "x=-x")
    );

    static final List<Preset> GENERATE_BIOME = List.of(
        new Preset("Sphere", "x^2+y^2+z^2<1"),
        new Preset("Cylinder (vertical)", "x^2+z^2<1"),
        new Preset("Left half", "x<0")
    );

    static final List<Preset> TREES = List.of(
        new Preset("Oak", "tree"),
        new Preset("Large oak", "bigtree"),
        new Preset("Birch", "birch"),
        new Preset("Spruce", "redwood"),
        new Preset("Tall spruce", "tallredwood"),
        new Preset("Jungle", "jungle"),
        new Preset("Acacia", "acacia"),
        new Preset("Dark oak", "darkoak"),
        new Preset("Mangrove", "mangrove"),
        new Preset("Cherry", "cherry"),
        new Preset("Pale oak", "pale_oak"),
        new Preset("Random", "random")
    );

    static final List<Preset> SIDE_EFFECTS = List.of(
        new Preset("lighting", "lighting"),
        new Preset("neighbors", "neighbors"),
        new Preset("update", "update"),
        new Preset("validation", "validation"),
        new Preset("entity_ai", "entity_ai"),
        new Preset("events", "events")
    );

    /** Presets for a command, keyed by argument name; empty if the command has none. */
    static Map<String, List<Preset>> forCommand(String path) {
        return switch (path) {
            case "//generate", "//gen", "//g" -> Map.of("expression", GENERATE_SHAPES);
            case "//generatebiome" -> Map.of("expression", GENERATE_BIOME);
            case "//deform" -> Map.of("expression", DEFORM);
            case "//forest", "/forestgen", "/tool tree" -> Map.of("type", TREES);
            case "//perf" -> Map.of("sideEffect", SIDE_EFFECTS);
            default -> Map.of();
        };
    }
}
