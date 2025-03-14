package ru.knshnkn.eleftheria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.knshnkn.eleftheria.bot.BotConfiguration;
import ru.knshnkn.eleftheria.bot.EleftheriaBot;

@EnableAsync
@SpringBootApplication
public class EleftheriaApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(EleftheriaApplication.class);

    public static void main(String[] args) {
        final BotConfiguration botConfiguration = new BotConfiguration();
        ConfigurableApplicationContext context = SpringApplication.run(EleftheriaApplication.class, args);
        EleftheriaBot bot = context.getBean("eleftheriaBot", EleftheriaBot.class);
        try {
            TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication();
            botsApplication.registerBot(botConfiguration.getToken(), bot);
            bot.notifyTechChat("Bot started.");
        } catch (TelegramApiException ex) {
            LOGGER.error("Error registering bot.", ex);
        }
    }
}