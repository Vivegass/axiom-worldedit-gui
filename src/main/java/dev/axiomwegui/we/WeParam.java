package dev.axiomwegui.we;

import java.util.List;

/**
 * One argument or flag of a WorldEdit command, read from WorldEdit's own command tree.
 *
 * @param kind        argument, switch flag ({@code -h}) or flag that takes a value ({@code -m <mask>})
 * @param name        argument name ({@code pattern}) or flag with dash ({@code -h})
 * @param type        simple Java type WorldEdit converts the text to, e.g. {@code Pattern}, {@code int}, {@code BlockVector3}
 * @param qualifier   annotation on the type, e.g. {@code Direction} or {@code Offset}; empty if none
 * @param defaults    default value(s) WorldEdit uses when the argument is left out
 * @param required    whether the command fails without it
 * @param variable    whether it eats the rest of the line (lists, expressions)
 * @param description help text from WorldEdit
 * @param key         WorldEdit's key for the value type, used to list its values; null for switches
 */
public record WeParam(Kind kind, String name, String type, String qualifier, List<String> defaults,
                      boolean required, boolean variable, String description,
                      org.enginehub.piston.inject.Key<?> key) {
    public enum Kind { ARGUMENT, SWITCH, VALUE_FLAG }
}
