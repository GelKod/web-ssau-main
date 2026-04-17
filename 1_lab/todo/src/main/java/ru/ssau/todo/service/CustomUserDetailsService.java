package ru.ssau.todo.service;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ssau.todo.dto.UserDto;
import ru.ssau.todo.entity.Role;
import ru.ssau.todo.entity.User;
import ru.ssau.todo.repository.RoleRepository;
import ru.ssau.todo.repository.UserRepository;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_USER = "ROLE_USER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public CustomUserDetailsService(UserRepository userRepository,
                                    RoleRepository roleRepository,
                                    PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        Set<GrantedAuthority> authorities = user.getRoles()
                .stream()
                .map(Role::getName)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(authorities)
                .build();
    }

    @Transactional
    public UserDto register(UserDto userDto) {
        if (userDto.getUsername() == null || userDto.getUsername().isBlank()) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        if (userDto.getPassword() == null || userDto.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password must not be blank");
        }
        if (userRepository.findByUsername(userDto.getUsername()).isPresent()) {
            throw new IllegalArgumentException("User with username already exists");
        }

        String roleName = "admin".equalsIgnoreCase(userDto.getUsername()) ? ROLE_ADMIN : ROLE_USER;
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not found: " + roleName));

        User user = new User();
        user.setUsername(userDto.getUsername());
        user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        user.getRoles().add(role);

        User savedUser = userRepository.save(user);
        return new UserDto(savedUser.getId(), savedUser.getUsername(), null);
    }
}
