package dev.lumora.social.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WsServerTest {
    @Test
    void realAddressFromTheProxyIsAnIpLiteralOnly() {
        assertEquals("203.0.113.5", WsServer.realAddress(" 203.0.113.5 ").getHostAddress());
        assertEquals("2001:db8:0:0:0:0:0:1", WsServer.realAddress("2001:db8::1").getHostAddress());
        assertNull(WsServer.realAddress(null), "missing header");
        assertNull(WsServer.realAddress("evil.example.com"), "names are not resolved");
        assertNull(WsServer.realAddress("1.2.3.4, 5.6.7.8"), "no lists");
        assertNull(WsServer.realAddress(""), "empty");
    }
}
