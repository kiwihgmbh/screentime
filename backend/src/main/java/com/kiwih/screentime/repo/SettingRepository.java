package com.kiwih.screentime.repo;

import com.kiwih.screentime.domain.Setting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettingRepository extends JpaRepository<Setting, Long> {

    /** The whole history, oldest first. It is a few dozen rows a year. */
    List<Setting> findAllByOrderByValidFromAscIdAsc();
}
