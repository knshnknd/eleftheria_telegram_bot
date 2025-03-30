package ru.knshnkn.eleftheria.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.knshnkn.eleftheria.bot.UserBot;
import ru.knshnkn.eleftheria.jpa.entity.BotEntity;
import ru.knshnkn.eleftheria.jpa.repository.BanListRepository;
import ru.knshnkn.eleftheria.jpa.repository.BotRepository;

@Service
@RequiredArgsConstructor
public class UserBotService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserBotService.class);

    private final BotRepository botRepository;
    private final BanListRepository banListRepository;
    private final BanService banService;

    private static final TelegramBotsLongPollingApplication userBotsApplication = new TelegramBotsLongPollingApplication();

    @Async("customExecutor")
    public void startUserBot(BotEntity bot) {
        try {
            LOGGER.info("Starting bot for user={}, botId={}, token={}",
                    bot.getCreatorChatId(), bot.getId(), bot.getToken());

            UserBot userBot = new UserBot(bot.getId(), bot.getToken(), botRepository, banListRepository, banService);
            userBotsApplication.registerBot(bot.getToken(), userBot);

        } catch (TelegramApiException ex) {
            LOGGER.error("Error registering user bot: {}", ex.getMessage());
        }
    }

    public void stopUserBot(BotEntity bot) {
        try {
            LOGGER.info("Stopping bot with id={}, token={}", bot.getId(), bot.getToken());

            userBotsApplication.unregisterBot(bot.getToken());

        } catch (TelegramApiException ex) {
            LOGGER.error("Error unregistering user bot: {}", ex.getMessage());
        }
    }
}