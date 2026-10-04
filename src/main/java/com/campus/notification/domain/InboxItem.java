package com.campus.notification.domain;

public record InboxItem(NotificationDelivery delivery, String title, String body) { }
