package com.smartshift.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AttendanceClientIpResolver {
    private final String trustedProxies;

    public AttendanceClientIpResolver(@Value("${app.attendance.trusted-proxies:}") String trustedProxies) {
        this.trustedProxies = trustedProxies;
    }

    public String resolve(HttpServletRequest request) {
        String peer = NetworkAddressMatcher.literal(request.getRemoteAddr());
        if (!NetworkAddressMatcher.matches(peer, trustedProxies)) return peer;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.length() > 1024) return peer;
        String[] hops = forwarded.split(",", -1);
        if (hops.length > 16) return peer;
        String[] addresses = new String[hops.length];
        for (int i = 0; i < hops.length; i++) {
            addresses[i] = NetworkAddressMatcher.literal(hops[i]);
            if (addresses[i] == null) return peer;
        }
        String current = peer;
        for (int i = addresses.length-1; i >= 0 && NetworkAddressMatcher.matches(current, trustedProxies); i--) {
            current = addresses[i];
        }
        return current;
    }
}
