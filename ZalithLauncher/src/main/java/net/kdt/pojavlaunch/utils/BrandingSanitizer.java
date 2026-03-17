package net.kdt.pojavlaunch.utils;

import androidx.annotation.Nullable;

public final class BrandingSanitizer {
    private BrandingSanitizer() {}

    public static String sanitize(@Nullable String text) {
        if (text == null) return "";

        String sanitized = text;
        sanitized = sanitized.replace("com.movtery.zalithlauncher.debug", "com.trapgaint.launcher.debug");
        sanitized = sanitized.replace("com.movtery.zalithlauncher", "com.trapgaint.launcher");
        sanitized = sanitized.replace("ZalithLauncher", "Trapgaint");
        sanitized = sanitized.replace("zalithlauncher", "trapgaint");
        sanitized = sanitized.replace("Zalith", "Trapgaint");
        sanitized = sanitized.replace("zalith", "trapgaint");
        sanitized = sanitized.replace("Zainith", "Trapgaint");
        sanitized = sanitized.replace("zainith", "trapgaint");
        return sanitized;
    }
}
