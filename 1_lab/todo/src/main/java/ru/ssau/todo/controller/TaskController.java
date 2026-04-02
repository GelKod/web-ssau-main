
package ru.ssau.todo.controller;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ru.ssau.todo.entity.Task;
import ru.ssau.todo.service.TaskService;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public ResponseEntity<List<Task>> findAll(@RequestParam long userId,
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to) {
        if (from == null) {
            from = LocalDateTime.of(2000, 1, 1, 0, 0);
        }
        if (to == null) {
            to = LocalDateTime.of(2100, 12, 31, 23, 59, 59);
        }
        return ResponseEntity.ok(taskService.findAll(from, to, userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Task> findById(@PathVariable Long id) {
        Optional<Task> task = taskService.findById(id);
        if (task.isPresent()) {
            return ResponseEntity.ok().body(task.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<Task> createTask(@RequestBody Task task) {
        Task taskTmp = taskService.create(task);
        return ResponseEntity.created(URI.create("tasks/" + taskTmp.getId())).body(taskTmp);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateTask(@PathVariable long id, @RequestBody Task task) {
        task.setId(id);
        try {
            taskService.updateTask(task);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable long id) {
        taskService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/active/count")
    public ResponseEntity<Long> countTasks(@RequestParam long userId) {
        long count = taskService.countActiveTasksByUserId(userId);
        return ResponseEntity.ok().body(count);
    }
}
