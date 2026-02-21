# IDMachine

**IDMachine** is a specialized Java backend designed to get IDs from 1001Tracklists per artist.

## The Inspiration
I love the EDM scene. Whether it be raves or festivals, I love the music. 
The genres I love: **Minimal house, tech house, drum and bass, Dubstep, Trap, UKG.**

Whenever I go to a show or festival I am always keen on new songs (or IDs) played by the DJs and oftentimes I look back on the sets I listen to and find those particular IDs and track them. 

## The Problem
Specifically I use 1001Tracklists, but what I noticed is that they only have full sets played by DJs. I wanted a way to:
* **Filter IDs by DJ.**
* **Filter by if the DJ played a particular ID tied to them or not tied to them.**

I was inspired after listening to the **Prospa Creamfields set** where I noticed a lot of IDs played on 1001Tracklists and I wanted a simpler way to track them. Listen to it if you have the chance :) https://soundcloud.com/user-531094569/prospa-live-at-creamfields

## Technical Implementation
I am building the backend using a modern stack to handle the complexity of data scraping and cross-platform tracking:

* **Language:** Java 21 (Utilizing **Virtual Threads** for high-performance concurrent scraping).
* **Framework:** Spring Boot 3.
* **Database:** PostgreSQL (To store the "Source of Truth" for tracked IDs).
* **Data Ingestion:** JSoup (Custom-built internal API to scrape 1001Tracklists DOM).
* **Cross-Referencing:** Integration with Spotify and SoundCloud APIs to identify when an ID finally gets a title and a release.



## How it is planned to work
1. **Search:** You search for a specific Artist.
2. **Scrape:** The backend pulls the most recent IDs from recent fests via 1001Tracklists.
3. **Match:** The system compares those IDs with data from SoundCloud and Spotify.
4. **Listen:** It provides a way to listen to the tracks directly based on the artist matching.

---
