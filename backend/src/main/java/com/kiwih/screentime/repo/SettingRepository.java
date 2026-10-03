package com.kiwih.screentime.repo;

import com.kiwih.screentime.domain.Setting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepository extends JpaRepository<Setting, String> {
}
