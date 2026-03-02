package com.kchen2010.idmachine.controller;

import com.kchen2010.idmachine.dto.ArtistSummary;
import com.kchen2010.idmachine.dto.IdsReport;
import com.kchen2010.idmachine.dto.TrackEntryView;
import com.kchen2010.idmachine.dto.TracklistSummary;
import com.kchen2010.idmachine.entity.Artist;
import com.kchen2010.idmachine.entity.TrackEntry;
import com.kchen2010.idmachine.repository.ArtistRepository;
import com.kchen2010.idmachine.repository.TrackEntryRepository;
import com.kchen2010.idmachine.repository.TracklistRepository;
import com.kchen2010.idmachine.scraper.TracklistScraper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/artists")
@RequiredArgsConstructor
public class ArtistController {

    private final TracklistScraper scraper;
    private final ArtistRepository artistRepository;
    private final TracklistRepository tracklistRepository;
    private final TrackEntryRepository trackEntryRepository;

    /** List all artists that have been scraped. */
    @GetMapping
    public List<ArtistSummary> listArtists() {
        return artistRepository.findAll().stream()
                .map(a -> new ArtistSummary(a.getId(), a.getName(), a.getSlug(), a.getTracklists().size()))
                .toList();
    }

    /** Get a single artist summary by slug. */
    @GetMapping("/{slug}")
    public ArtistSummary getArtist(@PathVariable String slug) {
        Artist a = findArtistOrThrow(slug);
        return new ArtistSummary(a.getId(), a.getName(), a.getSlug(), a.getTracklists().size());
    }

    /**
     * Trigger a scrape for the given artist slug.
     * If the artist already exists, only new tracklists are scraped (idempotent).
     */
    @PostMapping("/{slug}/scrape")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ArtistSummary scrape(@PathVariable String slug) {
        try {
            Artist a = scraper.scrapeArtist(slug);
            return new ArtistSummary(a.getId(), a.getName(), a.getSlug(), a.getTracklists().size());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Scrape failed — is FlareSolverr running? " + e.getMessage(), e);
        }
    }

    /** List all tracklists for an artist. */
    @GetMapping("/{slug}/tracklists")
    public List<TracklistSummary> getTracklists(@PathVariable String slug) {
        Artist a = findArtistOrThrow(slug);
        return tracklistRepository.findAllByArtistId(a.getId()).stream()
                .map(tl -> new TracklistSummary(
                        tl.getId(), tl.getTitle(), tl.getUrl(),
                        tl.getEventDate(), tl.getVenue(),
                        trackEntryRepository.countByTracklistId(tl.getId())))
                .toList();
    }

    /**
     * Return all ID tracks for an artist.
     *
     * @param own  Optional filter: true = own IDs only, false = others' IDs only, absent = all IDs
     */
    @GetMapping("/{slug}/ids")
    public IdsReport getIds(@PathVariable String slug,
                            @RequestParam(required = false) Boolean own) {
        Artist a = findArtistOrThrow(slug);
        Long artistId = a.getId();

        List<TrackEntry> entries = own == null
                ? trackEntryRepository.findAllByTracklistArtistIdAndIdTrackTrue(artistId)
                : own
                    ? trackEntryRepository.findAllByTracklistArtistIdAndIdTrackTrueAndOwnIdTrue(artistId)
                    : trackEntryRepository.findAllByTracklistArtistIdAndIdTrackTrueAndOwnIdFalse(artistId);

        List<TrackEntryView> views = entries.stream()
                .map(e -> new TrackEntryView(
                        e.getId(),
                        e.getPosition(),
                        e.getTrackArtist(),
                        e.getTitle(),
                        e.isOwnId(),
                        e.getTracklist().getTitle(),
                        e.getTracklist().getEventDate()))
                .toList();

        int ownCount   = (int) views.stream().filter(TrackEntryView::ownId).count();
        int otherCount = views.size() - ownCount;

        return new IdsReport(a.getName(), views.size(), ownCount, otherCount, views);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Artist findArtistOrThrow(String slug) {
        return artistRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Artist not found: " + slug));
    }
}
