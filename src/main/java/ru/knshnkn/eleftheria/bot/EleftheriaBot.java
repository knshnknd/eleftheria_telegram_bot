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

import java.util.List;

@Component
public class EleftheriaBot implements LongPollingSingleThreadUpdateConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(EleftheriaBot.class);

    private final BotConfiguration botConfiguration = new BotConfiguration();

    private final TelegramClient tgClient = new OkHttpTelegramClient(botConfiguration.getToken());

    private final ClientRepository clientRepository;
    private final BotRepository botRepository;
    private final BotCreationService botCreationService;

    public EleftheriaBot(ClientRepository clientRepository, BotRepository botRepository, BotCreationService botCreationService) {
        this.clientRepository = clientRepository;
        this.botRepository = botRepository;
        this.botCreationService = botCreationService;
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
        User user = callbackQuery.getFrom();

        try {
            switch (callData) {
                case "CREATE_BOT":
                    botCreationService.startCreationProcess(this, chatId);
                    break;
                case "MY_BOTS":
                    showUserBots(chatId);
                    break;
                case "GOBACK":
                    showStartMenu(chatId);
                    break;
                case "CHOOSE_PERSONAL":
                    Long lastBotId = getLastCreatedBotId(chatId);
                    if (lastBotId != null) {
                        botCreationService.setPersonal(lastBotId, chatId);
                        sendMessage(chatId, "Ура! Бот создан и будет присылать сообщения Вам лично.");
                    }
                    break;
                case "CHOOSE_CHAT":
                    botCreationService.waitForAdminChatId(chatId);
                    sendMessage(chatId, "Добавьте созданного бота в ваш админский чат. " +
                            "Там введите /chat_id, скопируйте ответ и пришлите этот ChatID сюда. Отменить: /cancel.");
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            notifyTechChat("Error processing callback.", e);
            sendMessage(chatId, "Unknown error.");
        }

        int previousMessageId = callbackQuery.getMessage().getMessageId();
        removeKeyboard(chatId, previousMessageId);
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
        } else {
            StringBuilder sb = new StringBuilder("Ваши боты:\n");
            for (BotEntity bot : bots) {
                sb.append("• ID=").append(bot.getId())
                        .append(", токен=").append(bot.getToken())
                        .append(", статус=").append(bot.getStatus())
                        .append("\n");
            }

            List<InlineKeyboardRow> keyboard = List.of(
                    new InlineKeyboardRow(
                            List.of(
                                    InlineKeyboardButton.builder().text("Назад").callbackData("GOBACK").build()
                            )
                    )
            );
            sendMessageWithInlineKeyboard(chatId, sb.toString(), keyboard);
        }
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
            sendMessage(botConfiguration.getTechChatId(), techMessage);
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