package com.FinCode.finance_bot.feature.info;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.bots.AbsSender;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class InfoBlockHandler {

    private final Map<String,String> fileIdCache = new ConcurrentHashMap<>();

    public SendMessage createWelcomeMessage(long chatId, boolean isExistingClient) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));

        if (isExistingClient) {
            message.setText("Здравствуйте! Рады вашему возвращению. Вы можете снова ознакомиться с материалами или заполнить новую анкету договора в меню внизу:");
            message.setReplyMarkup(createReplyKeyboard(true));
        } else {
            message.setText("Здравствуйте! Я цифровой помощник финансового директора. Чем могу быть полезен?\n\n" +
                    "Пожалуйста, ознакомьтесь с материалами в меню внизу экрана, чтобы открыть доступ к анкете заключения договора:");
            message.setReplyMarkup(createReplyKeyboard(false));
        }
        return message;
    }

    public void sendResumeFile(AbsSender sender, long chatId, UserProgress progress, boolean isExistingClient) {
        log.info("Чат {}: просмотр резюме", chatId);
        String caption = "📋 Перед вами подробное резюме и портфолио специалиста.";

        if (isExistingClient) {
            sendPdfFromResources(sender, chatId, "files/resume.pdf", "Портфолио_Фин_Директора.pdf", caption, createReplyKeyboard(true));
        } else {
            progress.setViewedResume(true);
            boolean readyNow = progress.isAllViewed();
            sendPdfFromResources(sender, chatId, "files/resume.pdf", "Портфолио_Фин_Директора.pdf", caption, createReplyKeyboard(readyNow));
            checkAndSendOnboardingMessage(sender, chatId, progress);
        }
    }

    public void sendDemoWorks(AbsSender sender, long chatId, UserProgress progress, boolean isExistingClient) {
        log.info("Чат {}: просмотр демо-работ", chatId);
        String retailCaption = "📊 Демо-кейс №1: Сеть розничных магазинов & Маркетплейсы";
        String itCaption = "💻 Демо-кейс №2: SaaS-платформа (IT-стартап)";

        if (isExistingClient) {
            sendPdfFromResources(sender, chatId, "files/demo_retail.pdf", "Кейс_Маркетплейсы_Ритейл.pdf", retailCaption, createReplyKeyboard(true));
            sendPdfFromResources(sender, chatId, "files/demo_it.pdf", "Кейс_IT_Стартап.pdf", itCaption, createReplyKeyboard(true));
        } else {
            progress.setViewedDemo(true);
            boolean readyNow = progress.isAllViewed();
            sendPdfFromResources(sender, chatId, "files/demo_retail.pdf", "Кейс_Маркетплейсы_Ритейл.pdf", retailCaption, createReplyKeyboard(readyNow));
            sendPdfFromResources(sender, chatId, "files/demo_it.pdf", "Кейс_IT_Стартап.pdf", itCaption, createReplyKeyboard(readyNow));
            checkAndSendOnboardingMessage(sender, chatId, progress);
        }
    }

    public void sendCalculatorDescription(AbsSender sender, long chatId, UserProgress progress, boolean isExistingClient) {
        log.info("Чат {}: просмотр калькулятора", chatId);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("🧮 **Как работает калькулятор финансовых моделей?**\n\n" +
                "Это автоматизированный инструмент анализа сценариев «Что если...» и предотвращения кассовых разрывов.");

        if (isExistingClient) {
            message.setReplyMarkup(createReplyKeyboard(true));
        } else {
            progress.setViewedCalculator(true);
            message.setReplyMarkup(createReplyKeyboard(progress.isAllViewed()));
        }

        try {
            sender.execute(message);
        } catch (Exception e) {
            log.error("Ошибка при отправке описания калькулятора", e);
        }

        if (!isExistingClient) {
            checkAndSendOnboardingMessage(sender, chatId, progress);
        }
    }

    private void checkAndSendOnboardingMessage(AbsSender sender, long chatId, UserProgress progress) {
        if (progress.isAllViewed() && !progress.isOnboardingSent()) {
            progress.setOnboardingSent(true);

            SendMessage message= new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText("🎉 Материалы успешно изучены!\n\n" +
                    "Вам открыт полный доступ к системе. Теперь вы можете запустить пошаговую анкету для заключения договора.");

            InlineKeyboardMarkup inlineMarkup = new InlineKeyboardMarkup();
            inlineMarkup.setKeyboard(List.of(List.of(createButton("🤝 Заключить договор","start_contract")
            )));

            message.setReplyMarkup(inlineMarkup);

            try {
                sender.execute(message);
            } catch (Exception e) {
                log.error("Ошибка отправки онбординг-сообщения", e);
            }
        }
    }

    public ReplyKeyboardMarkup createReplyKeyboard(boolean withContractButton) {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setSelective(true);
        replyKeyboardMarkup.setResizeKeyboard(true);
        replyKeyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("📄 Резюме специалиста"));
        row1.add(new KeyboardButton("📊 Демо-работы"));
        keyboardRows.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("🧮 Работа калькулятора"));
        keyboardRows.add(row2);

        if (withContractButton) {
            KeyboardRow row3 = new KeyboardRow();
            row3.add(new KeyboardButton("🤝 Заключить договор"));
            keyboardRows.add(row3);
        }

        replyKeyboardMarkup.setKeyboard(keyboardRows);
        return replyKeyboardMarkup;
    }

    private void sendPdfFromResources(AbsSender sender, long chatId, String resourcePath, String fileName, String caption, ReplyKeyboard reply) {
        try {
            SendDocument document = new SendDocument();
            document.setChatId(String.valueOf(chatId));
            document.setCaption(caption);
            if(reply != null) document.setReplyMarkup(reply);

            if (fileIdCache.containsKey(resourcePath)) {
                String cachedFileId = fileIdCache.get(resourcePath);

                document.setDocument(new InputFile(cachedFileId));
                sender.execute(document);
            } else {
                ClassPathResource resource = new ClassPathResource(resourcePath);
                try (InputStream inputStream = resource.getInputStream()) {
                    document.setDocument(new InputFile(inputStream, fileName));


                    Message sentMessage = sender.execute(document);

                    if (sentMessage.hasDocument()) {
                        String newFileId = sentMessage.getDocument().getFileId();
                        fileIdCache.put(resourcePath, newFileId);
                        log.info("Файл '{}' успешно закэширован в памяти. Присвоен File ID: {}", resourcePath, newFileId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при работе с файлом '{}' для чата {}", resourcePath, chatId, e);
        }
    }

    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setText(text);
        btn.setCallbackData(callbackData);
        return btn;
    }
}
