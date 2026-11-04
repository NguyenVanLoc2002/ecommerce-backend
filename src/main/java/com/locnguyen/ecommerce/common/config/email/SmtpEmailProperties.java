package com.locnguyen.ecommerce.common.config.email;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.email.smtp")
public class SmtpEmailProperties {

    private boolean enabled = false;
    private String host = "";
    private int port = 587;
    private String username = "";
    private String password = "";
    private String from = "";
    private boolean auth = true;
    private boolean starttlsEnabled = true;
    private int connectionTimeoutMs = 10_000;
    private int timeoutMs = 10_000;
    private int writeTimeoutMs = 10_000;
}
