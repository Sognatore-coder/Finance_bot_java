package com.FinCode.finance_bot.feature.calculator;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CalculatorSession {
    private CalculatorState state;

    // Калькулятор 1
    private Double cost;              // C — себестоимость

    // Калькулятор 2
    private Double fixedCosts;        // постоянные издержки бизнеса за месяц
    private Double price;             // цена продажи единицы товара

    private final List<Integer> messageIds = new ArrayList<>();

    private Runnable onFinishCallback;

    public void trackMessage(int messageId) {
        messageIds.add(messageId);
    }

}
