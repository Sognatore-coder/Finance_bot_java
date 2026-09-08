package com.FinCode.finance_bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;

@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        MailSenderAutoConfiguration.class
})
public class FinanceBotApplication {

	public static void main(String[] args) {
		SpringApplication.run(FinanceBotApplication.class, args);
	}

}
