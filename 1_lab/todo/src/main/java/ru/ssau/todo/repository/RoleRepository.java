package ru.ssau.todo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.ssau.todo.entity.Role;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);
}
