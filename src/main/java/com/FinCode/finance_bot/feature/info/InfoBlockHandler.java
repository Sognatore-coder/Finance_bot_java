package com.FinCode.finance_bot.feature.info;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.bots.AbsSender;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class InfoBlockHandler {

    public SendMessage createWelcomeMessage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Здравствуйте! Я цифровой помощник финансового директора. Чем могу быть полезен?\n\n" +
                "Пожалуйста, ознакомьтесь с материалами ниже, чтобы открыть доступ к анкете заключения договора:");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(Collections.singletonList(createButton("📄 Резюме специалиста", "info_resume")));
        rows.add(Collections.singletonList(createButton("📊 Демонстрационные работы", "info_demo")));
        rows.add(Collections.singletonList(createButton("🧮 Работа калькулятора фин-моделей", "info_calculator")));

        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        return message;
    }

    public void sendResumeFile(AbsSender sender, long chatId, UserProgress progress) {
        log.info("Чат {}: просмотр резюме ", chatId);
        progress.setViewedResume(true);

        sendPdfFromResources(sender, chatId, "files/resume.pdf", "Портфолио_Фин_Директора.pdf",
                "Перед вами подробное резюме и портфолио специалиста.\n\n" +
                        "• Опыт управления финансами: 10+ лет\n" +
                        "• Реализовано более 70 индивидуальных фин-моделей");

        checkAndOfferRegistration(sender, chatId, progress);
    }

    public void sendDemoWorks(AbsSender sender, long chatId, UserProgress progress) {
        log.info("Чат {}: просмотр демо-работ", chatId);
        progress.setViewedDemo(true);

        String retailCaption = "📊 Демо-кейс №1: Сеть розничных магазинов & Маркетплейсы\n\n" +
                "**Направление бизнеса:** Розничная торговля одеждой через Wildberries/Ozon.";
        sendPdfFromResources(sender, chatId, "files/demo_retail.pdf", "Кейс_Маркетплейсы_Ритейл.pdf", retailCaption);

        String itCaption = "💻 Демо-кейс №2: SaaS-платформа (IT-стартап)\n\n" +
                "**Направление бизнеса:** Разработка программного обеспечения по подписке.";
        sendPdfFromResources(sender, chatId, "files/demo_it.pdf", "Кейс_IT_Стартап.pdf", itCaption);

        checkAndOfferRegistration(sender, chatId, progress);
    }

    public void sendCalculatorDescription(AbsSender sender, long chatId, UserProgress progress) {
        log.info("Чат {}: просмотр калькулятора ", chatId);
        progress.setViewedCalculator(true);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("🧮 **Как работает калькулятор финансовых моделей?**\n\n" +
                "Это автоматизированный инструмент, который собирает воедино все метрики вашего бизнеса:\n\n" +
                "1. **Предотвращение кассовых разрывов**\n" +
                "2. **Анализ сценариев «Что если...»**\n" +
                "3. **Расчет Unit-экономики**");
        try {
            sender.execute(message);
        } catch (Exception e) {
            log.error("Ошибка при отправке описания калькулятора", e);
        }

        checkAndOfferRegistration(sender, chatId, progress);
    }

    private void checkAndOfferRegistration(AbsSender sender, long chatId, UserProgress progress) {
        // Проверяем метод из класса памяти
        if (progress.isAllViewed()) {

            SendMessage offerMessage = new SendMessage();
            offerMessage.setChatId(String.valueOf(chatId));
            offerMessage.setText("Вы ознакомились с компетенцией нашего финансового директора. Теперь можем перейти к заполнению договора");

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            rows.add(Collections.singletonList(createButton("🤝 Заключить договор", "start_contract")));
            markup.setKeyboard(rows);
            offerMessage.setReplyMarkup(markup);

            try {
                sender.execute(offerMessage);
            } catch (Exception e) {
                log.error("Ошибка при отправке предложения заключить договор", e);
            }
        }
    }

    private void sendPdfFromResources(AbsSender sender, long chatId, String resourcePath, String fileName, String caption) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            InputStream inputStream = resource.getInputStream();
            SendDocument document = new SendDocument();
            document.setChatId(String.valueOf(chatId));
            document.setDocument(new InputFile(inputStream, fileName));
            document.setCaption(caption);
            sender.execute(document);
        } catch (Exception e) {
            log.error("Ошибка при отправке файла '{}'", resourcePath, e);
        }
    }

    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setText(text);
        btn.setCallbackData(callbackData);
        return btn;
    }
}
