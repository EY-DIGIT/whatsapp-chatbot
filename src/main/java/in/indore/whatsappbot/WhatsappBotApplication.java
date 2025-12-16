package in.indore.whatsappbot;

import in.indore.whatsappbot.config.WhatsappConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(WhatsappConfig.class)
public class WhatsappBotApplication {
    public static void main(String[] args) {
        SpringApplication.run(WhatsappBotApplication.class, args);
    }
}
