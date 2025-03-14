package ru.knshnkn.eleftheria.bot;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "bot")
public class BotConfiguration {

    private String token;
    private String techChatId;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTechChatId() {
        return techChatId;
    }

    public void setTechChatId(String techChatId) {
        this.techChatId = techChatId;
    }
}
