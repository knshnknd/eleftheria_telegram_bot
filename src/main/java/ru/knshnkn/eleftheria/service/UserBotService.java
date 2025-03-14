package ru.knshnkn.eleftheria.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.knshnkn.eleftheria.bot.UserBot;
import ru.knshnkn.eleftheria.jpa.entity.BotEntity;
import ru.knshnkn.eleftheria.jpa.repository.BotRepository;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Service
@RequiredArgsConstructor
public class UserBotService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserBotService.class);

    private final BotRepository botRepository;

    @Async("customExecutor")
    public void startUserBot(BotEntity bot) {
        try {
            LOGGER.info("Starting bot for user {}", bot.getCreatorChatId());

            UserBot userBot = new UserBot(bot.getId(), bot.getToken(), botRepository);
            TelegramBotsLongPollingApplication userBotsApplication = new TelegramBotsLongPollingApplication();

            userBotsApplication.registerBot(bot.getToken(), userBot);
        } catch (TelegramApiException ex) {
            LOGGER.error("Error registering user bot: {}", ex.getMessage());
        }
    }
}