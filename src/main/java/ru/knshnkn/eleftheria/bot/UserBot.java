package ru.knshnkn.eleftheria.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import ru.knshnkn.eleftheria.jpa.entity.BotEntity;
import ru.knshnkn.eleftheria.jpa.repository.BotRepository;

import java.util.Comparator;
import java.util.List;

public class UserBot implements LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserBot.class);

    private final BotRepository botRepository;
    private final Long id;
    public final String botToken;
    private String adminId;
    private String adminChatId;
    public final TelegramClient tgClient;

    public UserBot(Long id, String token, BotRepository botRepository) {
        this.id = id;
        this.botToken = token;
        this.botRepository = botRepository;
        this.tgClient = new OkHttpTelegramClient(token);

        loadBotSettingsFromDb();
    }

    private void loadBotSettingsFromDb() {
        if (id == null || id <= 0) {
            return;
        }
        BotEntity be = botRepository.findById(id).orElse(null);
        if (be != null) {
            this.adminChatId = be.getAdmin_chat_id();
            this.adminId = be.getCreatorChatId();
        }
    }

    @Override
    public void consume(Update update) {
        try {
            if (update.hasMessage()) {
                Message message = update.getMessage();
                if (message.hasText() && message.getText().equals("/chat_id")) {
                    sendMessageToAdmin(message.getChatId().toString(), null);
                } else if (message.hasText() && message.getText().equals("/start")) {
                    sendMessageToUser(message.getChatId().toString(), "Привет! Напишите сообщение прямо в чат, и мы ответим.");
                } else {
                    handleMessage(message);
                }
            }
        } catch (Exception e) {
            log.error("Error in consume method: ", e);
        }
    }

    private void handleMessage(Message message) {
        String fromChatId = message.getChatId().toString();

        if (isFromAdminChat(fromChatId)) {
            if (message.getReplyToMessage() != null) {
                processAdminReply(message);
            }
            return;
        }

        processClientMessage(message);
    }

    private boolean isFromAdminChat(String chatId) {
        if (adminChatId != null && !adminChatId.isEmpty()) {
            return adminChatId.equals(chatId);
        }
        return adminId != null && adminId.equals(chatId);
    }

    private void processClientMessage(Message message) {
        String userChatId = message.getChatId().toString();
        String firstName = (message.getFrom() != null) ? message.getFrom().getFirstName() : "noName";

        String textToAdmin;
        if (message.hasPhoto()) {
            List<PhotoSize> photos = message.getPhoto();
            PhotoSize biggest = photos.stream()
                    .max(Comparator.comparing(PhotoSize::getFileSize))
                    .orElse(null);

            String caption = message.getCaption() != null ? message.getCaption() : "";
            textToAdmin = userChatId + " " + firstName + " sent a photo";
            if (!caption.isEmpty()) {
                textToAdmin += " with caption: " + caption;
            }
            forwardPhotoToAdmin(biggest, textToAdmin);
        } else if (message.hasText()) {
            String text = message.getText();
            textToAdmin = userChatId + " " + firstName + ": " + text;
            sendMessageToAdmin(textToAdmin, null);
        } else {
            log.info("Received message of unknown type from user: " + userChatId);
        }
    }

    private void processAdminReply(Message adminMessage) {
        Message repliedMessage = adminMessage.getReplyToMessage();

        String replyText = repliedMessage.getText();
        if (replyText == null) {
            replyText = repliedMessage.getCaption();
        }
        if (replyText == null) {
            log.warn("Admin reply does not contain text for extracting user ID.");
            return;
        }

        String[] parts = replyText.split(" ", 2);
        if (parts.length < 1) {
            log.warn("Invalid original message format, missing user ID.");
            return;
        }
        String potentialUserChatId = parts[0].trim();
        if (!potentialUserChatId.matches("-?\\d+")) {
            log.warn("Failed to extract valid chatId from message.");
            return;
        }

        String answerFromAdmin = adminMessage.getText();
        if (answerFromAdmin == null || answerFromAdmin.isEmpty()) {
            if (adminMessage.hasPhoto()) {
                answerFromAdmin = "Admin sent a photo.";
            } else {
                answerFromAdmin = "[Empty admin message]";
            }
        }
        sendMessageToUser(potentialUserChatId, answerFromAdmin);
    }

    private void sendMessageToAdmin(String text, Integer unusedReplyToMessageId) {
        String targetChatId = (adminChatId == null || adminChatId.isEmpty()) ? adminId : adminChatId;
        try {
            SendMessage msg = SendMessage.builder()
                    .chatId(targetChatId)
                    .text(text)
                    .build();
            tgClient.execute(msg);
        } catch (TelegramApiException e) {
            notifyTechChat("Error sending to admin: " + e.getMessage());
        }
    }

    private void forwardPhotoToAdmin(PhotoSize photo, String caption) {
        if (photo == null) {
            return;
        }
        String targetChatId = (adminChatId == null || adminChatId.isEmpty()) ? adminId : adminChatId;
        try {
            SendPhoto sendPhoto = SendPhoto.builder()
                    .chatId(targetChatId)
                    .photo(new InputFile(photo.getFileId()))
                    .caption(caption)
                    .build();
            tgClient.execute(sendPhoto);
        } catch (TelegramApiException e) {
            notifyTechChat("Error sending photo to admin: " + e.getMessage());
        }
    }

    private void sendMessageToUser(String userChatId, String text) {
        try {
            SendMessage msg = SendMessage.builder()
                    .chatId(userChatId)
                    .text(text)
                    .build();
            tgClient.execute(msg);
        } catch (TelegramApiException e) {
            notifyTechChat("Error replying to client: " + e.getMessage());
        }
    }

    private void notifyTechChat(String message) {
        log.warn("[TECH_CHAT_NOTIFY] " + message);
    }
}