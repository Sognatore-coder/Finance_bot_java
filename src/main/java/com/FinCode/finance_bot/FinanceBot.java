package com.FinCode.finance_bot;

import com.FinCode.finance_bot.config.BotConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;


@Component
@Slf4j
public class FinanceBot extends TelegramLongPollingBot {

    private final BotConfig config;

    public FinanceBot(BotConfig config) {
        super(config.getBotToken());
        this.config = config;
        log.info("Телеграм бот '{}' успешно инициализирован в системе.", config.getBotUsername());
    }

    @Override
    public void onUpdateReceived(Update update) {
        if(update.hasMessage() && update.getMessage().hasText()) {
            String userText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            String username = update.getMessage().getFrom().getUserName();

            log.info("Получено сообщение от пользователя [@{} (ID: {})]: '{}'", username, chatId, userText);

            if(userText.equals("/start")){
                sendWelcomeMessage(chatId);
            }
        }
    }

    private void sendWelcomeMessage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Здравствуйте, я Игорь, помощник финансового директора, чем могу быть полезен?");

        try {
            execute(message);
            log.info("Приветственное сообщение успешно отправлено в чат ID: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке приветственного сообщения в чат ID: {}", chatId, e);
        }
    }

    @Override
    public String getBotUsername() {
        return config.getBotUsername();
    }
}
