package dev.axiomwegui.ui;

import dev.axiomwegui.AxiomWorldEditGui;
import imgui.moulberry92.ImDrawList;
import imgui.moulberry92.ImVec2;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * A one-colour icon from an SVG file in {@code assets/axiom_worldedit_gui/icons/}, drawn with ImGui lines
 * and fills so it stays sharp at any UI scale and takes the text colour of the theme.
 *
 * <p>Supported: {@code path} (all commands, arcs included), {@code line}, {@code polyline}, {@code polygon},
 * {@code circle}, {@code ellipse}, {@code rect}, nested in {@code g}; {@code fill}, {@code stroke},
 * {@code stroke-width} as attributes or in {@code style}. Colours are ignored: anything not "none" is drawn
 * in the icon colour. No transforms, text, gradients or masks.
 */
final class VectorIcon {
    private static final int CURVE_SEGMENTS = 12;
    private static final int CIRCLE_SEGMENTS = 32;
    private static final int DRAW_CLOSED = 1;
    private static final Map<String, Optional<VectorIcon>> CACHE = new HashMap<>();

    private record Shape(float[] points, boolean closed, boolean fill, boolean stroke, float strokeWidth) {
    }

    private final List<Shape> shapes;
    private final float minX;
    private final float minY;
    private final float size;

    private VectorIcon(List<Shape> shapes, float minX, float minY, float size) {
        this.shapes = shapes;
        this.minX = minX;
        this.minY = minY;
        this.size = size;
    }

    /** The icon named {@code name}.svg, or null if there is none (or it cannot be read). */
    static VectorIcon get(String name) {
        return CACHE.computeIfAbsent(name, VectorIcon::load).orElse(null);
    }

    /** Draws the icon into the square at (x, y) with side {@code side}. */
    void draw(ImDrawList draw, float x, float y, float side, int color) {
        float scale = side / size;
        for (Shape shape : shapes) {
            int n = shape.points.length / 2;
            if (n < 2) {
                continue;
            }
            ImVec2[] pts = new ImVec2[n];
            for (int i = 0; i < n; i++) {
                pts[i] = new ImVec2(x + (shape.points[i * 2] - minX) * scale, y + (shape.points[i * 2 + 1] - minY) * scale);
            }
            if (shape.fill && n >= 3) {
                draw.addConcavePolyFilled(pts, n, color);
            }
            if (shape.stroke) {
                draw.addPolyline(pts, n, color, shape.closed ? DRAW_CLOSED : 0, Math.max(1f, shape.strokeWidth * scale));
            }
        }
    }

    // ------------------------------------------------------------------ loading

    private static Optional<VectorIcon> load(String name) {
        String resource = "/assets/axiom_worldedit_gui/icons/" + name + ".svg";
        try (InputStream in = VectorIcon.class.getResourceAsStream(resource)) {
            if (in == null) {
                return Optional.empty();
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Element root = builder.parse(in).getDocumentElement();
            float[] box = {0, 0, 32, 32};
            String viewBox = root.getAttribute("viewBox");
            if (!viewBox.isBlank()) {
                String[] parts = viewBox.trim().split("[\\s,]+");
                for (int i = 0; i < 4 && i < parts.length; i++) {
                    box[i] = Float.parseFloat(parts[i]);
                }
            }
            List<Shape> shapes = new ArrayList<>();
            walk(root, new Style(true, false, 1), shapes);
            return Optional.of(new VectorIcon(shapes, box[0], box[1], Math.max(box[2], box[3])));
        } catch (Exception e) {
            AxiomWorldEditGui.LOGGER.warn("Could not read icon {}", resource, e);
            return Optional.empty();
        }
    }

    private record Style(boolean fill, boolean stroke, float strokeWidth) {
        Style apply(Element e) {
            Map<String, String> props = new HashMap<>();
            for (String key : new String[] {"fill", "stroke", "stroke-width"}) {
                if (e.hasAttribute(key)) {
                    props.put(key, e.getAttribute(key).trim());
                }
            }
            for (String decl : e.getAttribute("style").split(";")) {
                int colon = decl.indexOf(':');
                if (colon > 0) {
                    props.put(decl.substring(0, colon).trim(), decl.substring(colon + 1).trim());
                }
            }
            boolean f = props.containsKey("fill") ? !props.get("fill").equals("none") : fill;
            boolean s = props.containsKey("stroke") ? !props.get("stroke").equals("none") : stroke;
            float w = strokeWidth;
            if (props.containsKey("stroke-width")) {
                try {
                    w = Float.parseFloat(props.get("stroke-width").replace("px", ""));
                } catch (NumberFormatException ignored) {
                    // keep the inherited width
                }
            }
            return new Style(f, s, w);
        }
    }

    private static void walk(Element element, Style inherited, List<Shape> out) {
        Style style = inherited.apply(element);
        switch (element.getTagName()) {
            case "path" -> parsePath(element.getAttribute("d"), style, out);
            case "line" -> add(out, style, false, num(element, "x1"), num(element, "y1"), num(element, "x2"), num(element, "y2"));
            case "polyline", "polygon" -> {
                float[] p = numbers(element.getAttribute("points"));
                add(out, style, element.getTagName().equals("polygon"), p);
            }
            case "circle" -> ellipse(out, style, num(element, "cx"), num(element, "cy"), num(element, "r"), num(element, "r"));
            case "ellipse" -> ellipse(out, style, num(element, "cx"), num(element, "cy"), num(element, "rx"), num(element, "ry"));
            case "rect" -> {
                float x = num(element, "x");
                float y = num(element, "y");
                float w = num(element, "width");
                float h = num(element, "height");
                add(out, style, true, x, y, x + w, y, x + w, y + h, x, y + h);
            }
            default -> {
            }
        }
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element e) {
                walk(e, style, out);
            }
        }
    }

    private static void add(List<Shape> out, Style style, boolean closed, float... points) {
        if (points.length >= 4 && (style.fill || style.stroke)) {
            out.add(new Shape(points, closed, style.fill && closed, style.stroke, style.strokeWidth));
        }
    }

    private static void ellipse(List<Shape> out, Style style, float cx, float cy, float rx, float ry) {
        float[] p = new float[CIRCLE_SEGMENTS * 2];
        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            double a = Math.PI * 2 * i / CIRCLE_SEGMENTS;
            p[i * 2] = (float) (cx + Math.cos(a) * rx);
            p[i * 2 + 1] = (float) (cy + Math.sin(a) * ry);
        }
        add(out, style, true, p);
    }

    private static float num(Element e, String attribute) {
        String value = e.getAttribute(attribute).trim();
        return value.isEmpty() ? 0 : Float.parseFloat(value.replace("px", ""));
    }

    private static float[] numbers(String text) {
        List<Float> values = new ArrayList<>();
        new Tokens(text).forEachNumber(values::add);
        float[] out = new float[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i);
        }
        return out;
    }

    // ------------------------------------------------------------------ path data

    /** Splits path data into command letters and numbers ("M10-5.5.5" is M, 10, -5.5, .5). */
    private static final class Tokens {
        private final String s;
        private int i = 0;

        Tokens(String s) {
            this.s = s == null ? "" : s;
        }

        private void skip() {
            while (i < s.length() && (Character.isWhitespace(s.charAt(i)) || s.charAt(i) == ',')) {
                i++;
            }
        }

        boolean atEnd() {
            skip();
            return i >= s.length();
        }

        boolean atCommand() {
            skip();
            return i < s.length() && Character.isLetter(s.charAt(i)) && s.charAt(i) != 'e' && s.charAt(i) != 'E';
        }

        char command() {
            skip();
            return s.charAt(i++);
        }

        /** Arc flags may be written without separators ("a1 1 0 01.5.5"). */
        float flag() {
            skip();
            char c = s.charAt(i++);
            return c == '1' ? 1 : 0;
        }

        float number() {
            skip();
            int start = i;
            if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) {
                i++;
            }
            boolean dot = false;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (Character.isDigit(c)) {
                    i++;
                } else if (c == '.' && !dot) {
                    dot = true;
                    i++;
                } else if ((c == 'e' || c == 'E') && i + 1 < s.length()) {
                    i++;
                    if (s.charAt(i) == '-' || s.charAt(i) == '+') {
                        i++;
                    }
                } else {
                    break;
                }
            }
            if (start == i) {
                throw new IllegalArgumentException("Number expected at " + i + " in " + s);
            }
            return Float.parseFloat(s.substring(start, i));
        }

        void forEachNumber(java.util.function.Consumer<Float> consumer) {
            while (!atEnd()) {
                consumer.accept(number());
            }
        }
    }

    private static void parsePath(String d, Style style, List<Shape> out) {
        Tokens t = new Tokens(d);
        List<Float> pts = new ArrayList<>();
        float x = 0;
        float y = 0;
        float startX = 0;
        float startY = 0;
        float ctrlX = 0;
        float ctrlY = 0;
        char last = ' ';
        char cmd = ' ';
        while (!t.atEnd()) {
            if (t.atCommand()) {
                cmd = t.command();
            } else if (cmd == 'M') {
                cmd = 'L';
            } else if (cmd == 'm') {
                cmd = 'l';
            }
            boolean rel = Character.isLowerCase(cmd);
            float ox = rel ? x : 0;
            float oy = rel ? y : 0;
            switch (Character.toUpperCase(cmd)) {
                case 'M' -> {
                    flush(out, style, pts, false);
                    x = ox + t.number();
                    y = oy + t.number();
                    startX = x;
                    startY = y;
                    pts.add(x);
                    pts.add(y);
                }
                case 'L' -> {
                    x = ox + t.number();
                    y = oy + t.number();
                    pts.add(x);
                    pts.add(y);
                }
                case 'H' -> {
                    x = ox + t.number();
                    pts.add(x);
                    pts.add(y);
                }
                case 'V' -> {
                    y = oy + t.number();
                    pts.add(x);
                    pts.add(y);
                }
                case 'C', 'S' -> {
                    float x1;
                    float y1;
                    if (Character.toUpperCase(cmd) == 'C') {
                        x1 = ox + t.number();
                        y1 = oy + t.number();
                    } else {
                        boolean smooth = "CcSs".indexOf(last) >= 0;
                        x1 = smooth ? 2 * x - ctrlX : x;
                        y1 = smooth ? 2 * y - ctrlY : y;
                    }
                    float x2 = ox + t.number();
                    float y2 = oy + t.number();
                    float ex = ox + t.number();
                    float ey = oy + t.number();
                    cubic(pts, x, y, x1, y1, x2, y2, ex, ey);
                    ctrlX = x2;
                    ctrlY = y2;
                    x = ex;
                    y = ey;
                }
                case 'Q', 'T' -> {
                    float x1;
                    float y1;
                    if (Character.toUpperCase(cmd) == 'Q') {
                        x1 = ox + t.number();
                        y1 = oy + t.number();
                    } else {
                        boolean smooth = "QqTt".indexOf(last) >= 0;
                        x1 = smooth ? 2 * x - ctrlX : x;
                        y1 = smooth ? 2 * y - ctrlY : y;
                    }
                    float ex = ox + t.number();
                    float ey = oy + t.number();
                    cubic(pts, x, y, x + 2f / 3 * (x1 - x), y + 2f / 3 * (y1 - y),
                        ex + 2f / 3 * (x1 - ex), ey + 2f / 3 * (y1 - ey), ex, ey);
                    ctrlX = x1;
                    ctrlY = y1;
                    x = ex;
                    y = ey;
                }
                case 'A' -> {
                    float rx = t.number();
                    float ry = t.number();
                    float rotation = t.number();
                    boolean large = t.flag() == 1;
                    boolean sweep = t.flag() == 1;
                    float ex = ox + t.number();
                    float ey = oy + t.number();
                    arc(pts, x, y, rx, ry, rotation, large, sweep, ex, ey);
                    x = ex;
                    y = ey;
                }
                case 'Z' -> {
                    flush(out, style, pts, true);
                    x = startX;
                    y = startY;
                }
                default -> throw new IllegalArgumentException("Unsupported path command " + cmd);
            }
            last = cmd;
        }
        flush(out, style, pts, false);
    }

    private static void flush(List<Shape> out, Style style, List<Float> pts, boolean closed) {
        if (pts.size() >= 4) {
            float[] p = new float[pts.size()];
            for (int i = 0; i < p.length; i++) {
                p[i] = pts.get(i);
            }
            // A path that is not closed still gets filled if it has a fill (SVG closes it implicitly).
            out.add(new Shape(p, closed, style.fill && p.length >= 6, style.stroke, style.strokeWidth));
        }
        pts.clear();
    }

    private static void cubic(List<Float> pts, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3) {
        for (int i = 1; i <= CURVE_SEGMENTS; i++) {
            float t = i / (float) CURVE_SEGMENTS;
            float u = 1 - t;
            pts.add(u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3);
            pts.add(u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3);
        }
    }

    /** SVG elliptical arc, endpoint to centre form (SVG 1.1, appendix F.6.5). */
    private static void arc(List<Float> pts, float x1, float y1, float rx, float ry, float angleDeg, boolean large,
                            boolean sweep, float x2, float y2) {
        if (rx == 0 || ry == 0) {
            pts.add(x2);
            pts.add(y2);
            return;
        }
        double phi = Math.toRadians(angleDeg);
        double cos = Math.cos(phi);
        double sin = Math.sin(phi);
        double dx = (x1 - x2) / 2.0;
        double dy = (y1 - y2) / 2.0;
        double x1p = cos * dx + sin * dy;
        double y1p = -sin * dx + cos * dy;
        double arx = Math.abs(rx);
        double ary = Math.abs(ry);
        double lambda = (x1p * x1p) / (arx * arx) + (y1p * y1p) / (ary * ary);
        if (lambda > 1) {
            arx *= Math.sqrt(lambda);
            ary *= Math.sqrt(lambda);
        }
        double num = arx * arx * ary * ary - arx * arx * y1p * y1p - ary * ary * x1p * x1p;
        double den = arx * arx * y1p * y1p + ary * ary * x1p * x1p;
        double coef = (large != sweep ? 1 : -1) * Math.sqrt(Math.max(0, num / den));
        double cxp = coef * arx * y1p / ary;
        double cyp = -coef * ary * x1p / arx;
        double cx = cos * cxp - sin * cyp + (x1 + x2) / 2.0;
        double cy = sin * cxp + cos * cyp + (y1 + y2) / 2.0;
        double start = Math.atan2((y1p - cyp) / ary, (x1p - cxp) / arx);
        double end = Math.atan2((-y1p - cyp) / ary, (-x1p - cxp) / arx);
        double delta = end - start;
        if (sweep && delta < 0) {
            delta += Math.PI * 2;
        } else if (!sweep && delta > 0) {
            delta -= Math.PI * 2;
        }
        int segments = Math.max(4, (int) Math.ceil(Math.abs(delta) / (Math.PI * 2) * CIRCLE_SEGMENTS));
        for (int i = 1; i <= segments; i++) {
            double a = start + delta * i / segments;
            double ex = arx * Math.cos(a);
            double ey = ary * Math.sin(a);
            pts.add((float) (cos * ex - sin * ey + cx));
            pts.add((float) (sin * ex + cos * ey + cy));
        }
    }
}
