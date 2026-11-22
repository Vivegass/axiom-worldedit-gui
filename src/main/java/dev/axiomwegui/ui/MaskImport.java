package dev.axiomwegui.ui;

import com.moulberry.axiom.mask.CmdStringConverter;
import com.moulberry.axiom.mask.MaskElement;
import com.moulberry.axiom.mask.elements.AllMaskElement;
import com.moulberry.axiom.mask.elements.AnyMaskElement;
import com.moulberry.axiom.mask.elements.BiomeConditionMaskElement;
import com.moulberry.axiom.mask.elements.BlockAboveConditionMaskElement;
import com.moulberry.axiom.mask.elements.BlockAdjacentConditionMaskElement;
import com.moulberry.axiom.mask.elements.BlockBelowConditionMaskElement;
import com.moulberry.axiom.mask.elements.BlockConditionMaskElement;
import com.moulberry.axiom.mask.elements.BothMaskElement;
import com.moulberry.axiom.mask.elements.ConstantMaskElement;
import com.moulberry.axiom.mask.elements.CoordMaskElement;
import com.moulberry.axiom.mask.elements.EitherMaskElement;
import com.moulberry.axiom.mask.elements.GenericBlockConditionMaskElement;
import com.moulberry.axiom.mask.elements.NotMaskElement;
import com.moulberry.axiom.mask.elements.OffsetMaskElement;
import com.moulberry.axiom.mask.elements.SelectedMaskElement;
import com.moulberry.axiom.mask.elements.SolidMaskElement;
import com.moulberry.axiom.mask.elements.SurfaceMaskElement;
import dev.axiomwegui.Tr;
import dev.axiomwegui.mixin.MaskManagerAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns the mask set in Axiom's Tool Masks window into WorldEdit mask syntax.
 * <pre>
 *   Axiom                  WorldEdit
 *   block = stone          stone
 *   below = grass_block    &gt;grass_block   (the block underneath)
 *   above = air            &lt;air
 *   adjacent = water       ~water
 *   biome = plains         $plains
 *   y &gt; 64                 =y&gt;64
 *   solid / surface        #solid / #exposed
 *   selected               #sel
 *   A &amp; B                  A B
 *   !A                     !A
 *   A | B (same kind)      merged block list
 * </pre>
 * Angle, near, neighbor, can-see-sky, Lua and general OR have no WorldEdit equivalent.
 */
final class MaskImport {
    record Result(String mask, String message) {
    }

    private MaskImport() {
    }

    private static final class Unsupported extends RuntimeException {
        Unsupported(String what) {
            super(what, null, false, false);
        }
    }

    static Result fromAxiom() {
        MaskElement element = MaskManagerAccessor.axiomwegui$getConfiguredMask();
        if (element == null) {
            return new Result(null, Tr.t("mask.axiom_empty"));
        }
        try {
            return new Result(translate(element), null);
        } catch (Unsupported e) {
            return new Result(null, Tr.t("mask.axiom_unsupported", e.getMessage()));
        } catch (RuntimeException e) {
            return new Result(null, Tr.t("mask.axiom_unsupported", e.toString()));
        }
    }

    private static String translate(MaskElement element) {
        if (element instanceof BothMaskElement both) {
            return translate(both.getChild1()) + " " + translate(both.getChild2());
        }
        if (element instanceof AllMaskElement all) {
            List<String> parts = new ArrayList<>();
            for (MaskElement child : all.getChildren()) {
                if (child != null) {
                    parts.add(translate(child));
                }
            }
            return String.join(" ", parts);
        }
        if (element instanceof EitherMaskElement either) {
            return orOf(List.of(either.getChild1(), either.getChild2()));
        }
        if (element instanceof AnyMaskElement any) {
            List<MaskElement> children = new ArrayList<>();
            for (MaskElement child : any.getChildren()) {
                if (child != null) {
                    children.add(child);
                }
            }
            return orOf(children);
        }
        if (element instanceof NotMaskElement not) {
            String inner = translate(not.getChild());
            if (inner.contains(" ")) {
                throw new Unsupported("!(" + inner + ")");
            }
            return inner.startsWith("!") ? inner.substring(1) : "!" + inner;
        }
        if (element instanceof GenericBlockConditionMaskElement condition) {
            String blocks = blockList(condition);
            if (element instanceof BlockConditionMaskElement) {
                return blocks;
            }
            if (element instanceof BlockBelowConditionMaskElement) {
                return ">" + blocks;
            }
            if (element instanceof BlockAboveConditionMaskElement) {
                return "<" + blocks;
            }
            if (element instanceof BlockAdjacentConditionMaskElement) {
                return "~" + blocks;
            }
            throw new Unsupported(condition.cmdStringName());
        }
        if (element instanceof BiomeConditionMaskElement biome) {
            var id = biome.getMatchBiome().identifier();
            return "$" + (id.getNamespace().equals("minecraft") ? id.getPath() : id.toString());
        }
        if (element instanceof CoordMaskElement coord) {
            String axis = coord.getAxis().getName().toLowerCase(Locale.ROOT);
            String op = switch (coord.getComparison()) {
                case 0 -> "==";
                case 1 -> "!=";
                case 2 -> "<";
                case 3 -> ">";
                case 4 -> "<=";
                case 5 -> ">=";
                default -> throw new Unsupported(axis);
            };
            return "=" + axis + op + coord.getValue();
        }
        if (element instanceof OffsetMaskElement offset) {
            String inner = translate(offset.getChild());
            if (offset.getOffsetX() == 0 && offset.getOffsetZ() == 0 && !inner.contains(" ")
                && !inner.startsWith(">") && !inner.startsWith("<")) {
                if (offset.getOffsetY() == -1) {
                    return ">" + inner;
                }
                if (offset.getOffsetY() == 1) {
                    return "<" + inner;
                }
            }
            throw new Unsupported("offset");
        }
        if (element instanceof SolidMaskElement) {
            return "#solid";
        }
        if (element instanceof SurfaceMaskElement) {
            return "#exposed";
        }
        if (element instanceof SelectedMaskElement) {
            return "#sel";
        }
        if (element instanceof ConstantMaskElement) {
            String s = CmdStringConverter.toCmdString(element);
            throw new Unsupported(s);
        }
        throw new Unsupported(CmdStringConverter.toCmdString(element));
    }

    /** WorldEdit can only OR block lists of the same kind, by merging them into one list. */
    private static String orOf(List<MaskElement> children) {
        String prefix = null;
        List<String> lists = new ArrayList<>();
        for (MaskElement child : children) {
            String t = translate(child);
            if (t.contains(" ") || t.startsWith("!") || t.startsWith("=") || t.startsWith("#") || t.startsWith("$")) {
                throw new Unsupported("|");
            }
            String p = t.startsWith(">") || t.startsWith("<") || t.startsWith("~") ? t.substring(0, 1) : "";
            if (prefix != null && !prefix.equals(p)) {
                throw new Unsupported("|");
            }
            prefix = p;
            lists.add(t.substring(p.length()));
        }
        return (prefix == null ? "" : prefix) + String.join(",", lists);
    }

    /** "stone", "[stone,oak_log[axis=y]]" or "#logs" in Axiom's syntax, as a WorldEdit block list. */
    private static String blockList(GenericBlockConditionMaskElement condition) {
        String s = CmdStringConverter.toCmdString((MaskElement) condition);
        int eq = s.indexOf(" = ");
        if (eq < 0) {
            eq = s.indexOf(" != ");
            if (eq < 0) {
                throw new Unsupported(s);
            }
            s = s.substring(eq + 4);
        } else {
            s = s.substring(eq + 3);
        }
        s = s.trim();
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1);
        }
        List<String> out = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                out.add(fixTag(current.toString().trim()));
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            out.add(fixTag(current.toString().trim()));
        }
        return String.join(",", out);
    }

    private static String fixTag(String token) {
        return token.startsWith("#") && !token.startsWith("##") ? "#" + token : token;
    }
}
