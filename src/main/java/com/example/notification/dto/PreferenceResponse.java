package com.example.notification.dto;
public record PreferenceResponse(String userId, boolean emailEnabled, boolean smsEnabled, boolean pushEnabled){}
