package com.FinCode.finance_bot.feature.calculator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Реализует пошаговый диалог двух финансовых калькуляторов:
 * 1) Цена товара по заданной рентабельности продаж.
 * 2) Точка безубыточности (сколько единиц товара нужно продать, чтобы
 *    перекрыть постоянные издержки бизнеса за месяц).
 * <p>
 * После получения результата все сообщения, отправленные в рамках сеанса
 * (вопросы бота, ответы пользователя и само сообщение с результатом),
 * автоматически удаляются через 2 минуты — по обе стороны переписки.
 */
@Component
@Slf4j
public class CalculatorHandler {

    private static final double MIN_PERCENT = 10.0;
    private static final double MAX_PERCENT = 80.0;
    private static final double MIN_FIXED_COSTS = 100_000.0;
    private static final long CLEANUP_DELAY_MINUTES = 2;

    private final Map<Long, CalculatorSession> sessions = new ConcurrentHashMap<>();

    // Демоны, чтобы не мешать штатному завершению приложения
    private final ScheduledExecutorService cleanupExecutor = Executors.newScheduledThreadPool(2, daemonThreadFactory());

    private static ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "calc-cleanup");
            thread.setDaemon(true);
            return thread;
        };
    }

    // Точка входа: сообщение с описанием калькуляторов + кнопки выбора
    public void sendCalculatorChoice(AbsSender sender, long chatId,Runnable onFinishCallback) {
        // На случай, если у пользователя уже был незавершённый сеанс — начинаем заново
        cancelSessionSilently(chatId);

        String text = "🧮 *Калькуляторы финансовых моделей*\n\n" +
                "*Калькулятор 1 — «Цена по рентабельности»*\n" +
                "Рассчитывает итоговую цену продажи товара исходя из его полной себестоимости " +
                "и желаемого уровня рентабельности продаж.\n\n" +
                "*Калькулятор 2 — «Точка безубыточности»*\n" +
                "Рассчитывает, сколько единиц товара нужно продать компании за месяц, " +
                "чтобы перекрыть постоянные издержки бизнеса.\n\n" +
                "Выберите нужный вариант 👇";

        SendMessage message = new SendMessage(String.valueOf(chatId), text);
        message.setParseMode("Markdown");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(
                List.of(button("Калькулятор 1", "calc_choice_1"), button("Калькулятор 2", "calc_choice_2"))
        ));
        message.setReplyMarkup(markup);

        Message sent = safeExecute(sender, message);
        if (sent != null) {
            CalculatorSession session = new CalculatorSession();
            session.setState(CalculatorState.AWAITING_CHOICE);
            session.setOnFinishCallback(onFinishCallback);
            session.trackMessage(sent.getMessageId());
            sessions.put(chatId, session);
        }
    }

    public boolean hasActiveSession(long chatId) {
        return sessions.containsKey(chatId);
    }

    // Обработка нажатия на inline-кнопку выбора калькулятора
    public void handleChoiceCallback(AbsSender sender, long chatId, String callbackData,
                                     String callbackQueryId, int clickedMessageId) {
        CalculatorSession session = sessions.get(chatId);

        if (session == null || session.getState() != CalculatorState.AWAITING_CHOICE) {
            answerCallback(sender, callbackQueryId, "⚠️ Эта кнопка больше не активна. Откройте калькулятор заново.", true);
            return;
        }

        removeInlineKeyboard(sender, chatId, clickedMessageId);

        if ("calc_choice_1".equals(callbackData)) {
            session.setState(CalculatorState.CALC1_AWAITING_COST);
            askAndTrack(sender, chatId, session, "Введите полную себестоимость товара (в рублях), число больше 0:");
        } else if ("calc_choice_2".equals(callbackData)) {
            session.setState(CalculatorState.CALC2_AWAITING_FIXED_COSTS);
            askAndTrack(sender, chatId, session,
                    "Введите сумму постоянных издержек бизнеса за месяц (в рублях). Значение должно быть не менее 100 000:");
        } else {
            answerCallback(sender, callbackQueryId, "⚠️ Неизвестная команда.", true);
            return;
        }

        answerCallback(sender, callbackQueryId, null, false);
    }

    // Обработка текстовых ответов пользователя внутри активного сеанса
    public void handleTextInput(AbsSender sender, long chatId, String text, int incomingMessageId) {
        CalculatorSession session = sessions.get(chatId);
        if (session == null) {
            return;
        }

        session.trackMessage(incomingMessageId);

        Double value = parseNumber(text);
        if (value == null) {
            askAndTrack(sender, chatId, session, "❗ Введите корректное число. Попробуйте ещё раз:");
            return;
        }

        switch (session.getState()) {
            case CALC1_AWAITING_COST -> handleCalc1Cost(sender, chatId, session, value);
            case CALC1_AWAITING_PROFITABILITY -> handleCalc1Profitability(sender, chatId, session, value);
            case CALC2_AWAITING_FIXED_COSTS -> handleCalc2FixedCosts(sender, chatId, session, value);
            case CALC2_AWAITING_PRICE -> handleCalc2Price(sender, chatId, session, value);
            case CALC2_AWAITING_COST -> handleCalc2Cost(sender, chatId, session, value);
            case CALC2_AWAITING_VARIABLE_PERCENT -> handleCalc2VariablePercent(sender, chatId, session, value);
            case AWAITING_CHOICE -> askAndTrack(sender, chatId, session,
                    "Пожалуйста, выберите калькулятор с помощью кнопок выше ☝️");
        }
    }

    // -------- Калькулятор 1 -------------

    private void handleCalc1Cost(AbsSender sender, long chatId, CalculatorSession session, double value) {
        if (value <= 0) {
            askAndTrack(sender, chatId, session, "❗ Себестоимость должна быть больше 0. Введите корректное значение:");
            return;
        }
        session.setCost(value);
        session.setState(CalculatorState.CALC1_AWAITING_PROFITABILITY);
        askAndTrack(sender, chatId, session,
                "Введите желаемый уровень рентабельности продаж, в % (число строго от 10 до 80, например 25):");
    }

    private void handleCalc1Profitability(AbsSender sender, long chatId, CalculatorSession session, double percent) {
        if (!isPercentValid(percent)) {
            askAndTrack(sender, chatId, session,
                    "❗ Рентабельность должна быть в диапазоне от 10% до 80% (границы не включаются). Введите корректное значение:");
            return;
        }

        double p = percent / 100.0;
        double price = session.getCost() / (1 - p);
        double profit = price - session.getCost();

        if (price <= 0 || Double.isNaN(price) || Double.isInfinite(price)) {
            askAndTrack(sender, chatId, session,
                    "❗ При таких параметрах цена получается некорректной (≤ 0). Введите другое значение рентабельности:");
            return;
        }

        String result = String.format(Locale.forLanguageTag("ru"),
                "✅ *Результат расчёта — Калькулятор 1*\n\n" +
                        "Себестоимость: %s руб.\n" +
                        "Рентабельность продаж: %s%%\n\n" +
                        "📌 Итоговая цена продажи: *%s руб.*\n" +
                        "Чистая прибыль: *%s руб.*",
                formatNumber(session.getCost()), formatNumber(percent), formatNumber(price),formatNumber(profit));

        finishSession(sender, chatId, session, result);
    }

    // --------- Калькулятор 2 ------------

    private void handleCalc2FixedCosts(AbsSender sender, long chatId, CalculatorSession session, double value) {
        if (value <= 0) {
            askAndTrack(sender, chatId, session, "❗ Издержки должны быть положительным числом. Введите корректное значение:");
            return;
        }
        if (value < MIN_FIXED_COSTS) {
            askAndTrack(sender, chatId, session,
                    "❗ Постоянные издержки должны быть не менее 100 000 руб. Введите корректное значение:");
            return;
        }
        session.setFixedCosts(value);
        session.setState(CalculatorState.CALC2_AWAITING_PRICE);
        askAndTrack(sender, chatId, session, "Введите цену продажи единицы товара (в рублях), число больше 0:");
    }

    private void handleCalc2Price(AbsSender sender, long chatId, CalculatorSession session, double value) {
        if (value <= 0) {
            askAndTrack(sender, chatId, session, "❗ Цена должна быть больше 0. Введите корректное значение:");
            return;
        }
        session.setPrice(value);
        session.setState(CalculatorState.CALC2_AWAITING_COST);
        askAndTrack(sender, chatId, session,
                "Введите себестоимость единицы товара (в рублях), число больше 0 и меньше цены продажи ("
                        + formatNumber(value) + " руб.):");
    }

    private void handleCalc2Cost(AbsSender sender, long chatId, CalculatorSession session, double value) {
        if (value <= 0 || value >= session.getPrice()) {
            askAndTrack(sender, chatId, session,
                    "❗ Себестоимость должна быть больше 0 и меньше цены продажи (" + formatNumber(session.getPrice())
                            + " руб.). Введите корректное значение:");
            return;
        }
        session.setCost(value);
        session.setState(CalculatorState.CALC2_AWAITING_VARIABLE_PERCENT);
        askAndTrack(sender, chatId, session,
                "Введите долю дополнительных переменных издержек от цены, в % (число строго от 10 до 80, например 50):");
    }

    private void handleCalc2VariablePercent(AbsSender sender, long chatId, CalculatorSession session, double percent) {
        if (!isPercentValid(percent)) {
            askAndTrack(sender, chatId, session,
                    "❗ Процент должен быть в диапазоне от 10% до 80% (границы не включаются). Введите корректное значение:");
            return;
        }

        double price = session.getPrice();
        double cost = session.getCost();
        double variableCosts = price * (percent / 100.0);
        double marginPerUnit = price - cost - variableCosts;

        if (marginPerUnit <= 0) {
            askAndTrack(sender, chatId, session,
                    "❗ При таких параметрах маржинальный доход с единицы товара получается ≤ 0 (цена не покрывает издержки). " +
                            "Введите другую долю переменных издержек:");
            return;
        }

        double quantity = Math.ceil(session.getFixedCosts() / marginPerUnit);

        String result = String.format(Locale.forLanguageTag("ru"),
                "✅ *Результат расчёта — Калькулятор 2*\n\n" +
                        "Цена продажи: %s руб.\n" +
                        "Себестоимость: %s руб.\n" +
                        "Доп. переменные издержки (%s%%): %s руб.\n" +
                        "Маржинальный доход с 1 шт.: %s руб.\n" +
                        "Постоянные издержки за месяц: %s руб.\n\n" +
                        "📌 Нужно продать: *%s шт. в месяц*, чтобы перекрыть постоянные издержки.",
                formatNumber(price), formatNumber(cost), formatNumber(percent), formatNumber(variableCosts),
                formatNumber(marginPerUnit), formatNumber(session.getFixedCosts()), formatNumber(quantity));

        finishSession(sender, chatId, session, result);
    }

    // Завершение сеанса: отправка результата + отложенное удаление переписки
    private void finishSession(AbsSender sender, long chatId, CalculatorSession session, String resultText) {
        SendMessage message = new SendMessage(String.valueOf(chatId), resultText);
        message.setParseMode("Markdown");

        Message sent = safeExecute(sender, message);
        if (sent != null) {
            session.trackMessage(sent.getMessageId());
        }

        List<Integer> messageIdsToDelete = List.copyOf(session.getMessageIds());
        Runnable onFinishCallback = session.getOnFinishCallback();
        sessions.remove(chatId);

        cleanupExecutor.schedule(
                () -> deleteMessages(sender, chatId, messageIdsToDelete),
                CLEANUP_DELAY_MINUTES, TimeUnit.MINUTES);

        if (onFinishCallback != null) {
            try {
                onFinishCallback.run();
            } catch (Exception e) {
                log.error("Чат {}: ошибка при выполнении колбэка после завершения калькулятора", chatId, e);
            }
        }
    }

    private void cancelSessionSilently(long chatId) {
        sessions.remove(chatId);
    }

    private void deleteMessages(AbsSender sender, long chatId, List<Integer> messageIds) {
        for (Integer id : messageIds) {
            try {
                sender.execute(new DeleteMessage(String.valueOf(chatId), id));
            } catch (TelegramApiException e) {
                // Сообщение могло быть уже удалено пользователем/чатом — это не критично
                log.debug("Чат {}: не удалось удалить сообщение {} калькулятора (возможно, уже удалено)", chatId, id);
            }
        }
        log.info("Чат {}: переписка калькулятора ({} сообщений) удалена по истечении 2 минут", chatId, messageIds.size());
    }

    // Утилиты
    private boolean isPercentValid(double percent) {
        return percent > MIN_PERCENT && percent < MAX_PERCENT;
    }

    private void askAndTrack(AbsSender sender, long chatId, CalculatorSession session, String text) {
        SendMessage message = new SendMessage(String.valueOf(chatId), text);
        Message sent = safeExecute(sender, message);
        if (sent != null) {
            session.trackMessage(sent.getMessageId());
        }
    }

    private Double parseNumber(String text) {
        try {
            String normalized = text.trim()
                    .replace(",", ".")
                    .replace(" ", "")
                    .replace("%", "");
            if (normalized.isEmpty()) {
                return null;
            }
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatNumber(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.format(Locale.forLanguageTag("ru"), "%,.0f", value);
        }
        return String.format(Locale.forLanguageTag("ru"), "%,.2f", value);
    }

    private void removeInlineKeyboard(AbsSender sender, long chatId, int messageId) {
        try {
            EditMessageReplyMarkup edit = new EditMessageReplyMarkup();
            edit.setChatId(String.valueOf(chatId));
            edit.setMessageId(messageId);
            edit.setReplyMarkup(null);
            sender.execute(edit);
        } catch (TelegramApiException e) {
            log.debug("Чат {}: не удалось убрать клавиатуру у сообщения {}", chatId, messageId);
        }
    }

    private void answerCallback(AbsSender sender, String callbackQueryId, String alertText, boolean showAlert) {
        try {
            AnswerCallbackQuery answer = new AnswerCallbackQuery(callbackQueryId);
            if (alertText != null) {
                answer.setText(alertText);
                answer.setShowAlert(showAlert);
            }
            sender.execute(answer);
        } catch (TelegramApiException e) {
            log.warn("Не удалось ответить на callback {}", callbackQueryId, e);
        }
    }

    private Message safeExecute(AbsSender sender, SendMessage message) {
        try {
            return sender.execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке сообщения калькулятора", e);
            return null;
        }
    }

    private InlineKeyboardButton button(String text, String callbackData) {
        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setText(text);
        btn.setCallbackData(callbackData);
        return btn;
    }
}