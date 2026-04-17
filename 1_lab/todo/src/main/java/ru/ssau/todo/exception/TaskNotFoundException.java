package ru.ssau.todo.exception;

public class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException(long taskId) {
        super("Task with id " + taskId + " not found");
    }
}
