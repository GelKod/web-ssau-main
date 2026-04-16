package ru.ssau.todo.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.ssau.todo.entity.Role;

public interface RoleRepository extends JpaRepository<Role, Long> {
}
