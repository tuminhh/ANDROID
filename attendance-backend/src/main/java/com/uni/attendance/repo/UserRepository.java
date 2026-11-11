package com.uni.attendance.repo;

import com.uni.attendance.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {
}
