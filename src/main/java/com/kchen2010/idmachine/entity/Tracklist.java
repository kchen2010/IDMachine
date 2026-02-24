package com.kchen2010.idmachine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tracklists")
public class Tracklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "artist_id", nullable = false)
    private Artist artist;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, unique = true)
    private String url;

    private LocalDate eventDate;

    private String venue;

    @Column(nullable = false)
    private LocalDateTime scrapedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "tracklist", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TrackEntry> tracks = new ArrayList<>();
}
