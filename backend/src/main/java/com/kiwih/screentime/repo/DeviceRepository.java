package com.kiwih.screentime.repo;

import com.kiwih.screentime.domain.Device;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    List<Device> findAllByOrderBySortOrderAscNameAsc();

    List<Device> findAllByActiveTrueOrderBySortOrderAscNameAsc();
}
