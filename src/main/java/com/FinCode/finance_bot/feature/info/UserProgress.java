package com.FinCode.finance_bot.feature.info;

import lombok.Data;

@Data
public class UserProgress {
    private boolean viewedResume = false;
    private boolean viewedDemo = false;
    private boolean viewedCalculator = false;

    public boolean isAllViewed() {
        return viewedResume && viewedDemo && viewedCalculator;
    }
}
