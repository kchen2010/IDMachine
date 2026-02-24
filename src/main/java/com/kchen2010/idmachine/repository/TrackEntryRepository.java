package com.kchen2010.idmachine.repository;

import com.kchen2010.idmachine.entity.TrackEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrackEntryRepository extends JpaRepository<TrackEntry, Long> {

    List<TrackEntry> findAllByTracklistId(Long tracklistId);

    // All IDs played by an artist
    List<TrackEntry> findAllByTracklistArtistIdAndIdTrue(Long artistId);

    // IDs that are the DJ's own unreleased tracks
    List<TrackEntry> findAllByTracklistArtistIdAndIdTrueAndOwnIdTrue(Long artistId);

    // IDs that belong to someone else
    List<TrackEntry> findAllByTracklistArtistIdAndIdTrueAndOwnIdFalse(Long artistId);
}
