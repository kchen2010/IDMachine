package com.kchen2010.idmachine.scraper;

import com.kchen2010.idmachine.entity.Artist;
import com.kchen2010.idmachine.entity.Tracklist;
import com.kchen2010.idmachine.entity.TrackEntry;
import com.kchen2010.idmachine.repository.ArtistRepository;
import com.kchen2010.idmachine.repository.TracklistRepository;
import com.kchen2010.idmachine.repository.TrackEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TracklistScraper {

    private static final String BASE_URL = "https://www.1001tracklists.com";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ArtistRepository artistRepository;
    private final TracklistRepository tracklistRepository;
    private final TrackEntryRepository trackEntryRepository;
    private final FlareSolverrClient flareSolverr;

    /**
     * Entry point — scrape all tracklists for a given artist slug.
     * Slug is the artist's 1001Tracklists URL identifier e.g. "prospa".
     */
    public Artist scrapeArtist(String slug) throws IOException {
        Artist artist = artistRepository.findBySlug(slug).orElseGet(() -> {
            Artist a = new Artist();
            a.setSlug(slug);
            return a;
        });

        String artistUrl = BASE_URL + "/dj/" + slug + "/index.html";
        log.info("Scraping artist page: {}", artistUrl);

        Document doc = Jsoup.parse(flareSolverr.fetchHtml(artistUrl), artistUrl);
        log.info("Artist page title: '{}' | body length: {}", doc.title(), doc.body().text().length());

        // Extract artist name from page title e.g. "Prospa Tracklists Overview"
        if (artist.getName() == null || artist.getName().isBlank()) {
            String pageTitle = doc.select("#pageTitle h1").text();
            artist.setName(pageTitle.replace("Tracklists Overview", "").trim());
        }

        artist = artistRepository.save(artist);

        // Each set row on the overview page is a div.bItm.action.oItm
        Elements setRows = doc.select("div.bItm.action.oItm");
        log.info("Found {} tracklists for {}", setRows.size(), artist.getName());

        for (Element row : setRows) {
            String relativeUrl = row.select(".bTitle a").attr("href");
            if (relativeUrl.isBlank()) continue;

            String fullUrl = BASE_URL + relativeUrl;

            // Skip if already scraped
            if (tracklistRepository.findByUrl(fullUrl).isPresent()) {
                log.debug("Skipping already-scraped tracklist: {}", fullUrl);
                continue;
            }

            try {
                scrapeTracklist(artist, row, fullUrl);
            } catch (IOException e) {
                log.warn("Failed to scrape tracklist {}: {}", fullUrl, e.getMessage());
            }
        }

        return artist;
    }

    /**
     * Scrapes a single tracklist detail page and persists all track entries.
     */
    private void scrapeTracklist(Artist artist, Element overviewRow, String url) throws IOException {
        String title = overviewRow.select(".bTitle a").text();
        String dateText = overviewRow.select("div[title=tracklist date]").text().trim();
        String venueText = overviewRow.select("div[title=musicstyle(s)]").text().trim();

        Tracklist tracklist = new Tracklist();
        tracklist.setArtist(artist);
        tracklist.setTitle(title);
        tracklist.setUrl(url);
        tracklist.setVenue(venueText.isBlank() ? null : venueText);

        try {
            tracklist.setEventDate(LocalDate.parse(dateText, DATE_FORMAT));
        } catch (Exception e) {
            log.debug("Could not parse date '{}' for tracklist: {}", dateText, url);
        }

        tracklist = tracklistRepository.save(tracklist);

        // Scrape the detail page for individual tracks
        log.info("Scraping tracklist: {}", url);
        Document doc = Jsoup.parse(flareSolverr.fetchHtml(url), url);

        List<TrackEntry> entries = parseTracks(doc, tracklist, artist.getName());
        trackEntryRepository.saveAll(entries);
        log.info("Saved {} tracks for: {}", entries.size(), title);
    }

    /**
     * Parses individual track entries from a tracklist detail page.
     *
     * DOM structure confirmed from 1001Tracklists detail pages:
     *
     *   Track rows:   div.tlpItem
     *   Position:     span[id$=_tracknumber_value]  — "w/" prefix means played-together, skip
     *
     *   Track types detected via data attributes on the row:
     *     data-isid="true"    → full ID (both artist and title unknown, shows "ID - ID")
     *     data-isided="true"  → fully identified track
     *     neither attribute   → "Artist - ID" (artist known, title is ID — ownId candidate)
     *
     *   Artist / title extraction (schema.org microdata inside each row):
     *     meta[itemprop=byArtist]  → artist name
     *     meta[itemprop=name]      → "Artist - Title" combined; title = everything after " - "
     *
     *   For "Artist - ID" rows, fall back to span.trackValue span.blueTxt for the artist name.
     */
    private List<TrackEntry> parseTracks(Document doc, Tracklist tracklist, String djName) {
        List<TrackEntry> entries = new ArrayList<>();

        Elements trackRows = doc.select("div.tlpItem");

        int position = 1;
        for (Element row : trackRows) {
            // "w/" rows are played-together continuations — they share a slot with the previous track
            String trackNumText = row.select("span[id$=_tracknumber_value]").text().trim();
            if (trackNumText.startsWith("w/")) continue;

            boolean isFullId     = "true".equals(row.attr("data-isid"));
            boolean isIdentified = "true".equals(row.attr("data-isided"));

            String trackArtist = null;
            String trackTitle  = null;
            boolean isId;
            boolean isOwnId = false;

            if (isFullId) {
                // "ID - ID": both artist and title unknown
                isId = true;

            } else if (isIdentified) {
                // Fully identified track — pull artist and title from schema.org meta
                isId = false;
                trackArtist = row.select("meta[itemprop=byArtist]").attr("content").trim();
                String fullName = row.select("meta[itemprop=name]").attr("content").trim();
                int sep = fullName.indexOf(" - ");
                trackTitle = sep >= 0 ? fullName.substring(sep + 3).trim() : fullName;

            } else {
                // "Artist - ID": artist is known but title is unreleased
                isId = true;
                // Prefer schema.org meta; fall back to the blue-text artist span
                String metaArtist = row.select("meta[itemprop=byArtist]").attr("content").trim();
                if (!metaArtist.isEmpty()) {
                    trackArtist = metaArtist;
                } else {
                    Element blueSpan = row.select("span.trackValue span.blueTxt").first();
                    trackArtist = blueSpan != null ? blueSpan.text().trim() : null;
                }
                isOwnId = djName != null && djName.equalsIgnoreCase(trackArtist);
            }

            TrackEntry entry = new TrackEntry();
            entry.setTracklist(tracklist);
            entry.setPosition(position++);
            entry.setTitle(trackTitle);
            entry.setTrackArtist(trackArtist);
            entry.setIdTrack(isId);
            entry.setOwnId(isOwnId);

            entries.add(entry);
        }

        return entries;
    }
}
