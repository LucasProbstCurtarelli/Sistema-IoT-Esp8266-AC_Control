package com.iot.app.repository;

import com.iot.app.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Este método é mágico: o Spring cria o SQL automaticamente
    // SELECT * FROM user WHERE username = ?
    Optional<User> findByUsername(String username);
    
}