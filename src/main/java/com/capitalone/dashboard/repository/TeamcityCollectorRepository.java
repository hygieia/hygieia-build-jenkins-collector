package com.capitalone.dashboard.repository;

import com.capitalone.dashboard.model.TeamcityCollector;
import org.springframework.stereotype.Repository;

/**
 * Collector repository for the tcCollector subclass
 */
@Repository
public interface TeamcityCollectorRepository extends BaseCollectorRepository<TeamcityCollector> {
}
