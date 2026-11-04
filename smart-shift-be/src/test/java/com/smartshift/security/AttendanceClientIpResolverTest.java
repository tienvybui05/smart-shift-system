package com.smartshift.security;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
class AttendanceClientIpResolverTest {
    private MockHttpServletRequest request(String peer,String header) {
        var r=new MockHttpServletRequest();r.setRemoteAddr(peer);if(header!=null)r.addHeader("X-Forwarded-For",header);return r;
    }
    @Test void ignoresSpoofedHeadersWithoutTrustedProxy() {
        assertEquals("203.0.113.8",new AttendanceClientIpResolver("").resolve(request("203.0.113.8","198.51.100.1")));
    }
    @Test void walksFromRightAndStopsAtFirstUntrustedHop() {
        var resolver=new AttendanceClientIpResolver("10.0.0.2/32,10.0.0.3/32");
        assertEquals("203.0.113.8",resolver.resolve(request("10.0.0.2","198.51.100.1,203.0.113.8,10.0.0.3")));
    }
    @Test void rejectsMalformedChainsInsteadOfPartiallyTrustingThem() {
        var resolver=new AttendanceClientIpResolver("10.0.0.2");
        for(String header:new String[]{"example.com","203.0.113.8,","unknown","[::1]:443","1.2.3.4,".repeat(30)}) {
            assertEquals("10.0.0.2",resolver.resolve(request("10.0.0.2",header)));
        }
    }
    @Test void supportsIpv4Ipv6CidrAndLegacyWildcardWithoutPrefixConfusion() {
        assertTrue(NetworkAddressMatcher.matches("192.168.1.20","192.168.1.*"));
        assertFalse(NetworkAddressMatcher.matches("192.168.10.20","192.168.1.*"));
        assertTrue(NetworkAddressMatcher.matches("2001:db8::5","2001:db8::/64"));
        assertFalse(NetworkAddressMatcher.matches("2001:db9::5","2001:db8::/64"));
        assertNull(NetworkAddressMatcher.literal("127.1"));
        assertNull(NetworkAddressMatcher.literal("2130706433"));
        assertNull(NetworkAddressMatcher.literal("office.example"));
        assertFalse(NetworkAddressMatcher.matches("10.0.0.1","10.0.0.1/99"));
    }
}

