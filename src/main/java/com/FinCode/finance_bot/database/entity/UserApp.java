package com.FinCode.finance_bot.database.entity;

import com.FinCode.finance_bot.feature.registration.BotState;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name="users")
@Data
public class UserApp {
    @Id
    @Column(name = "chat_id")
    private Long chatId;

    @Enumerated(EnumType.STRING)
    @Column(name = "bot_state")
    private BotState botState;

    private String fio;
    private String city;
    private String industry;
    private String tariff;
    private String term;

    @Column(columnDefinition = "TEXT")
    private String comments;
}
