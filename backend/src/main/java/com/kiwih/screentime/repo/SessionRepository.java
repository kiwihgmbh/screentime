package com.kiwih.screentime.repo;

import com.kiwih.screentime.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {

    Optional<Session> findByUserIdAndEndedAtIsNull(Long userId);

    List<Session> findByEndedAtIsNull();

    /**
     * Sessions that started inside a half open instant range. The caller
     * converts the local day or week to instants, so the time zone lives in
     * one place only.
     */
    @Query("""
            select s from Session s
            where s.userId = :userId
              and s.startedAt >= :from
              and s.startedAt < :to
            order by s.startedAt asc
            """)
    List<Session> findStartedBetween(@Param("userId") Long userId,
                                     @Param("from") Instant from,
                                     @Param("to") Instant to);

    @Query("""
            select s from Session s
            where s.startedAt >= :from
              and s.startedAt < :to
            order by s.startedAt asc
            """)
    List<Session> findStartedBetween(@Param("from") Instant from,
                                     @Param("to") Instant to);
}
