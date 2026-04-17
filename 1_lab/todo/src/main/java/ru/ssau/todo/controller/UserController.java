package ru.ssau.todo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ssau.todo.dto.UserDto;
import ru.ssau.todo.service.CustomUserDetailsService;

import java.net.URI;

@RestController
@RequestMapping("/users")
public class UserController {

    private final CustomUserDetailsService customUserDetailsService;

    public UserController(CustomUserDetailsService customUserDetailsService) {
        this.customUserDetailsService = customUserDetailsService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserDto> register(@RequestBody UserDto userDto) {
        UserDto savedUser = customUserDetailsService.register(userDto);
        return ResponseEntity.created(URI.create("/users/" + savedUser.getId())).body(savedUser);
    }
}
