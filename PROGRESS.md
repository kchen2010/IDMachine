# IDMachine — Build Progress

## So what even is this thing?

Basically when you go to a rave or festival and the DJ drops a unrelease song you like 
IDMachine is a Java backend that scrapes 1001Tracklists for a given artist and pulls out all their IDs. The two main things you can filter by:
- All IDs a DJ has played across their sets
- Whether that ID is **their own** unreleased track vs something they played from another artist

The end goal is to also hook it up to Spotify and SoundCloud so when one of those IDs finally drops, you already know about it.

---

## The Stack

| Technology | Version | What it's doing |
|---|---|---|
| Java | 25 | Language |
| Spring Boot | 4.0.3 | The whole application framework |
| Spring Data JPA | (via Boot) | How we talk to the database |
| Hibernate | (via JPA) | The actual ORM doing the heavy lifting under JPA |
| PostgreSQL | 16 | Production database — the source of truth |
| H2 | (via Boot) | Fake in-memory database for local dev (no installs needed) |
| JSoup | 1.22.1 | What actually scrapes the 1001Tracklists HTML |
| Lombok | (via Boot) | Cuts out all the boilerplate Java (getters, setters, constructors) |
| Docker Compose | — | Spins up PostgreSQL in a container so you don't have to install it |
| Maven | 3.x (wrapper) | Builds the project |

### Why these specifically?

**Java Virtual Threads (Project Loom)**
When you're scraping a site, you're making a ton of HTTP requests and just waiting on responses. The old way of handling that was either expensive OS threads (one per request) or reactive programming (WebFlux), which works but turns your code into a callback nightmare. Virtual threads are Java's answer to that — they're super lightweight, the JVM manages them, and you can run thousands of them concurrently without breaking a sweat. Best part? The code still looks like normal synchronous Java. No reactive spaghetti.

**Spring Boot 4**
This is the first Spring version on Jakarta EE 10 basically they renamed all the `javax.*` packages to `jakarta.*`. It also has first-class support for virtual threads out of the box via one config flag: `spring.threads.virtual.enabled=true`.

**Spring Data JPA + Hibernate**
JPA is the standard Java way to map objects to database tables. Hibernate is the implementation that actually does it. The cool thing with Spring Data is you write an interface with method names like `findAllByTracklistArtistIdAndIdTrue` and it just... generates the SQL for you. No boilerplate, no SQL strings to maintain.

**PostgreSQL**
Standard choice for a relational backend. The data naturally fits a relational model — artists have tracklists, tracklists have tracks. Foreign keys, constraints, all of that just makes sense here.

**H2 for dev**
Zero installs. You run the app locally and H2 spins up an in-memory database automatically. There's even a browser console at `http://localhost:8080/h2-console` where you can poke around the schema. When you're ready to use real Postgres, you just flip a profile flag.

**JSoup**
It's basically the go-to HTML parser for Java. You can hit a URL, grab the response HTML, and then navigate it with CSS selectors just like you would in JavaScript. Perfect for DOM scraping.

---

## Environment Setup — How the config works

Instead of having one massive `application.properties` that you have to manually edit depending on your environment, we use **Spring profiles**. The base file just says which profile is active, and Spring loads the corresponding file on top of it.

### `application.properties` (base — always loaded)
```properties
spring.application.name=IDMachine
spring.profiles.active=prod   # change to "dev" to use H2 locally
```

### `application-dev.properties` — local development with H2
```properties
spring.datasource.url=jdbc:h2:mem:idmachine;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

spring.h2.console.enabled=true
spring.h2.console.path=/h2-console

spring.jpa.hibernate.ddl-auto=create-drop   # nuke and recreate schema on every run
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

### `application-prod.properties` — real PostgreSQL
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/idmachine
spring.datasource.username=idmachine
spring.datasource.password=idmachine

spring.jpa.hibernate.ddl-auto=update   # only apply additive changes, never drop data
spring.jpa.show-sql=false
```

### `docker-compose.yml` — spins up Postgres without installing it
```yaml
services:
  postgres:
    image: postgres:16
    container_name: idmachine-db
    environment:
      POSTGRES_DB: idmachine
      POSTGRES_USER: idmachine
      POSTGRES_PASSWORD: idmachine
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data   # data persists between restarts
```
Once Docker is installed: `docker compose up -d` and you're good.

---

## The Data Model

Three tables. They chain together like this:

```
Artist  ──<  Tracklist  ──<  TrackEntry
```

An artist has many tracklists (their sets), and each tracklist has many track entries (individual tracks in that set).

---

### `artists`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGINT | PK, auto-increment | |
| name | VARCHAR | NOT NULL | e.g. "Prospa" |
| slug | VARCHAR | NOT NULL, UNIQUE | e.g. "prospa" — used to build the 1001TL URL |
| created_at | TIMESTAMP | NOT NULL, not updatable | stamped once on creation |

**About `slug`:** 1001Tracklists URLs follow the pattern `1001tracklists.com/dj/{slug}`. Rather than storing a full URL (which could change if the site restructures), we just store the slug and build the URL at runtime. It's also unique so you can't accidentally create the same artist twice.

**Cascade:** `@OneToMany` to Tracklist with `cascade = ALL` and `orphanRemoval = true`. Delete an artist, and all their tracklists and track entries go with it. Clean.

---

### `tracklists`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGINT | PK, auto-increment | |
| artist_id | BIGINT | FK → artists.id, NOT NULL | |
| title | VARCHAR | NOT NULL | e.g. "Creamfields 2024" |
| url | VARCHAR | NOT NULL, UNIQUE | the full 1001TL URL for this set |
| event_date | DATE | nullable | when the set was played |
| venue | VARCHAR | nullable | festival or club name |
| scraped_at | TIMESTAMP | NOT NULL | last time we scraped this |

**About the UNIQUE on `url`:** Before persisting a new tracklist the scraper calls `findByUrl()` first. If it already exists, skip it. This is how we avoid scraping the same set twice.

---

### `track_entries`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGINT | PK, auto-increment | |
| tracklist_id | BIGINT | FK → tracklists.id, NOT NULL | |
| position | INT | NOT NULL | track number in the set |
| title | VARCHAR | nullable | null if unknown (it's an ID) |
| track_artist | VARCHAR | nullable | null if unknown (it's an ID) |
| is_id | BOOLEAN | NOT NULL | true if listed as "ID" on 1001TL |
| own_id | BOOLEAN | NOT NULL | true if the ID belongs to the DJ playing the set |
| spotify_id | VARCHAR | nullable | filled in once the track gets released |
| soundcloud_id | VARCHAR | nullable | same, for SoundCloud |

**The `is_id` / `own_id` thing — this is the whole point:**

On 1001Tracklists an unreleased track shows up as either:
- `"ID - ID"` — you know nothing about it
- `"Artist - ID"` — you know who made it, but not the title

`is_id = true` covers both cases. It just means "this track is unreleased/unidentified."

`own_id = true` means the artist name matches the DJ playing the set — so it's their own unreleased production. `own_id = false` means they played someone else's ID. That distinction is the main thing this app exposes that 1001Tracklists doesn't.

---

## The Repository Layer

We're using Spring Data JPA, which means we write interfaces and Spring generates all the SQL. No implementation code needed at all.

### `ArtistRepository`
```java
Optional<Artist> findBySlug(String slug);
Optional<Artist> findByNameIgnoreCase(String name);
```
`findBySlug` is what the scraper uses. `findByNameIgnoreCase` is for user searches — so "prospa", "Prospa", "PROSPA" all work.

### `TracklistRepository`
```java
List<Tracklist> findAllByArtistId(Long artistId);
Optional<Tracklist> findByUrl(String url);
```
`findByUrl` is the deduplication check.

### `TrackEntryRepository`
```java
List<TrackEntry> findAllByTracklistId(Long tracklistId);
List<TrackEntry> findAllByTracklistArtistIdAndIdTrue(Long artistId);
List<TrackEntry> findAllByTracklistArtistIdAndIdTrueAndOwnIdTrue(Long artistId);
List<TrackEntry> findAllByTracklistArtistIdAndIdTrueAndOwnIdFalse(Long artistId);
```
Those last three are the money queries. They navigate `TrackEntry → Tracklist → Artist` through the method name alone. Spring Data parses that name and generates the correct JOIN + WHERE clause automatically.

---

## Progress Tracker

| Layer | Status |
|---|---|
| Docker + PostgreSQL setup | Pending — need Docker installed |
| JPA Entities (Artist, Tracklist, TrackEntry) | Done |
| Spring Data Repositories | Done |
| Environment profiles (dev/prod) | Done |
| JSoup scraping service | Up next |
| REST controllers | Not started |
| Virtual thread config | Not started |
| Spotify API integration | Not started |
| SoundCloud API integration | Not started |

---

## What We're Doing Next

### Step 1 — JSoup Scraping Service
This is the core of the whole thing. We need a service that:
- Takes an artist slug
- Hits `1001tracklists.com/dj/{slug}` and grabs all their set URLs
- For each set, scrapes the tracklist page and parses every track entry
- Figures out which ones are IDs and whether they're `ownId` or not
- Persists everything to the database (with deduplication)

This will live in a `ScraperService` class and use JSoup under the hood.

### Step 2 — REST Controllers
Once the scraper works we need to expose it via HTTP endpoints. Something like:
- `GET /artists/{slug}/ids` — all IDs for an artist
- `GET /artists/{slug}/ids?own=true` — only their own IDs
- `GET /artists/{slug}/ids?own=false` — only IDs they played from others
- `POST /artists/{slug}/scrape` — trigger a fresh scrape

### Step 3 — Virtual Threads
One-line config change that makes the scraper able to handle many concurrent requests without needing a thread pool or reactive code:
```properties
spring.threads.virtual.enabled=true
```
We'll do this once the scraper is working so we can actually see the performance difference.

### Step 4 — Spotify Integration
Hit the Spotify API and try to match each ID (once it has a title/artist) to a real Spotify track. Store the `spotifyId` on the `TrackEntry`. This is how you get playback links.

### Step 5 — SoundCloud Integration
Same idea but for SoundCloud. A lot of IDs surface on SoundCloud before Spotify, so this is actually really useful.

---

## Interview Talking Points

**"Why not use WebFlux / reactive programming for the concurrency?"**
Virtual threads give you the same concurrency wins without the mental overhead. Reactive code is hard to read, hard to debug, and forces you to think in streams and callbacks. Virtual threads let you write normal blocking Java that reads linearly — and the JVM handles the scheduling. For a scraping workload that's mostly I/O-bound waiting on HTTP responses, virtual threads are genuinely the better call.

**"Why Spring Data query derivation instead of writing SQL?"**
For these kinds of filtered lookups — "give me all track entries where the tracklist's artist ID matches X and is_id is true" — the derived method name is actually more readable than a raw SQL string. Spring parses the method name and generates the JOIN + WHERE clause. JPQL with `@Query` is still there for anything complex, but you don't reach for it unless you need it.

**"Why cascade ALL and orphanRemoval on the relationships?"**
If you delete an Artist, you want all their tracklists and track entries gone too. `CascadeType.ALL` means any persistence operation (persist, merge, delete) cascades down the chain. `orphanRemoval = true` handles the edge case where you remove a child from the parent's collection — without it, the child just loses its FK reference and becomes orphaned data in the table.

**"Why store a slug instead of the full URL on Artist?"**
URLs are fragile. A site can change its subdomain, switch from HTTP to HTTPS, restructure its paths — the slug is the one part that's stable and meaningful. We construct the full URL at runtime from the slug so if the URL structure ever changes, we fix one line of code.

**"Why ddl-auto=create-drop in dev but update in prod?"**
`create-drop` blows away and recreates the whole schema on every app startup. That's great for dev — you always start fresh. In prod that would be catastrophic, you'd wipe all your data. `update` only applies additive changes (new columns, new tables) and never touches existing data.

**"Why H2 for local development?"**
Nobody wants to install and configure a database just to run a project locally. H2 is bundled as a dependency and starts automatically in-memory when the app boots. There's even a browser-based console at `/h2-console` so you can inspect the schema and run queries. When you're ready for real Postgres, you change one line in `application.properties`.
