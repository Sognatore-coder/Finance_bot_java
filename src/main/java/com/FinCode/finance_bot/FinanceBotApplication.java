package com.FinCode.finance_bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class FinanceBotApplication {

	public static void main(String[] args) {
		SpringApplication.run(FinanceBotApplication.class, args);
	}

}
