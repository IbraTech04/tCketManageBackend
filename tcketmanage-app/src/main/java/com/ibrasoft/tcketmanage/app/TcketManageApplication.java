package com.ibrasoft.tcketmanage.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Standalone driver for the tCketManage core library.
 *
 * <p>This application contributes no business logic of its own — it exists purely to boot core as a
 * runnable service. Core is activated by {@code tcketmanage.enabled=true} in this module's
 * {@code application.properties}, which triggers
 * {@code com.ibrasoft.tcketmanage.autoconfigure.TcketManageAutoConfiguration}. Note this class sits
 * in {@code com.ibrasoft.tcketmanage.app}, so its own component scan never reaches core's
 * {@code com.ibrasoft.tcketmanagebackend} packages: core loads solely via auto-configuration,
 * exactly as it will when embedded in another host. Scheduling is enabled by the auto-configuration,
 * not here.
 */
@SpringBootApplication
public class TcketManageApplication {

    public static void main(String[] args) {
        SpringApplication.run(TcketManageApplication.class, args);
    }

}
