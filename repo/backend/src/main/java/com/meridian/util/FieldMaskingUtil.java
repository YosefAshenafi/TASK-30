package com.meridian.util;

public final class FieldMaskingUtil {

    private FieldMaskingUtil() {
    }

    /**
     * Masks an employee ID, showing only the last 4 characters.
     * Returns null if input is null. Returns "****" if length is 4 or less.
     */
    public static String maskEmployeeId(String raw) {
        if (raw == null) {
            return null;
        }
        if (raw.length() <= 4) {
            return "****";
        }
        return "****" + raw.substring(raw.length() - 4);
    }

    /**
     * Masks an email address, showing the first 2 characters of the local part.
     * Returns null if input is null.
     */
    public static String maskEmail(String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split("@", 2);
        String localPart = parts[0];
        String domain = parts.length > 1 ? parts[1] : "";
        String prefix = localPart.length() >= 2 ? localPart.substring(0, 2) : localPart;
        return prefix + "****@" + domain;
    }

    /**
     * Masks a phone number, replacing all digits except the last 4 with '*'.
     * Returns null if input is null.
     */
    public static String maskPhone(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.replaceAll("\\d(?=\\d{4})", "*");
    }
}
