package com.kchen2010.idmachine.dto;

import java.time.LocalDate;

public record TracklistSummary(Long id, String title, String url, LocalDate eventDate, String venue, long trackCount) {}
