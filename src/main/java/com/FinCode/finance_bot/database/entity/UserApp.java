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
    @Column(name = "service_type")
    private String serviceType;

    @Column(name = "contact_method")
    private String contactMethod;

    @Column(name = "contact_value")
    private String contactValue;

    @Column(columnDefinition = "TEXT")
    private String comments;
}
