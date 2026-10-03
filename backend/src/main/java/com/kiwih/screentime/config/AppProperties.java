package com.kiwih.screentime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Everything the deployment supplies. There is no default for any secret and
 * no default for the first parent account: the application refuses to start
 * without them rather than come up with a credential somebody could guess.
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** The zone every calendar decision is made in. Instants are stored in UTC. */
    private String timezone = "Europe/Zurich";

    private String publicUrl = "http://localhost:4200";

    private Admin admin = new Admin();

    private Jwt jwt = new Jwt();

    private Cors cors = new Cors();

    public static class Admin {
        /** From APP_ADMIN_USER. Required. */
        private String username;
        /** From APP_ADMIN_PASSWORD. Required. */
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class Jwt {
        /** From JWT_SECRET. Required, and at least 32 characters for HS256. */
        private String secret;
        /** A child who is logged out every day stops using the app. */
        private int ttlDays = 30;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public int getTtlDays() {
            return ttlDays;
        }

        public void setTtlDays(int ttlDays) {
            this.ttlDays = ttlDays;
        }
    }

    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:4200");

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public Admin getAdmin() {
        return admin;
    }

    public void setAdmin(Admin admin) {
        this.admin = admin;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }
}
