package com.kchen2010.idmachine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "track_entries")
public class TrackEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tracklist_id", nullable = false)
    private Tracklist tracklist;

    @Column(nullable = false)
    private int position;

    private String title;       // null if unknown
    private String trackArtist; // null if unknown

    @Column(nullable = false)
    private boolean id;    // true if listed as "ID" on 1001tracklists

    @Column(nullable = false)
    private boolean ownId; // true if the ID belongs to the DJ playing the set

    private String spotifyId;

    private String soundcloudId;
}
