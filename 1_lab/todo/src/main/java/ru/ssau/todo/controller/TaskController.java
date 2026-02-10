
package ru.ssau.todo.controller;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ru.ssau.todo.entity.Task;
import ru.ssau.todo.repository.TaskRepository;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    private TaskRepository taskRepository;

    public TaskController(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @GetMapping
    public ResponseEntity<List<Task>> findAll(@RequestParam long userId,
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to) {
        if (from == null) {
            from = LocalDateTime.MIN;
        }
        if (to == null) {
            to = LocalDateTime.MAX;
        }
        return ResponseEntity.ok(taskRepository.findAll(from, to, userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Task> findById(@PathVariable Long id) {
        Optional<Task> task = taskRepository.findById(id);
        if (task.isPresent()) {
            return ResponseEntity.ok().body(task.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<Task> createTask(@RequestBody Task task) {
        task = taskRepository.create(task);
        return ResponseEntity.created(URI.create("tasks/" + task.getId())).body(task);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateTask(@PathVariable long id, @RequestBody Task task) {
        task.setId(id);
        try {
            taskRepository.update(task);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable long id) {
        taskRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/active/count")
    public ResponseEntity<Long> countTasks(@RequestParam long userId) {
        long count = taskRepository.countActiveTasksByUserId(userId);
        return ResponseEntity.ok().body(count);
    }
}
