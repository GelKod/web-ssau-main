package ru.ssau.todo.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.ssau.todo.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
}
