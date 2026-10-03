package com.kiwih.screentime.web.dto;

/** One item the child has to tick today: the id to send back, and the text. */
public record ChecklistDueView(Long id, String text) {
}
