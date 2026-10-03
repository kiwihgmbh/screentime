package com.kiwih.screentime.repo;

import com.kiwih.screentime.domain.Role;
import com.kiwih.screentime.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByRole(Role role);

    List<User> findAllByOrderByUsernameAsc();
}
