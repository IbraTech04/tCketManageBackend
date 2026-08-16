package com.ibrasoft.tcketmanagebackend.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "tcketmanage.crypto")
@Data
public class CryptoProperties {
    private Resource privateKey = new ClassPathResource("keys/private.pem");
    private Resource publicKey = new ClassPathResource("keys/public.pem");
}
