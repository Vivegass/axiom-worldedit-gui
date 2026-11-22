package dev.axiomwegui.we;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.platform.PlatformManager;
import com.sk89q.worldedit.util.formatting.WorldEditText;
import com.sk89q.worldedit.util.formatting.text.Component;
import dev.axiomwegui.AxiomWorldEditGui;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import org.enginehub.piston.Command;
import org.enginehub.piston.inject.Key;
import org.enginehub.piston.part.ArgAcceptingCommandFlag;
import org.enginehub.piston.part.ArgAcceptingCommandPart;
import org.enginehub.piston.part.CommandArgument;
import org.enginehub.piston.part.CommandFlag;
import org.enginehub.piston.part.CommandPart;
import org.enginehub.piston.part.SubCommandPart;

/**
 * Reads the list of WorldEdit commands, with their arguments, defaults and flags, from WorldEdit's
 * registered command tree. The Piston annotations are not kept in the jar, but the tree they generate is,
 * so the schema always matches the installed WorldEdit version.
 */
public final class WeSchema {
    public enum Status { MULTIPLAYER, STARTING, READY }

    private static List<WeCommand> cached = null;
    private static MinecraftServer cachedFor = null;
    private static String cachedLanguage = null;

    private WeSchema() {
    }

    public static Status status() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.hasSingleplayerServer()) {
            return Status.MULTIPLAYER;
        }
        PlatformManager platforms = WorldEdit.getInstance().getPlatformManager();
        return platforms.isInitialized() ? Status.READY : Status.STARTING;
    }

    public static String worldEditVersion() {
        return WorldEdit.getVersion();
    }

    /** Commands sorted by path; empty until {@link #status()} is READY. Rebuilt when the world or the language changes. */
    public static List<WeCommand> commands() {
        if (status() != Status.READY) {
            return List.of();
        }
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        String language = mc.getLanguageManager().getSelected();
        if (cached == null || cachedFor != server || !language.equals(cachedLanguage)) {
            cached = build(locale(language));
            cachedFor = server;
            cachedLanguage = language;
            AxiomWorldEditGui.LOGGER.info("Read {} WorldEdit commands", cached.stream().mapToInt(WeCommand::leafCount).sum());
        }
        return cached;
    }

    private static List<WeCommand> build(Locale locale) {
        List<WeCommand> result = new ArrayList<>();
        WorldEdit.getInstance().getPlatformManager().getPlatformCommandManager().getCommandManager()
            .getAllCommands()
            .forEach(c -> result.add(convert(c, "/", null, List.of(), locale)));
        result.sort(Comparator.comparing(WeCommand::path));
        return List.copyOf(result);
    }

    private static WeCommand convert(Command command, String prefix, String groupPath, List<WeParam> groupParams,
                                     Locale locale) {
        String path = prefix + command.getName();
        List<WeParam> params = new ArrayList<>();
        List<WeCommand> children = new ArrayList<>();
        for (CommandPart part : command.getParts()) {
            if (part instanceof SubCommandPart sub) {
                // Arguments before the sub-command word belong to the group (e.g. //brush apply <shape>).
                List<WeParam> ownSoFar = List.copyOf(params);
                for (Command child : sub.getCommands()) {
                    children.add(convert(child, path + " ", ownSoFar.isEmpty() ? null : path, ownSoFar, locale));
                }
            } else if (part instanceof CommandFlag flag) {
                boolean takesValue = part instanceof ArgAcceptingCommandFlag;
                params.add(new WeParam(
                    takesValue ? WeParam.Kind.VALUE_FLAG : WeParam.Kind.SWITCH,
                    "-" + flag.getName(),
                    takesValue ? typeName((ArgAcceptingCommandPart) part) : "boolean",
                    takesValue ? qualifier((ArgAcceptingCommandPart) part) : "",
                    takesValue ? List.copyOf(((ArgAcceptingCommandPart) part).getDefaults()) : List.of(),
                    false, false,
                    text(part.getDescription(), locale)));
            } else if (part instanceof CommandArgument arg) {
                params.add(new WeParam(
                    WeParam.Kind.ARGUMENT,
                    stripBrackets(text(arg.getTextRepresentation(), locale)),
                    typeName(arg),
                    qualifier(arg),
                    List.copyOf(arg.getDefaults()),
                    arg.isRequired(),
                    arg.isVariable(),
                    text(arg.getDescription(), locale)));
            }
        }
        children.sort(Comparator.comparing(WeCommand::path));
        return new WeCommand(path, List.copyOf(command.getAliases()), text(command.getDescription(), locale),
            List.copyOf(params), List.copyOf(children), groupPath, groupParams);
    }

    private static String typeName(ArgAcceptingCommandPart part) {
        for (Key<?> key : part.getTypes()) {
            // "java.util.List<java.lang.Double>" -> "List<Double>"
            return key.getTypeToken().getType().getTypeName().replaceAll("\\b[a-z0-9_]+\\.", "");
        }
        return "?";
    }

    private static String qualifier(ArgAcceptingCommandPart part) {
        for (Key<?> key : part.getTypes()) {
            Class<?> annotation = key.getAnnotationType();
            return annotation == null ? "" : annotation.getSimpleName();
        }
        return "";
    }

    private static String stripBrackets(String s) {
        String t = s.trim();
        while (!t.isEmpty() && "<[".indexOf(t.charAt(0)) >= 0) {
            t = t.substring(1);
        }
        while (!t.isEmpty() && ">]".indexOf(t.charAt(t.length() - 1)) >= 0) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    private static String text(Component component, Locale locale) {
        try {
            return WorldEditText.reduceToText(component, locale);
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static Locale locale(String mcLanguage) {
        return Locale.forLanguageTag(mcLanguage.replace('_', '-'));
    }
}
