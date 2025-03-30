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
import ru.knshnkn.eleftheria.jpa.entity.BanList;
import ru.knshnkn.eleftheria.jpa.entity.BotEntity;
import ru.knshnkn.eleftheria.jpa.repository.BanListRepository;
import ru.knshnkn.eleftheria.jpa.repository.BotRepository;
import ru.knshnkn.eleftheria.service.SpamProtectionService;

import java.util.Comparator;
import java.util.List;

public class UserBot implements LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserBot.class);

    private final BotRepository botRepository;
    private final BanListRepository banListRepository;
    private final Long botId;
    public final String botToken;
    private String adminId;
    private String adminChatId;
    public final TelegramClient tgClient;

    private final SpamProtectionService spamProtectionService = new SpamProtectionService();

    public UserBot(Long botId,
                   String token,
                   BotRepository botRepository,
                   BanListRepository banListRepository) {
        this.botId = botId;
        this.botToken = token;
        this.botRepository = botRepository;
        this.banListRepository = banListRepository;
        this.tgClient = new OkHttpTelegramClient(token);

        loadBotSettingsFromDb();
    }

    private void loadBotSettingsFromDb() {
        if (botId == null || botId <= 0) {
            return;
        }
        BotEntity be = botRepository.findById(botId).orElse(null);
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
                    sendMessageToUser(message.getChatId().toString(), message.getChatId().toString());
                } else if (message.hasText() && message.getText().equals("/start")) {
                    sendMessageToUser(message.getChatId().toString(),
                            "Здравствуйте! Напишите сообщение прямо в чат, и мы ответим.");
                } else {
                    loadBotSettingsFromDb();
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

        if (banListRepository.existsByBotIdAndChatId(botId, userChatId)) {
            return;
        }
        if (spamProtectionService.isMuted(userChatId)) {
            return;
        }

        spamProtectionService.recordMessage(userChatId);
        if (spamProtectionService.isSpam(userChatId)) {
            spamProtectionService.mute(userChatId);
            return;
        }

        String firstName = (message.getFrom() != null) ? message.getFrom().getFirstName() : "noName";
        String textToAdmin;

        if (message.hasPhoto()) {
            List<PhotoSize> photos = message.getPhoto();
            PhotoSize biggest = photos.stream()
                    .max(Comparator.comparing(PhotoSize::getFileSize))
                    .orElse(null);

            String caption = message.getCaption() != null ? message.getCaption() : "";
            textToAdmin = userChatId + " " + firstName + " прислал(а) картинку";
            if (!caption.isEmpty()) {
                textToAdmin += " с текстом: " + caption;
            }
            forwardPhotoToAdmin(biggest, textToAdmin);
        } else if (message.hasText()) {
            String text = message.getText();
            textToAdmin = userChatId + " " + firstName + ": " + text;
            sendMessageToAdmin(textToAdmin, null);
        } else {
            log.info("Received message of unknown type from user: {}", userChatId);
        }
    }

    private void processAdminReply(Message adminMessage) {
        Message repliedMessage = adminMessage.getReplyToMessage();
        if (repliedMessage == null) {
            return;
        }

        String replyText = repliedMessage.getText();
        if (replyText == null) {
            replyText = repliedMessage.getCaption();
        }
        if (replyText == null) {
            log.warn("Ответ администратора не содержит текста для извлечения ID пользователя.");
            return;
        }
        String[] parts = replyText.split(" ", 2);
        if (parts.length < 1) {
            log.warn("Неверный формат исходного сообщения, отсутствует ID пользователя.");
            return;
        }
        String potentialUserChatId = parts[0].trim();
        if (!potentialUserChatId.matches("-?\\d+")) {
            log.warn("Не удалось извлечь корректный chatId из сообщения: {}", potentialUserChatId);
            return;
        }

        String adminText = adminMessage.getText();
        if (adminText != null) {
            String normalized = adminText.toLowerCase().trim();
            if (normalized.equals("!ban")) {
                banUser(potentialUserChatId);
                sendMessageToAdmin("Пользователь " + potentialUserChatId + " забанен.", null);
                return;
            } else if (normalized.contains("!unbun")) {
                unbanUser(potentialUserChatId);
                sendMessageToAdmin("Пользователь " + potentialUserChatId + " разбанен.", null);
                return;
            }
        }

        if (adminMessage.hasPhoto()) {
            List<PhotoSize> photos = adminMessage.getPhoto();
            PhotoSize biggest = photos.stream()
                    .max(Comparator.comparing(PhotoSize::getFileSize))
                    .orElse(null);
            String caption = adminMessage.getCaption() != null ? adminMessage.getCaption() : "";
            sendPhotoToUser(potentialUserChatId, biggest, caption);
        } else if (adminMessage.hasText()) {
            String answerFromAdmin = adminMessage.getText();
            sendMessageToUser(potentialUserChatId, answerFromAdmin);
        } else {
            log.warn("Неподдерживаемый тип ответа от администратора.");
        }
    }

    private void banUser(String userChatId) {
        if (!banListRepository.existsByBotIdAndChatId(botId, userChatId)) {
            BanList banEntry = new BanList();
            banEntry.setBotId(botId);
            banEntry.setChatId(userChatId);
            banListRepository.save(banEntry);
        }
    }

    private void unbanUser(String userChatId) {
        banListRepository.deleteByBotIdAndChatId(botId, userChatId);
    }

    private void sendPhotoToUser(String userChatId, PhotoSize photo, String caption) {
        if (photo == null) {
            log.warn("Фото отсутствует, не могу отправить сообщение пользователю {}.", userChatId);
            return;
        }
        try {
            SendPhoto sendPhoto = SendPhoto.builder()
                    .chatId(userChatId)
                    .photo(new InputFile(photo.getFileId()))
                    .caption(caption)
                    .build();
            tgClient.execute(sendPhoto);
        } catch (TelegramApiException e) {
            notifyTechChat("Ошибка при отправке фото клиенту: " + e.getMessage());
        }
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