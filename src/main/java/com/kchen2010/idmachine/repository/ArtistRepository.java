package com.kchen2010.idmachine.repository;

import com.kchen2010.idmachine.entity.Artist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArtistRepository extends JpaRepository<Artist, Long> {

    Optional<Artist> findBySlug(String slug);

    Optional<Artist> findByNameIgnoreCase(String name);
}
