package com.kchen2010.idmachine.dto;

import java.time.LocalDate;

public record TrackEntryView(
        Long id,
        int position,
        String trackArtist,
        String title,
        boolean ownId,
        String tracklist,
        LocalDate eventDate
) {}
