package com.kiwih.screentime.repo;

import com.kiwih.screentime.domain.ChecklistItemRow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItemRow, Long> {

    /** Every item a parent has not removed, in the parents' order. */
    List<ChecklistItemRow> findByActiveTrueOrderBySortOrderAscIdAsc();
}
