package com.kchen2010.idmachine.dto;

import java.util.List;

public record IdsReport(
        String artistName,
        int totalIds,
        int ownIds,
        int otherIds,
        List<TrackEntryView> entries
) {}
