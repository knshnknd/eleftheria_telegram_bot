package ru.knshnkn.eleftheria.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import ru.knshnkn.eleftheria.jpa.entity.BotEntity;
import ru.knshnkn.eleftheria.jpa.repository.BotRepository;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BotStartupInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BotStartupInitializer.class);

    private final BotRepository botRepository;
    private final UserBotService userBotService;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        List<BotEntity> bots = botRepository.findByStatusIn(List.of("CREATED", "ACTIVE"));
        if (bots.isEmpty()) {
            LOGGER.info("No bots to start.");
            return;
        }
        bots.forEach(bot -> {
            LOGGER.info("Starting bot with id: {} for user: {}", bot.getId(), bot.getCreatorChatId());
            userBotService.startUserBot(bot);
        });
    }
}