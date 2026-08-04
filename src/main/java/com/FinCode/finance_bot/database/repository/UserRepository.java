package com.FinCode.finance_bot.database.repository;

import com.FinCode.finance_bot.database.entity.UserApp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserApp, Long> {
}
