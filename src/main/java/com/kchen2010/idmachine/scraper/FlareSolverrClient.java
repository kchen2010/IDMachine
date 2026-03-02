package com.kchen2010.idmachine.scraper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;

/**
 * Routes HTTP requests through a local FlareSolverr instance so that
 * Cloudflare-protected pages are fetched via a real headless Chrome browser.
 *
 * FlareSolverr must be running before the scraper is called:
 *   docker run -p 8191:8191 ghcr.io/flaresolverr/flaresolverr:latest
 *
 * API docs: https://github.com/FlareSolverr/FlareSolverr
 */
@Slf4j
@Service
public class FlareSolverrClient {

    @Value("${flaresolverr.url:http://localhost:8191/v1}")
    private String solverrUrl;

    private final RestClient restClient = RestClient.create();

    /**
     * Fetches the fully-rendered HTML for the given URL via FlareSolverr.
     *
     * @throws IOException if FlareSolverr is unreachable or returns an error status
     */
    public String fetchHtml(String targetUrl) throws IOException {
        log.debug("FlareSolverr → {}", targetUrl);

        var body = new SolverrRequest("request.get", targetUrl, 60_000);

        SolverrResponse response;
        try {
            response = restClient.post()
                    .uri(solverrUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(SolverrResponse.class);
        } catch (Exception e) {
            throw new IOException(
                    "FlareSolverr is unreachable at " + solverrUrl +
                    " — is the container running?  " + e.getMessage(), e);
        }

        if (response == null || !"ok".equals(response.status())) {
            String status = response != null ? response.status() : "null";
            throw new IOException("FlareSolverr returned '" + status + "' for: " + targetUrl);
        }

        String html = response.solution() != null ? response.solution().response() : null;
        if (html == null) html = "";
        log.info("FlareSolverr response: htmlLen={} snippet='{}'",
                html.length(), html.substring(0, Math.min(200, html.length())).replaceAll("\\s+", " "));
        return html;
    }

    // ── request / response records ────────────────────────────────────────────

    record SolverrRequest(String cmd, String url, int maxTimeout) {}

    record SolverrResponse(String status, String message, Solution solution) {
        record Solution(String url, int status, String response) {}
    }
}
