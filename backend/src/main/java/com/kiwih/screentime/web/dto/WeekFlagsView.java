package com.kiwih.screentime.web.dto;

import java.time.LocalDate;

public record WeekFlagsView(LocalDate weekStart, boolean holiday, boolean bonusActive) {
}
