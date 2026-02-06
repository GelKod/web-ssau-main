
package ru.ssau.todo.tasks;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import ru.ssau.todo.entity.Task;
import ru.ssau.todo.repository.TaskRepository;

@RestController
@RequestMapping("/tasks")
public class StudentController {
    @Autowired
    private TaskRepository taskRepository;

    @GetMapping("/tasks?from={from}&to={to}&userId={userId}")
    public List<Task> findAll(@PathVariable LocalDateTime from, @PathVariable LocalDateTime to,
            @PathVariable long userId) {
        return findAll(from, to, userId);
    }

    @GetMapping("/tasks/{id}")
    public Optional<Task> findById(@PathVariable Long id) {
        return taskRepository.findById(id);
    }

    @GetMapping
    public List<Student> findAll() {
        return studentRepository.findAll();
    }

    @GetMapping("/{name}")
    public List<Student> findAllByName(@PathVariable String name) {
        return studentRepository.findAllByName(name);
    }
}
