package ru.knshnkn.eleftheria.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.knshnkn.eleftheria.jpa.entity.BanList;
import ru.knshnkn.eleftheria.jpa.repository.BanListRepository;

@Service
public class BanService {

    private final BanListRepository banListRepository;

    public BanService(BanListRepository banListRepository) {
        this.banListRepository = banListRepository;
    }

    @Transactional
    public void banUser(Long botId, String userChatId) {
        if (!banListRepository.existsByBotIdAndChatId(botId, userChatId)) {
            BanList banEntry = new BanList();
            banEntry.setBotId(botId);
            banEntry.setChatId(userChatId);
            banListRepository.save(banEntry);
        }
    }

    @Transactional
    public void unbanUser(Long botId, String userChatId) {
        banListRepository.deleteByBotIdAndChatId(botId, userChatId);
    }
}