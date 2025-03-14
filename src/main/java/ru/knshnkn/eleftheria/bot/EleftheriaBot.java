package ru.knshnkn.eleftheria.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import ru.knshnkn.eleftheria.jpa.entity.BotEntity;
import ru.knshnkn.eleftheria.jpa.entity.Client;
import ru.knshnkn.eleftheria.jpa.repository.BotRepository;
import ru.knshnkn.eleftheria.jpa.repository.ClientRepository;
import ru.knshnkn.eleftheria.service.BotCreationService;
import ru.knshnkn.eleftheria.service.UserBotService;

import java.util.ArrayList;
import java.util.List;

@Component
public class EleftheriaBot implements LongPollingSingleThreadUpdateConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(EleftheriaBot.class);

    private final ClientRepository clientRepository;
    private final BotRepository botRepository;
    private final BotCreationService botCreationService;
    private final UserBotService userBotService;

    private final TelegramClient tgClient = new OkHttpTelegramClient(BotConfiguration.token);

    public EleftheriaBot(ClientRepository clientRepository,
                         BotRepository botRepository,
                         BotCreationService botCreationService,
                         UserBotService userBotService
    ) {
        this.clientRepository = clientRepository;
        this.botRepository = botRepository;
        this.botCreationService = botCreationService;
        this.userBotService = userBotService;
    }

    @Override
    public void consume(org.telegram.telegrambots.meta.api.objects.Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleTextMessage(update.getMessage());
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
        }
    }

    private void handleTextMessage(Message message) {
        String text = message.getText();
        String chatId = message.getChatId().toString();

        try {
            String[] textParts = text.split(" ");
            String command = textParts[0].toLowerCase();

            switch (command) {
                case "/start", "/help" -> showStartMenu(chatId);
                case "/chat_id" -> sendMessage(chatId, chatId);
                case "/cancel" -> {
                    cancelCreation(chatId);
                    sendMessage(chatId, "Создание бота отменено.");
                    showStartMenu(chatId);
                }
                default -> handleTextMessage(chatId, text);
            }
        } catch (Exception e) {
            notifyTechChat("Error processing text.", e);
            sendMessage(chatId, "Unknown error.");
        }
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String callData = callbackQuery.getData();
        String chatId = callbackQuery.getMessage().getChatId().toString();
        int messageId = callbackQuery.getMessage().getMessageId();

        try {
            switch (callData) {
                case "CREATE_BOT" -> {
                    botCreationService.startCreationProcess(this, chatId);
                    removeKeyboard(chatId, messageId);
                }
                case "MY_BOTS" -> {
                    showUserBots(chatId);
                    removeKeyboard(chatId, messageId);
                }
                case "GOBACK" -> {
                    showStartMenu(chatId);
                    removeKeyboard(chatId, messageId);
                }
                case "CHOOSE_PERSONAL" -> {
                    Long lastBotId = getLastCreatedBotId(chatId);
                    if (lastBotId != null) {
                        botCreationService.setPersonal(lastBotId, chatId);
                        sendMessage(chatId, "Ура! Бот создан и будет присылать сообщения Вам лично.");
                    }
                    removeKeyboard(chatId, messageId);
                }
                case "CHOOSE_CHAT" -> {
                    botCreationService.waitForAdminChatId(chatId);
                    sendMessage(chatId, "Добавьте созданного бота в ваш админский чат. " +
                            "Там введите /chat_id, скопируйте ответ и пришлите этот ChatID сюда. Отменить: /cancel.");
                    removeKeyboard(chatId, messageId);
                }
                default -> {
                    if (callData.startsWith("SHOW_BOT_")) {
                        removeKeyboard(chatId, messageId);

                        String botIdStr = callData.substring("SHOW_BOT_".length());
                        Long botId = Long.valueOf(botIdStr);
                        BotEntity bot = botRepository.findById(botId).orElse(null);
                        if (bot != null) {
                            showBotDetails(chatId, bot);
                        } else {
                            sendMessage(chatId, "Бот не найден.");
                        }
                    } else if (callData.startsWith("DELETE_BOT_")) {
                        String botIdStr = callData.substring("DELETE_BOT_".length());
                        Long botId = Long.valueOf(botIdStr);
                        BotEntity bot = botRepository.findById(botId).orElse(null);
                        if (bot != null) {
                            userBotService.stopUserBot(bot);
                            botRepository.delete(bot);

                            removeKeyboard(chatId, messageId);

                            sendMessage(chatId, "Бот удалён.");
                            showStartMenu(chatId);
                        } else {
                            sendMessage(chatId, "Бот не найден.");
                        }
                    }
                }
            }
        } catch (Exception e) {
            notifyTechChat("Error processing callback.", e);
            sendMessage(chatId, "Unknown error.");
        }
    }

    // REGISTER ////

    private void handleTextMessage(String chatId, String text) {
        Client client = clientRepository.findById(chatId).orElse(null);
        if (client == null) {
            return;
        }
        if ("ОЖИДАЮ ТОКЕН".equals(client.getFabric_status())) {
            botCreationService.saveTokenAndStartBot(chatId, text);

            List<InlineKeyboardRow> keyboard = List.of(
                    new InlineKeyboardRow(
                            List.of(
                                    InlineKeyboardButton.builder().text("Лично").callbackData("CHOOSE_PERSONAL").build(),
                                    InlineKeyboardButton.builder().text("В чат").callbackData("CHOOSE_CHAT").build()
                            )
                    )
            );
            sendMessageWithInlineKeyboard(chatId,
                    "Будет ли ваш бот присылать сообщения в ЛС или в один общий админский чат?",
                    keyboard);

        } else if ("ОЖИДАЮ ADMIN_CHAT_ID".equals(client.getFabric_status())) {
            botCreationService.setAdminChatId(chatId, text);
            sendMessage(chatId, "Ура! Теперь бот активен и сообщения будут приходить в чат " + text);
        }
    }

    // MENU

    private void showStartMenu(String chatId) {
        List<InlineKeyboardRow> keyboard = List.of(
                new InlineKeyboardRow(
                        List.of(
                                InlineKeyboardButton.builder().text("Создать").callbackData("CREATE_BOT").build(),
                                InlineKeyboardButton.builder().text("Мои боты").callbackData("MY_BOTS").build()
                        )
                )
        );
        sendMessageWithInlineKeyboard(chatId, "Выберите действие:", keyboard);
    }

    private void showUserBots(String chatId) {
        List<BotEntity> bots = botRepository.findByCreatorChatId(chatId);
        if (bots.isEmpty()) {
            sendMessage(chatId, "У Вас пока нет ботов.");
            return;
        }

        sendMessage(chatId, "У вас ботов: " + bots.size() + ".");

        List<InlineKeyboardRow> keyboardRows = new ArrayList<>();
        for (BotEntity bot : bots) {
            InlineKeyboardButton btn = InlineKeyboardButton.builder()
                    .text(bot.getRandomName())
                    .callbackData("SHOW_BOT_" + bot.getId())
                    .build();

            InlineKeyboardRow row = new InlineKeyboardRow();
            row.add(btn);
            keyboardRows.add(row);
        }

        InlineKeyboardButton backBtn = InlineKeyboardButton.builder()
                .text("Назад")
                .callbackData("GOBACK")
                .build();
        InlineKeyboardRow backRow = new InlineKeyboardRow();
        backRow.add(backBtn);
        keyboardRows.add(backRow);

        sendMessageWithInlineKeyboard(chatId, "Выберите бота, чтобы посмотреть детали:", keyboardRows);
    }

    private void showBotDetails(String chatId, BotEntity bot) {
        StringBuilder sb = new StringBuilder();
        sb.append("Бот: ").append(bot.getRandomName()).append("\n");
        sb.append("Статус: ").append(bot.getStatus()).append("\n");
        sb.append("Токен: ").append(bot.getToken()).append("\n");

        List<InlineKeyboardRow> keyboardRows = new ArrayList<>();

        InlineKeyboardButton deleteBtn = InlineKeyboardButton.builder()
                .text("УДАЛИТЬ")
                .callbackData("DELETE_BOT_" + bot.getId())
                .build();

        InlineKeyboardButton menuBtn = InlineKeyboardButton.builder()
                .text("В меню")
                .callbackData("GOBACK")
                .build();

        InlineKeyboardRow row1 = new InlineKeyboardRow();
        row1.add(deleteBtn);
        keyboardRows.add(row1);

        InlineKeyboardRow row2 = new InlineKeyboardRow();
        row2.add(menuBtn);
        keyboardRows.add(row2);

        sendMessageWithInlineKeyboard(chatId, sb.toString(), keyboardRows);
    }

    private Long getLastCreatedBotId(String chatId) {
        List<BotEntity> bots = botRepository.findByCreatorChatId(chatId);
        BotEntity created = bots.stream()
                .filter(b -> "CREATED".equals(b.getStatus()))
                .reduce((first, second) -> second)
                .orElse(null);
        return created != null ? created.getId() : null;
    }

    private void cancelCreation(String chatId) {
        Client client = clientRepository.findById(chatId).orElse(null);
        if (client != null && client.getFabric_status() != null) {
            client.setFabric_status(null);
            clientRepository.save(client);
        }
    }

    /// BOT ///

    public void sendMessage(String chatId, String text) {
        try {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .parseMode("HTML")
                    .build();
            tgClient.execute(message);
        } catch (TelegramApiException e) {
            notifyTechChat("Error sending message to chat.", e);
        }
    }

    private void sendMessageWithInlineKeyboard(String chatId, String text, List<InlineKeyboardRow> keyboard) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(keyboard);

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .replyMarkup(markup)
                .build();
        try {
            tgClient.execute(message);
        } catch (TelegramApiException e) {
            LOGGER.error("Error sending message with inline keyboard", e);
        }
    }

    public void removeKeyboard(String chatId, Integer messageId) {
        EditMessageReplyMarkup editMarkup = new EditMessageReplyMarkup();
        editMarkup.setChatId(chatId);
        editMarkup.setMessageId(messageId);
        editMarkup.setReplyMarkup(null);

        try {
            tgClient.execute(editMarkup);
        } catch (TelegramApiException e) {
            notifyTechChat("Error removing keyboard from message.", e);
        }
    }

    /// UTILS ///

    public void notifyTechChat(String message) {
        notifyTechChat(message, null);
    }

    public void notifyTechChat(String message, Exception exception) {
        try {
            String errorBrief = (exception != null && exception.getMessage() != null) ? exception.getMessage() : "Unknown error";
            String techMessage = "⚠️  " + message + (exception != null ? "\n\nError: " + errorBrief : "");
            sendMessage(BotConfiguration.techChatId, techMessage);
        } catch (Exception e) {
            LOGGER.error("Error sending technical error notification: ", e);
        }

        if (exception != null) {
            LOGGER.error(message, exception);
        } else {
            LOGGER.error(message);
        }
    }
}