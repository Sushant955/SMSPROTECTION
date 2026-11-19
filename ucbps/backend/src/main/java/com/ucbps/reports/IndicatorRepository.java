package com.ucbps.reports;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IndicatorRepository extends JpaRepository<ThreatIndicator, Long> {

    long countByNumberHash(String numberHash);

    @Query("select count(distinct t.userId) from ThreatIndicator t where t.numberHash = :h")
    long countDistinctReporters(@Param("h") String hash);

    @Query("select coalesce(avg(t.riskScore), 0) from ThreatIndicator t where t.numberHash = :h")
    double avgRisk(@Param("h") String hash);

    @Query("select distinct t.numberHash from ThreatIndicator t where t.userId = :uid")
    List<String> hashesByUser(@Param("uid") Long userId);

    void deleteByUserId(Long userId);

    @Query("select t.country, count(t) from ThreatIndicator t group by t.country order by count(t) desc")
    List<Object[]> countByCountry();

    @Query("select t.type, count(t) from ThreatIndicator t group by t.type")
    List<Object[]> countByType();

    List<ThreatIndicator> findTop20ByOrderByCreatedAtDesc();
}
