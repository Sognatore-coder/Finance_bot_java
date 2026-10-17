package com.FinCode.finance_bot.service;
import com.FinCode.finance_bot.database.entity.UserApp;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.MessagingException;

@Service
@Slf4j
public class EmailService {
    private final JavaMailSender mailSender;

    @Value("${bot.director.email}")
    private String directorEmail;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendAnketaToDirector(UserApp user) {
        log.info("Формирование Email-письма для анкеты чата {}", user.getChatId());

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper message = new MimeMessageHelper(mimeMessage, false, "UTF-8");

            message.setFrom(fromEmail);
            message.setTo(directorEmail);
            message.setSubject("📊 Новая заполненная анкета клиента (Telegram Bot)");

            String emailBody = String.format(
                    "Уважаемый Финансовый Директор!\n\n" +
                            "Через Telegram-бота была получена новая анкета от потенциального клиента.\n" +
                            "Вот актуальные валидированные данные из базы данных:\n\n" +
                            "--------------------------------------------------\n" +
                            "🔹 ID чата Telegram: %d\n" +
                            "🔹 ФИО клиента: %s\n" +
                            "🔹 Город: %s\n" +
                            "🔹 Отрасль бизнеса: %s\n" +
                            "🔹 Наименование услуги: %s\n" +
                            "🔹 Способ коммуникации: %s\n" +
                            "🔹 Контактные данные: %s\n" +
                            "--------------------------------------------------\n" +
                            "💬 Дополнительные комментарии клиента:\n%s\n\n" +
                            "Письмо сгенерировано автоматически. Пожалуйста, свяжитесь с клиентом в течение рабочего дня.",
                    user.getChatId(),
                    user.getFio(),
                    user.getCity(),
                    user.getIndustry(),
                    user.getServiceType(),
                    user.getContactMethod(),
                    user.getContactValue(),
                    user.getComments()
            );

            message.setText(emailBody);

            mailSender.send(mimeMessage);
            log.info("Письмо с анкетой чата {} успешно отправлено на адрес: {}", user.getChatId(), directorEmail);

        } catch (MessagingException e) {
            log.error("Чат {}: ошибка формирования письма (MimeMessage)", user.getChatId(), e);
            throw new RuntimeException("Не удалось сформировать письмо", e);
        }
    }
}
