package com.lauua.lauuacheats.text;

/** Applies only to our translation keys, never to arbitrary user-authored text. */
public final class TextPolicy {
    private TextPolicy() { }
    public static boolean vanillaBrand = true;
    public static String plainKey(String key) {
        return key.startsWith("key.lauuacheats.") || key.startsWith("text.lauuacheats.") ? key : null;
    }
    /** Sign and anvil contents must not be trimmed, localized or rewritten. */
    public static String signPayload(String input) { return input; }
}
