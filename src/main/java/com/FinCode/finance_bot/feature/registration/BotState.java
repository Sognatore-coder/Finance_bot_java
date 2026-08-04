package com.FinCode.finance_bot.feature.registration;

public enum BotState {
    MAIN_MENU,          // Главное меню (информационный блок)
    FILLING_FIO,        // Ожидание ввода ФИО
    FILLING_CITY,       // Ожидание ввода города
    FILLING_INDUSTRY,   // Ожидание ввода отрасли бизнеса
    FILLING_TARIFF,     // Ожидание выбора тарифа (кнопки)
    FILLING_TERM,       // Ожидание ввода срока договора
    FILLING_COMMENTS,   // Ожидание ввода комментариев
    AWAITING_OFERTA     // Ожидание подтверждения оферты персональных данных
}
