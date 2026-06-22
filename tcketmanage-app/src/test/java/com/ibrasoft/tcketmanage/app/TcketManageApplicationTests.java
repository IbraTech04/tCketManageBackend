package com.ibrasoft.tcketmanage.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Boots the full application context. Because the app activates core via
 * {@code tcketmanage.enabled=true} (set in the test {@code application.properties}), a passing
 * context-load here proves core's auto-configuration wires up correctly through the module boundary.
 */
@SpringBootTest
class TcketManageApplicationTests {

    @Test
    void contextLoads() {
    }

}
