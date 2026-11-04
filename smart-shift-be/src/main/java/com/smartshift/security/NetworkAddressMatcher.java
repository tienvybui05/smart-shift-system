package com.smartshift.security;

import org.springframework.security.web.util.matcher.IpAddressMatcher;
import java.net.InetAddress;

/** Numeric addresses only: no DNS lookup of values supplied by a client. */
public final class NetworkAddressMatcher {
    private NetworkAddressMatcher() {}

    public static String literal(String value) {
        if (value == null || value.isBlank()) return null;
        String ip = value.trim();
        if (ip.length() > 45 || !ip.matches("[0-9a-fA-F:.]+")) return null;
        if (!ip.contains(":")) {
            String[] parts = ip.split("\\.", -1);
            if (parts.length != 4) return null;
            for (String part : parts) {
                if (!part.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(part) > 255) return null;
            }
        }
        try { return InetAddress.getByName(ip).getHostAddress(); }
        catch (Exception ignored) { return null; }
    }

    public static boolean matches(String ip, String patterns) {
        String address = literal(ip);
        if (address == null || patterns == null) return false;
        for (String entry : patterns.split("[,;]")) {
            String pattern = entry.trim();
            if (pattern.equalsIgnoreCase("localhost")) pattern = "127.0.0.1";
            if (pattern.endsWith(".*")) {
                String[] parts = pattern.substring(0, pattern.length()-2).split("\\.");
                if (parts.length < 1 || parts.length > 3) continue;
                pattern = String.join(".", parts) + ".0".repeat(4-parts.length) + "/" + (8*parts.length);
            }
            String[] cidr = pattern.split("/", -1);
            if (cidr.length > 2 || literal(cidr[0]) == null) continue;
            try {
                if (new IpAddressMatcher(pattern).matches(address)) return true;
            } catch (IllegalArgumentException ignored) { /* Invalid location configuration never verifies a network. */ }
        }
        return false;
    }
}
