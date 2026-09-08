package com.FinCode.finance_bot;

import com.FinCode.finance_bot.config.BotConfig;
import com.FinCode.finance_bot.database.entity.UserApp;
//import com.FinCode.finance_bot.database.repository.UserRepository;
import com.FinCode.finance_bot.feature.info.InfoBlockHandler;
import com.FinCode.finance_bot.feature.info.UserProgress;
import com.FinCode.finance_bot.feature.registration.BotState;
//import com.FinCode.finance_bot.feature.registration.RegBlockHandler;
//import com.FinCode.finance_bot.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;



@Component
@Slf4j
public class FinanceBot extends TelegramLongPollingBot {
    private final BotConfig config;
//    private UserRepository userRepository;
//    private RegBlockHandler regBlockHandler;
//    private EmailService emailService;
    private final InfoBlockHandler infoBlockHandler;
    private final Map<Long, UserProgress> infoProgressMap = new ConcurrentHashMap<>();
    private final Map<Long, List<Integer>> userMessageIds = new ConcurrentHashMap<>();

    private final Map<Long, Integer> activeInteractiveMessageId = new ConcurrentHashMap<>();

    private final Map<Long, Set<Integer>> permanentInteractiveMessageIds = new ConcurrentHashMap<>();

    public FinanceBot(BotConfig config, InfoBlockHandler infoBlockHandler) {
        super(config.getBotToken());
        this.config = config;
        this.infoBlockHandler = infoBlockHandler;
        log.info("Telegram-бот '{}' успешно инициализирован.", config.getBotUsername());
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            long chatId = update.getMessage().getChatId();
            String userText = update.getMessage().getText();
            int messageId = update.getMessage().getMessageId();

            //UserApp user = userRepository.findById(chatId).orElseGet(() -> createNewUser(chatId));

//            if (user.getBotState() == BotState.MAIN_MENU) {
//                trackMessage(chatId, messageId);
//            }

            if (userText.equals("/start")) {
//                user.setBotState(BotState.MAIN_MENU);
//                userRepository.save(user);
                trackMessage(chatId,messageId);

                SendMessage welcomeMsg = infoBlockHandler.createWelcomeMessage(chatId);
                sendMsg(welcomeMsg,chatId);
                return;
            }

//            if (user.getBotState() != BotState.MAIN_MENU) {
//                SendMessage nextQuestion = regBlockHandler.handleInput(user, userText);
//                sendMsg(nextQuestion, chatId);
//            }
        }

        else if (update.hasCallbackQuery()) {
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            int clickedMessageId = update.getCallbackQuery().getMessage().getMessageId();
            String callbackData = update.getCallbackQuery().getData();
            String callbackQueryId = update.getCallbackQuery().getId();

            boolean isInfoCallback = callbackData.startsWith("info_");

            if (isInfoCallback) {

                Set<Integer> permanentIds = permanentInteractiveMessageIds.getOrDefault(chatId, Collections.emptySet());

                if (!permanentIds.contains(clickedMessageId)) {
                    answerCallback(callbackQueryId, "⚠️ Эта кнопка больше не активна. Пожалуйста, используйте актуальное меню.", true);
                    return;
                }

                answerCallback(callbackQueryId, null, false);

                UserProgress progress = infoProgressMap.computeIfAbsent(chatId, k -> new UserProgress());

                switch (callbackData) {
                    case "info_resume" -> infoBlockHandler.sendResumeFile(this, chatId, progress);
                    case "info_demo" -> infoBlockHandler.sendDemoWorks(this, chatId, progress);
                    case "info_calculator" -> infoBlockHandler.sendCalculatorDescription(this, chatId, progress);
                }
                return;
            }


            Integer activeMessageId = activeInteractiveMessageId.get(chatId);

            if (activeMessageId == null || !activeMessageId.equals(clickedMessageId)) {
                answerCallback(callbackQueryId, "⚠️ Эта кнопка больше не активна. Пожалуйста, используйте актуальное меню.", true);
                return;
            }

            activeInteractiveMessageId.remove(chatId);
            answerCallback(callbackQueryId, null, false);

            //UserApp user = userRepository.findById(chatId).orElseThrow();

//            if (callbackData.equals("start_contract")) {
//                regBlockHandler.clearChatHistory(this, chatId, userMessageIds.getOrDefault(chatId, Collections.emptyList()));
//
//                infoProgressMap.remove(chatId);
//
//                SendMessage firstQuestion = regBlockHandler.startAnketa(user);
//                sendMsg(firstQuestion, chatId);
//            }
//            else if (callbackData.startsWith("service_")) {
//                SendMessage nextStep = regBlockHandler.handleServiceCallback(user, callbackData,this);
//                sendMsg(nextStep, chatId);
//            }
//            else if (callbackData.startsWith("contact_")) {
//                SendMessage prompt = regBlockHandler.handleContactMethodCallback(user, callbackData);
//                sendMsg(prompt, chatId);
//            }
//            else if (callbackData.equals("accept_privacy")) {
//                log.info("Чат {}: анкета полностью заполнена. Отправка на почту...", chatId);
//
//                try {
//                    emailService.sendAnketaToDirector(user);
//
//                    SendMessage successMsg = new SendMessage(String.valueOf(chatId),
//                            "🎉 Спасибо! Ваша анкета успешно сохранена и отправлена финансовому директору на почту. Он свяжется с вами.");
//                    sendMsg(successMsg, chatId);
//
//                } catch (Exception e) {
//                    log.error("Чат {}: не удалось отправить Email. Локальная ошибка или блокировка порта SMTP.", chatId, e);
//
//                    SendMessage errorMsg = new SendMessage(String.valueOf(chatId),
//                            "⚠️ Анкета сохранена в базе данных, но произошел технический сбой при отправке уведомления на почту. Исполнитель свяжется с вами.");
//                    sendMsg(errorMsg, chatId);
//                }
//
//                // Возвращаем пользователя в главное меню
//                user.setBotState(BotState.MAIN_MENU);
//                userRepository.save(user);
//            }
        }
    }

    // Метод для отправки сообщения и запоминания ID
    private void sendMsg(SendMessage msg, long chatId) {
        try {
            var sentMessage = execute(msg);

//            UserApp user = userRepository.findById(chatId).orElse(null);

            trackMessage(chatId,sentMessage.getMessageId());

//            if (user == null || user.getBotState() == BotState.MAIN_MENU) {
//                trackMessage(chatId, sentMessage.getMessageId());
//            }

            if (msg.getReplyMarkup() instanceof InlineKeyboardMarkup markup) {
                if (isInfoKeyboard(markup)) {
                    permanentInteractiveMessageIds
                            .computeIfAbsent(chatId, k -> ConcurrentHashMap.newKeySet())
                            .add(sentMessage.getMessageId());
                } else {
                    activeInteractiveMessageId.put(chatId, sentMessage.getMessageId());
                }
            }
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке сообщения в чат {}", chatId, e);
        }
    }

    private boolean isInfoKeyboard(InlineKeyboardMarkup markup) {
        return markup.getKeyboard().stream()
                .flatMap(List::stream)
                .map(InlineKeyboardButton::getCallbackData)
                .filter(data -> data != null)
                .anyMatch(data -> data.startsWith("info_"));
    }

//    private UserApp createNewUser(Long chatId) {
//        UserApp user = new UserApp();
//        user.setChatId(chatId);
//        user.setBotState(BotState.MAIN_MENU);
//        log.info("Чат {}: зарегистрирован новый пользователь в БД PostgreSQL.", chatId);
//        return userRepository.save(user);
//    }

    private void trackMessage(long chatId, int messageId) {
        userMessageIds.computeIfAbsent(chatId, k -> Collections.synchronizedList(new ArrayList<>())).add(messageId);
    }

    private void answerCallback(String callbackQueryId, String alertText, boolean showAlert) {
        try {
            AnswerCallbackQuery answer = new AnswerCallbackQuery(callbackQueryId);
            if (alertText != null) {
                answer.setText(alertText);
                answer.setShowAlert(showAlert);
            }
            execute(answer);
        } catch (TelegramApiException e) {
            log.warn("Не удалось ответить на callback {}", callbackQueryId, e);
        }
    }
    @Override
    public String getBotUsername() {
        return config.getBotUsername();
    }
}