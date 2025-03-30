package ru.knshnkn.eleftheria.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.knshnkn.eleftheria.jpa.entity.BanList;
import ru.knshnkn.eleftheria.jpa.entity.BotEntity;

import java.util.List;

public interface BanListRepository extends JpaRepository<BanList, Long> {
    boolean existsByBotIdAndChatId(Long botId, String chatId);
    void deleteByBotIdAndChatId(Long botId, String chatId);
}