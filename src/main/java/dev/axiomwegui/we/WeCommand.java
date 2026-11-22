package dev.axiomwegui.we;

import java.util.List;
import java.util.Locale;

/**
 * A WorldEdit command or sub-command.
 *
 * @param path        what the player types, e.g. {@code //set} or {@code //brush sphere}
 * @param aliases     other names of the last path element
 * @param description one-line help from WorldEdit
 * @param params      arguments and flags, in WorldEdit's order
 * @param children    sub-commands (for {@code //brush}, {@code /tool}, {@code //schem}, ...)
 * @param groupPath   for a sub-command of a group that takes its own arguments ({@code /brush apply}), the
 *                    group's path; otherwise null
 * @param groupParams the group's arguments, typed between the group name and this sub-command's name
 *                    ({@code /brush apply <shape> [radius] forest <type>}); empty if none
 */
public record WeCommand(String path, List<String> aliases, String description, List<WeParam> params,
                        List<WeCommand> children, String groupPath, List<WeParam> groupParams) {

    /** The last word of the path: the name of this (sub-)command. */
    public String name() {
        int space = path.lastIndexOf(' ');
        return space >= 0 ? path.substring(space + 1) : path;
    }

    /** True if this command or one of its sub-commands matches the lower-case query. */
    public boolean matches(String query) {
        if (matchesSelf(query)) {
            return true;
        }
        for (WeCommand child : children) {
            if (child.matches(query)) {
                return true;
            }
        }
        return false;
    }

    /** True if this command's own path, alias or description contains the lower-case query. */
    public boolean matchesSelf(String query) {
        if (query.isEmpty()) {
            return true;
        }
        if (path.toLowerCase(Locale.ROOT).contains(query) || description.toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        for (String alias : aliases) {
            if (alias.toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Runnable commands in this subtree that the query finds: all of them when this command itself matches,
     * otherwise only the matching sub-commands.
     */
    public int matchingLeafCount(String query) {
        if (matchesSelf(query)) {
            return leafCount();
        }
        int n = 0;
        for (WeCommand child : children) {
            n += child.matchingLeafCount(query);
        }
        return n;
    }

    /** Number of runnable commands in this subtree (a group with sub-commands counts its leaves). */
    public int leafCount() {
        if (children.isEmpty()) {
            return 1;
        }
        int n = 0;
        for (WeCommand child : children) {
            n += child.leafCount();
        }
        return n;
    }
}
