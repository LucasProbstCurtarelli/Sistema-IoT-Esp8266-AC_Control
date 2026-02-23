package com.iot.app.repository;

import com.iot.app.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    
    // This method is magic: Spring automatically creates the SQL
    // SELECT * FROM user WHERE username = ?
    Optional<User> findByUsername(String username);
    
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.password = :password WHERE u.username = :username")
    int updatePasswordByUsername(@Param("username") String username, @Param("password") String password);
    
}