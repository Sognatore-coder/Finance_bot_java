package com.FinCode.finance_bot.feature.registration;

public enum BotState {
    MAIN_MENU,          // Главное меню (информационный блок)
    FILLING_FIO,        // Ожидание ввода ФИО
    FILLING_CITY,       // Ожидание ввода города
    FILLING_INDUSTRY,   // Ожидание ввода отрасли бизнеса
    FILLING_TERM,       // Ожидание ввода срока договора
    FILLING_CONTACT_METHOD, // Метод коммуникации
    FILLING_CONTACT_VALUE,  // Ввод контакта с валидацией
    FILLING_COMMENTS,   // Ожидание ввода комментариев
    AWAITING_CONFIRMATION // Финальное окно политики конфиденциальности
}
