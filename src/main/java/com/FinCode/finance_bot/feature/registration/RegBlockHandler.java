package com.FinCode.finance_bot.feature.registration;

import com.FinCode.finance_bot.database.entity.UserApp;
import com.FinCode.finance_bot.database.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.bots.AbsSender;

import jakarta.mail.internet.InternetAddress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Component
@Slf4j
public class RegBlockHandler {

    private final UserRepository userRepository;


    public RegBlockHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Очистка истории
    public void clearChatHistory(AbsSender sender, long chatId, List<Integer> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) return;

        synchronized (messageIds) {
            log.info("Чат {}: запуск очистки истории чата ({} сообщений)", chatId, messageIds.size());
            for (Integer messageId : messageIds) {
                DeleteMessage deleteMessage = new DeleteMessage(String.valueOf(chatId), messageId);
                try {
                    sender.execute(deleteMessage);
                } catch (Exception e) {
                    log.debug("Чат {}: не удалось удалить сообщение {}: {}", chatId, messageId, e.getMessage());
                }
            }
            messageIds.clear();
        }
    }

    // Старт анкеты
    public SendMessage startAnketa(UserApp user) {
        user.setBotState(BotState.FILLING_FIO);
        userRepository.save(user);

        log.info("Чат {}: пользователь начал заполнение анкеты", user.getChatId());
        return new SendMessage(String.valueOf(user.getChatId()), "📝 Начинаем заполнение договора.\n\nВведите ваше ФИО:");
    }

    // Пошаговое анктирование
    public SendMessage handleInput(UserApp user, String text) {
        long chatId = user.getChatId();
        SendMessage response = new SendMessage();
        response.setChatId(String.valueOf(chatId));

        switch (user.getBotState()) {
            case FILLING_FIO -> {
                user.setFio(text);
                user.setBotState(BotState.FILLING_CITY);
                userRepository.save(user);
                response.setText("📍 Введите ваш город:");
            }
            case FILLING_CITY -> {
                user.setCity(text);
                user.setBotState(BotState.FILLING_INDUSTRY);
                userRepository.save(user);
                response.setText("🏢 Укажите вашу отрасль бизнеса:");
            }
            case FILLING_INDUSTRY -> {
                user.setIndustry(text);
                // [ИЗМЕНЕНО] Вместо срока выводим клавиатуру выбора услуг
                user.setBotState(BotState.FILLING_SERVICE_METHOD);
                userRepository.save(user);
                response.setText("📊 Выберите интересующую вас услугу:");
                response.setReplyMarkup(createServicesKeyboard());
            }
            case FILLING_SERVICE_CUSTOM -> {
                // [НОВЫЙ ШАГ] Сюда попадаем, если пользователь нажал "Другое" и написал текст руками
                user.setServiceType("Другое: " + text.trim());
                user.setBotState(BotState.FILLING_CONTACT_METHOD);
                userRepository.save(user);
                log.info("Чат {}: вручную введена услуга: '{}'", chatId, text);
                response.setText("📞 Выберите удобный формат коммуникации для связи:");
                response.setReplyMarkup(createContactMethodKeyboard());
            }
            case FILLING_CONTACT_VALUE -> {
                String validationResult = validateAndCleanContact(user.getContactMethod(), text);

                if (validationResult == null) {
                    log.warn("Чат {}: ошибка валидации контакта для типа [{}]", chatId, user.getContactMethod());
                    response.setText(getErrorMessage(user.getContactMethod()));
                } else {
                    user.setContactValue(validationResult);
                    user.setBotState(BotState.FILLING_COMMENTS);
                    userRepository.save(user);
                    response.setText("💬 Введите дополнительные комментарии, проблемы, которые касаются вашего бизнеса," +
                            " чтоб исполнитель заранее знал, с чем ему предстоит работать (или отправьте дефис '-', если комментариев нет):");
                }
            }
            case FILLING_COMMENTS -> {
                user.setComments(text);
                user.setBotState(BotState.AWAITING_CONFIRMATION);
                userRepository.save(user);

                log.info("Чат {}: анкета успешно заполнена, ожидает подтверждения", chatId);

                String privacyText = "🔒 Политика конфиденциальности\n\n" +
                        "Обратите внимание: мы собираем и сохраняем ваши персональные данные " +
                        "исключительно для удобства работы нашего исполнителя (финансового директора). " +
                        "Это необходимо, чтобы заранее подготовить проект договора и сэкономить ваше время на созвоне.\n\n" +
                        "Данные передаются напрямую исполнителю, строго защищены и никогда не будут переданы третьим лицам.";

                response.setText(privacyText);
                response.setReplyMarkup(createConfirmationKeyboard());
            }
            default -> response.setText("⚠️ Пожалуйста, используйте кнопки на экране или следуйте инструкциям.");
        }
        return response;
    }

    // Обработка кнопки "Услуги"
    public SendMessage handleServiceCallback(UserApp user, String callbackData, AbsSender sender) {
        long chatId = user.getChatId();
        String action = callbackData.replace("service_", "").toUpperCase();

        if (action.equals("OTHER")) {
            user.setBotState(BotState.FILLING_SERVICE_CUSTOM);
            userRepository.save(user);
            log.info("Чат {}: выбрана опция услуги [Другое]. Ожидание ручного ввода.", chatId);
            return new SendMessage(String.valueOf(chatId), "📝 Напишите, пожалуйста, наименование услуги, которая вам интересна:");
        } else {
            String serviceName = switch (action) {
                case "AUDIT" -> "Аудит";
                case "ACCOUNTING" -> "Учет";
                case "MODEL" -> "Фин. модель";
                case "ANALYSIS" -> "Анализ";
                default -> action;
            };
            user.setServiceType(serviceName);
            user.setBotState(BotState.FILLING_CONTACT_METHOD);
            userRepository.save(user);
            log.info("Чат {}: выбрана услуга через кнопку: [{}]", chatId, serviceName);

            try {
                SendMessage choiceMessage = new SendMessage();
                choiceMessage.setChatId(String.valueOf(chatId));
                choiceMessage.setText("Выбрана услуга: " + serviceName);
                sender.execute(choiceMessage); // Отправляем мгновенно в чат
            } catch (Exception e) {
                log.error("Ошибка при отправке сообщения-фиксации выбора услуги", e);
            }

            SendMessage nextStep = new SendMessage(String.valueOf(chatId), "📞 Выберите удобный формат коммуникации для связи:");
            nextStep.setReplyMarkup(createContactMethodKeyboard());
            return nextStep;
        }
    }

    // Обработка нажатия на кнопку "способ связи"
    public SendMessage handleContactMethodCallback(UserApp user, String callbackData) {
        String method = callbackData.replace("contact_", "").toUpperCase();
        user.setContactMethod(method);
        user.setBotState(BotState.FILLING_CONTACT_VALUE);
        userRepository.save(user);

        log.info("Чат {}: выбран способ связи [{}]", user.getChatId(), method);

        String promptText = switch (method) {
            case "TELEGRAM" -> "Пожалуйста, введите ваш Telegram аккаунт (аккаунт должен обязательно начинаться со знака @, например: @default):";
            case "EMAIL" -> "Пожалуйста, введите ваш адрес электронной почты (например: example@gmail.com):";
            case "PHONE" -> "Пожалуйста, введите ваш номер телефона:";
            default -> "Введите контактные данные:";
        };

        return new SendMessage(String.valueOf(user.getChatId()), promptText);
    }

    // Метод валидации и очистки данных в зависимости от выбранного типа.
    private String validateAndCleanContact(String method, String text) {
        if (text == null) return null;
        String trimmed = text.trim();

        return switch (method) {
            case "TELEGRAM" -> {
                if (trimmed.startsWith("@") && trimmed.length() > 2) {
                    yield trimmed;
                }
                yield null;
            }
            case "EMAIL" -> {
                try {
                    InternetAddress emailAddr = new InternetAddress(trimmed);

                    emailAddr.validate();

                    // Проверяем, что в адресе есть точка после символа '@'
                    if (trimmed.contains("@") && trimmed.substring(trimmed.indexOf("@")).contains(".")) {
                        yield trimmed;
                    }
                    yield null;
                } catch (Exception e) {
                    yield null;
                }
            }
            case "PHONE" -> {
                String digitsOnly = trimmed.replaceAll("[^0-9]", "");
                if (digitsOnly.length() >= 10 && digitsOnly.length() <= 15) {
                    yield digitsOnly;
                }
                yield null;
            }
            default -> null;
        };
    }

    // Тексты ошибок для пользователя при неверном вводе
    private String getErrorMessage(String method) {
        return switch (method) {
            case "TELEGRAM" -> "❌ Неверный формат! Имя аккаунта Telegram должно обязательно начинаться со знака @ (например: @default). Попробуйте еще раз:";
            case "EMAIL" -> "❌ Неверный формат почты! Адрес должен содержать знак @ и домен (например: client@gmail.com). Попробуйте еще раз:";
            case "PHONE" -> "❌ Неверный формат номера! Убедитесь, что вы ввели корректный номер телефона (минимум 10 цифр). Попробуйте еще раз:";
            default -> "❌ Формат указан неверно. Попробуйте еще раз:";
        };
    }

    // Клавиатура выбора формата отклика
    private InlineKeyboardMarkup createContactMethodKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(Collections.singletonList(createButton("💬 Telegram аккаунт", "contact_telegram")));
        rows.add(Collections.singletonList(createButton("📧 Электронная почта", "contact_email")));
        rows.add(Collections.singletonList(createButton("📞 Телефонный звонок", "contact_phone")));
        markup.setKeyboard(rows);
        return markup;
    }

    // Клавиатура финального подтверждения данных
    private InlineKeyboardMarkup createConfirmationKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(Collections.singletonList(createButton("🤝 Отправить анкету", "accept_privacy")));
        markup.setKeyboard(rows);
        return markup;
    }

    // Клавиатура услуг
    private InlineKeyboardMarkup createServicesKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(createButton("Аудит", "service_audit"), createButton("Учет", "service_accounting")));
        rows.add(List.of(createButton("Фин. модель", "service_model"), createButton("Анализ", "service_analysis")));
        rows.add(Collections.singletonList(createButton("Другое", "service_other")));
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setText(text);
        btn.setCallbackData(callbackData);
        return btn;
    }
}