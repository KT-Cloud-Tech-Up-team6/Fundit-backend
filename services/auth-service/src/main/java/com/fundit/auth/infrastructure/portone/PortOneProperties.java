package com.fundit.auth.infrastructure.portone;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "portone")
public class PortOneProperties {

    private String baseUrl;
    private String storeId;
    private String apiSecret;
}
