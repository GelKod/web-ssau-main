package ru.ssau.todo.repository;

import java.time.LocalDateTime;
import java.util.*;

import org.springframework.stereotype.Repository;

import ru.ssau.todo.entity.Task;

@Repository
public class TaskInMemoryRepository implements TaskRepository {

    private long _idGen = 1; 

    private Map<Long, Task> tasks = new HashMap<>();

    public Task create(Task task){
        try{
            task.setId(_idGen);
            tasks.put(_idGen,task);
            _idGen++;
        }
        catch(IllegalArgumentException e){
            throw new IllegalArgumentException("Передана пустая задача", e);
        }
        return task;
    }

    public Optional<Task> findById(long id){
        return Optional.ofNullable(tasks.get(id));
    }

    public List<Task> findAll(LocalDateTime from, LocalDateTime to, long userId){
        //userId? Where i can use it
        List<Task> tmp = new LinkedList<Task>();
        for (long i = 1; i<tasks.size();i++){
            if(from.isBefore(tasks.get(i).getCreatedAt())&&to.isAfter(tasks.get(i).getCreatedAt())){
                tmp.add(tasks.get(i));
            }
        }
        return tmp;
    }

    public void update(Task task) throws Exception{
        tasks.replace(task.getId(), task);
    }

    public void deleteById(long id){
        tasks.remove(id);
    }

    public long countActiveTasksByUserId(long userId){
        return -1; //Where used userId? I cant find
    }
}
