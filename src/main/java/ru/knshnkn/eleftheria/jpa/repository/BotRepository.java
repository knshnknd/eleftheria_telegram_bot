package ru.knshnkn.eleftheria.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.knshnkn.eleftheria.jpa.entity.BotEntity;

import java.util.List;

public interface BotRepository extends JpaRepository<BotEntity, Long> {
    List<BotEntity> findByCreatorChatId(String creatorChatId);
    List<BotEntity> findByStatusIn(List<String> statuses);
    boolean existsByRandomName(String randomName);
}