package com.duka.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "duka")
public class DukaProperties {
    private Jwt jwt = new Jwt();
    private Bootstrap bootstrap = new Bootstrap();
    private Storage storage = new Storage();
    private Mpesa mpesa = new Mpesa();
    private Etims etims = new Etims();
    private Security security = new Security();
    private Platform platform = new Platform();
    private Billing billing = new Billing();

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private int ttlMinutes = 720;
    }

    @Getter
    @Setter
    public static class Bootstrap {
        private String phone = "";
        private String pin = "";
        private String name = "Owner";
    }

    @Getter
    @Setter
    public static class Storage {
        private String path = "./data/documents";
    }

    @Getter
    @Setter
    public static class Mpesa {
        private String mode = "mock";
        private String webhookSecret = "";
        private String consumerKey = "";
        private String consumerSecret = "";
        private String shortcode = "";
        private String passkey = "";
        private String callbackUrl = "";
    }

    @Getter
    @Setter
    public static class Etims {
        private String mode = "unconfigured";
        private String baseUrl = "";
        private String tin = "";
    }

    @Getter
    @Setter
    public static class Security {
        private boolean allowRegistration = false;
    }

    @Getter
    @Setter
    public static class Platform {
        private String phone = "";
        private String pin = "";
        private String name = "Platform admin";
        /** One-time server secret. Empty unless an operator is recovering a lost platform login. */
        private String recoveryCode = "";
    }

    @Getter
    @Setter
    public static class Billing {
        private String provider = "unconfigured";
        private String secretKey = "";
        private String webhookSecret = "";
        private String callbackUrl = "";
        private String webhookUrl = "";
        private String publicKey = "";
        private String apiBase = "https://api.paystack.co";
    }
}
