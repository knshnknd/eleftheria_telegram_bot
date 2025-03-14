package ru.knshnkn.eleftheria.service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.knshnkn.eleftheria.bot.EleftheriaBot;
import ru.knshnkn.eleftheria.jpa.entity.BotEntity;
import ru.knshnkn.eleftheria.jpa.entity.Client;
import ru.knshnkn.eleftheria.jpa.repository.BotRepository;
import ru.knshnkn.eleftheria.jpa.repository.ClientRepository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class BotCreationService {

    private final ClientRepository clientRepository;
    private final BotRepository botRepository;
    private final UserBotService userBotService;

    private static final List<String> ADJECTIVES = Arrays.asList("Смелый", "Умный", "Быстрый", "Могучий", "Тихий", "Грозный");
    private static final List<String> NOUNS = Arrays.asList("Орёл", "Тигр", "Волк", "Лев", "Медведь", "Барс");
    private static final Random RANDOM = new Random();

    @Transactional
    public void startCreationProcess(EleftheriaBot bot, String chatId) {
        List<BotEntity> bots = botRepository.findByCreatorChatId(chatId);
        if (bots.size() >= 10) {
            bot.sendMessage(chatId, "Нельзя создать больше 10 ботов на аккаунт!");
            return;
        }

        Client client = clientRepository.findById(chatId).orElseGet(() -> {
            Client c = new Client();
            c.setCreator_chat_id(chatId);
            return c;
        });
        client.setFabric_status("ОЖИДАЮ ТОКЕН");
        clientRepository.save(client);

        bot.sendMessage(chatId, "Пришлите токен бота в ответном сообщении. Отменить: /cancel.");
    }

    private String generateUniqueRandomName() {
        String randomName;
        do {
            String adjective = ADJECTIVES.get(RANDOM.nextInt(ADJECTIVES.size()));
            String noun = NOUNS.get(RANDOM.nextInt(NOUNS.size()));
            int number = RANDOM.nextInt(1000);
            randomName = adjective + " " + noun + " " + number;
        } while(botRepository.existsByRandomName(randomName));
        return randomName;
    }

    @Transactional
    public void saveTokenAndStartBot(String chatId, String token) {
        Client client = clientRepository.findById(chatId).orElseThrow();
        client.setFabric_status(null);
        clientRepository.save(client);

        BotEntity bot = new BotEntity();
        bot.setCreatorChatId(chatId);
        bot.setToken(token);
        bot.setStatus("CREATED");
        bot.setCreated_at(LocalDateTime.now());
        bot.setUpdated_at(LocalDateTime.now());
        String randomName = generateUniqueRandomName();
        bot.setRandomName(randomName);
        botRepository.save(bot);

        userBotService.startUserBot(bot);
    }

    @Transactional
    public void setPersonal(Long botId, String chatId) {
        BotEntity bot = botRepository.findById(botId).orElseThrow();
        bot.setStatus("ACTIVE");
        bot.setAdmin_chat_id(null);

        botRepository.save(bot);
    }

    @Transactional
    public void waitForAdminChatId(String chatId) {
        Client client = clientRepository.findById(chatId).orElseThrow();
        client.setFabric_status("ОЖИДАЮ ADMIN_CHAT_ID");
        clientRepository.save(client);
    }

    @Transactional
    public void setAdminChatId(String userChatId, String groupChatId) {
        BotEntity bot = botRepository.findByCreatorChatId(userChatId).stream()
                .filter(b -> "CREATED".equals(b.getStatus()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No created bot for the current user!"));

        bot.setAdmin_chat_id(groupChatId);
        bot.setStatus("ACTIVE");
        botRepository.save(bot);

        Client client = clientRepository.findById(userChatId).orElseThrow();
        client.setFabric_status(null);
        clientRepository.save(client);
    }
}