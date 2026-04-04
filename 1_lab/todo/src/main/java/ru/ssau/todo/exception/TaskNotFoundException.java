package ru.ssau.todo.exception;

public class TaskNotFoundException extends Exception {
    public TaskNotFoundException(long taskId) {
        super("Task with id " + taskId + " not found");
    }
}