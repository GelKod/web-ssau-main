package ru.ssau.todo.entity;

import java.time.LocalDateTime;

public class Task {
    long id;
    String title;
    TaskStatus status;
    long createdBy;
    LocalDateTime createdAt;

    public long getId(){
        return id;
    }
    public void setId(long id){
        this.id=id;
    }
    public LocalDateTime getCreatedAt(){
        return createdAt;
    }
}

enum TaskStatus{
    OPEN,
    DONE,
    IN_PROGRESS,
    CLOSED
}
