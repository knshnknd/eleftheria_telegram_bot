CREATE TABLE bots
(
    id               SERIAL PRIMARY KEY,
    creator_chat_id  BIGINT,                              -- chat_id создателя бота клиента
    token            VARCHAR(255),                        -- API TOKEN бота (уникальное значение)
    admin_chat_id    BIGINT,                              -- chat_id админского чата для уведомлений/управления
    status           VARCHAR(50),                         -- состояние (например, 'active', 'inactive', 'error')
    start_message    TEXT,                                -- стартовое сообщение
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- дата и время создания записи
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- дата и время последнего обновления записи
    last_activity_at TIMESTAMP                            -- дата и время последней активности бота
);

CREATE TABLE client
(
    creator_chat_id VARCHAR(255) PRIMARY KEY,  -- chat_id создателя (используем строку, как в Entity)
    fabric_status   VARCHAR(255)               -- статус фабрики (например, 'ОЖИДАЮ ТОКЕН' или null)
);