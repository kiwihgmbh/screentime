package com.kiwih.screentime.repo;

import com.kiwih.screentime.domain.ChecklistTick;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ChecklistTickRepository extends JpaRepository<ChecklistTick, Long> {

    List<ChecklistTick> findBySessionIdInOrderByIdAsc(Collection<Long> sessionIds);
}
