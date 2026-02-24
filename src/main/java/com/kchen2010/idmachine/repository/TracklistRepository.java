package com.kchen2010.idmachine.repository;

import com.kchen2010.idmachine.entity.Tracklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TracklistRepository extends JpaRepository<Tracklist, Long> {

    List<Tracklist> findAllByArtistId(Long artistId);

    Optional<Tracklist> findByUrl(String url);
}
