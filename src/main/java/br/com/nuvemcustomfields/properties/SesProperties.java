package br.com.nuvemcustomfields.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "notifications.ses")
public record SesProperties(String host, int port, String username, String password, String from) {
    public boolean configured() {
        return StringUtils.hasText(host) && port > 0 && port <= 65535
                && StringUtils.hasText(username) && StringUtils.hasText(password) && StringUtils.hasText(from);
    }

    @Override
    public String toString() {
        return "SesProperties[configured=" + configured() + "]";
    }
}
