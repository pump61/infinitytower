package com.eternity.infinitytower.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextUtil {

    private static final Pattern HEX_1 = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern HEX_2 = Pattern.compile("#([A-Fa-f0-9]{6})");

    private TextUtil() {}

    public static String color(String input) {
        if (input == null) return "";
        String out = translateHex(input);
        out = out.replace("&", "§");
        return out;
    }

    private static String translateHex(String text) {
        text = replaceHex(text, HEX_1);
        text = replaceHex(text, HEX_2);
        return text;
    }

    private static String replaceHex(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hex = matcher.group(1);
            String replacement = "§x"
                    + "§" + hex.charAt(0) + "§" + hex.charAt(1)
                    + "§" + hex.charAt(2) + "§" + hex.charAt(3)
                    + "§" + hex.charAt(4) + "§" + hex.charAt(5);
            matcher.appendReplacement(buffer, replacement);
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    public static String applyPlaceholders(String text, int floor, int delay) {
        if (text == null) return "";
        return text.replace("{floor}", String.valueOf(floor))
                   .replace("{delay}", String.valueOf(delay));
    }
}
