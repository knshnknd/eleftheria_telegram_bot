package ru.knshnkn.eleftheria.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SpamProtectionService {

    private static final int MAX_MESSAGES_PER_MINUTE = 30;
    private static final long MUTE_DURATION_HOURS = 24;

    private final Map<String, Deque<Instant>> messageTimes = new ConcurrentHashMap<>();
    private final Map<String, Instant> mutedUntil = new ConcurrentHashMap<>();

    public void recordMessage(String userChatId) {
        if (isMuted(userChatId)) {
            return;
        }

        Deque<Instant> times = messageTimes.computeIfAbsent(userChatId, k -> new ArrayDeque<>());

        Instant now = Instant.now();
        times.addLast(now);

        while (!times.isEmpty() && times.peekFirst().isBefore(now.minus(60, ChronoUnit.SECONDS))) {
            times.removeFirst();
        }
    }

    public boolean isMuted(String userChatId) {
        Instant until = mutedUntil.get(userChatId);
        if (until == null) {
            return false;
        }
        if (Instant.now().isAfter(until)) {
            mutedUntil.remove(userChatId);
            return false;
        }
        return true;
    }


    public boolean isSpam(String userChatId) {
        if (isMuted(userChatId)) {
            return false;
        }

        Deque<Instant> times = messageTimes.get(userChatId);
        if (times == null) {
            return false;
        }
        return times.size() > MAX_MESSAGES_PER_MINUTE;
    }

    public void mute(String userChatId) {
        mutedUntil.put(userChatId, Instant.now().plus(MUTE_DURATION_HOURS, ChronoUnit.HOURS));
        messageTimes.remove(userChatId);
    }
}