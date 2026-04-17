package ru.ssau.todo;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import io.restassured.module.mockmvc.response.MockMvcResponse;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import ru.ssau.todo.entity.Task;
import ru.ssau.todo.entity.TaskStatus;
import ru.ssau.todo.entity.User;
import ru.ssau.todo.repository.TaskRepository;
import ru.ssau.todo.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class JdbcTaskTest {

    private static final String TEST_USER_PREFIX = "it-jdbc-task-";

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WebApplicationContext context;

    @BeforeEach
    void setUp() {
        ensureSchema();
        cleanupTestData();
        RestAssuredMockMvc.webAppContextSetup(context);
        RestAssuredMockMvc.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterEach
    void tearDown() {
        RestAssuredMockMvc.reset();
        cleanupTestData();
    }

    @Test
    void createTaskReturnsCreatedTaskDto() {
        User user = createUser("create");

        MockMvcResponse response = RestAssuredMockMvc.given()
                .contentType(JSON)
                .body(taskPayload("Create via API", user.getId(), "IN_PROGRESS"))
                .when()
                .post("/tasks");

        response.then().statusCode(201);

        Long taskId = response.jsonPath().getLong("id");
        assertAll(
                () -> assertNotNull(taskId),
                () -> assertEquals("Create via API", response.jsonPath().getString("title")),
                () -> assertEquals("IN_PROGRESS", response.jsonPath().getString("status")),
                () -> assertEquals(user.getId().intValue(), response.jsonPath().getInt("createdBy")),
                () -> assertNotNull(response.jsonPath().getString("createdAt"))
        );
    }

    @Test
    void createTaskUsesOpenStatusWhenStatusIsMissing() {
        User user = createUser("default-status");

        MockMvcResponse response = RestAssuredMockMvc.given()
                .contentType(JSON)
                .body("""
                        {
                          "title": "Task with default status",
                          "createdBy": %d
                        }
                        """.formatted(user.getId()))
                .when()
                .post("/tasks");

        response.then().statusCode(201);
        assertEquals("OPEN", response.jsonPath().getString("status"));
    }

    @Test
    void createTaskFailsForUnknownUser() {
        assertThrows(ServletException.class, () -> RestAssuredMockMvc.given()
                .contentType(JSON)
                .body(taskPayload("Unknown user task", Long.MAX_VALUE, "OPEN"))
                .when()
                .post("/tasks"));
    }

    @Test
    void findByIdReturnsTaskDtoWithoutInternalFields() {
        User user = createUser("dto");
        Task task = createTask(user, "DTO task", TaskStatus.OPEN, LocalDateTime.now().minusMinutes(10));

        MockMvcResponse response = RestAssuredMockMvc.given()
                .when()
                .get("/tasks/{id}", task.getId());

        response.then().statusCode(200);

        Map<String, Object> body = response.jsonPath().getMap("$");
        assertAll(
                () -> assertEquals(Set.of("id", "title", "status", "createdBy", "createdAt"), body.keySet()),
                () -> assertEquals(task.getId().intValue(), response.jsonPath().getInt("id")),
                () -> assertEquals("DTO task", response.jsonPath().getString("title")),
                () -> assertEquals("OPEN", response.jsonPath().getString("status")),
                () -> assertEquals(user.getId().intValue(), response.jsonPath().getInt("createdBy"))
        );
    }

    @Test
    void findByIdReturns404ForMissingTask() {
        RestAssuredMockMvc.given()
                .when()
                .get("/tasks/{id}", Long.MAX_VALUE)
                .then()
                .statusCode(404);
    }

    @Test
    void findAllAppliesUserAndDateFilters() {
        User firstUser = createUser("filter-a");
        User secondUser = createUser("filter-b");
        LocalDateTime baseTime = LocalDateTime.now().minusHours(2);

        Task earlyTask = createTask(firstUser, "Early task", TaskStatus.OPEN, baseTime);
        Task middleTask = createTask(firstUser, "Middle task", TaskStatus.DONE, baseTime.plusMinutes(10));
        Task lateTask = createTask(firstUser, "Late task", TaskStatus.IN_PROGRESS, baseTime.plusMinutes(20));
        createTask(secondUser, "Other user task", TaskStatus.OPEN, baseTime.plusMinutes(15));

        MockMvcResponse response = RestAssuredMockMvc.given()
                .queryParam("userId", firstUser.getId())
                .queryParam("from", baseTime.plusMinutes(5).toString())
                .queryParam("to", baseTime.plusMinutes(15).toString())
                .when()
                .get("/tasks");

        response.then().statusCode(200);

        List<Integer> ids = response.jsonPath().getList("id");
        List<String> titles = response.jsonPath().getList("title");

        assertAll(
                () -> assertEquals(1, ids.size()),
                () -> assertTrue(ids.contains(middleTask.getId().intValue())),
                () -> assertTrue(titles.contains("Middle task")),
                () -> assertFalse(ids.contains(earlyTask.getId().intValue())),
                () -> assertFalse(ids.contains(lateTask.getId().intValue()))
        );
    }

    @Test
    void countActiveTasksCountsOnlyOpenAndInProgressStatuses() {
        User user = createUser("count");
        createTask(user, "Open task", TaskStatus.OPEN, LocalDateTime.now().minusHours(1));
        createTask(user, "In progress task", TaskStatus.IN_PROGRESS, LocalDateTime.now().minusMinutes(50));
        createTask(user, "Done task", TaskStatus.DONE, LocalDateTime.now().minusMinutes(40));
        createTask(user, "Closed task", TaskStatus.CLOSED, LocalDateTime.now().minusMinutes(30));

        MockMvcResponse response = RestAssuredMockMvc.given()
                .queryParam("userId", user.getId())
                .when()
                .get("/tasks/active/count");

        response.then().statusCode(200);
        assertEquals("2", response.getBody().asString());
    }

    @Test
    void createTaskRejectsEleventhActiveTask() {
        User user = createUser("limit");
        for (int i = 0; i < 10; i++) {
            createTask(user, "Active task " + i, TaskStatus.OPEN, LocalDateTime.now().minusHours(1).plusMinutes(i));
        }

        assertThrows(ServletException.class, () -> RestAssuredMockMvc.given()
                .contentType(JSON)
                .body(taskPayload("Active task 11", user.getId(), "OPEN"))
                .when()
                .post("/tasks"));
    }

    @Test
    void deleteTaskRejectsFreshTask() {
        User user = createUser("fresh-delete");
        Task task = createTask(user, "Fresh task", TaskStatus.OPEN, LocalDateTime.now().minusMinutes(1));

        assertThrows(ServletException.class, () -> RestAssuredMockMvc.given()
                .when()
                .delete("/tasks/{id}", task.getId()));

        assertTrue(taskRepository.findById(task.getId()).isPresent());
    }

    @Test
    void deleteTaskRemovesTaskOlderThanFiveMinutes() {
        User user = createUser("old-delete");
        Task task = createTask(user, "Old task", TaskStatus.OPEN, LocalDateTime.now().minusMinutes(6));

        RestAssuredMockMvc.given()
                .when()
                .delete("/tasks/{id}", task.getId())
                .then()
                .statusCode(204);

        assertTrue(taskRepository.findById(task.getId()).isEmpty());
    }

    @Test
    void deleteTaskReturns204ForMissingTask() {
        RestAssuredMockMvc.given()
                .when()
                .delete("/tasks/{id}", Long.MAX_VALUE)
                .then()
                .statusCode(204);
    }

    @Test
    void updateTaskReturns404ForMissingTask() {
        User user = createUser("missing-update");

        RestAssuredMockMvc.given()
                .contentType(JSON)
                .body(taskPayload("Missing task", user.getId(), "DONE"))
                .when()
                .put("/tasks/{id}", Long.MAX_VALUE)
                .then()
                .statusCode(404);
    }

    @Test
    void updateTaskChangesTitleAndStatusOfExistingTask() {
        User user = createUser("update");
        Task task = createTask(user, "Before update", TaskStatus.OPEN, LocalDateTime.now().minusMinutes(20));

        RestAssuredMockMvc.given()
                .contentType(JSON)
                .body(taskPayload("After update", user.getId(), "DONE"))
                .when()
                .put("/tasks/{id}", task.getId())
                .then()
                .statusCode(200);

        MockMvcResponse response = RestAssuredMockMvc.given()
                .when()
                .get("/tasks/{id}", task.getId());

        response.then().statusCode(200);
        assertAll(
                () -> assertEquals("After update", response.jsonPath().getString("title")),
                () -> assertEquals("DONE", response.jsonPath().getString("status")),
                () -> assertEquals(user.getId().intValue(), response.jsonPath().getInt("createdBy"))
        );
    }

    @Test
    void updateTaskShouldNotCreateDuplicateRecord() {
        User user = createUser("update-duplicate");
        Task task = createTask(user, "Single task", TaskStatus.OPEN, LocalDateTime.now().minusMinutes(30));

        RestAssuredMockMvc.given()
                .contentType(JSON)
                .body(taskPayload("Single task updated", user.getId(), "CLOSED"))
                .when()
                .put("/tasks/{id}", task.getId())
                .then()
                .statusCode(200);

        List<Task> tasks = taskRepository.findAll(user.getId(), LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
        assertEquals(1, tasks.size(), "PUT should update an existing task instead of inserting a new row");
    }

    private User createUser(String suffix) {
        User user = new User(TEST_USER_PREFIX + suffix + "-" + UUID.randomUUID());
        return userRepository.saveAndFlush(user);
    }

    private Task createTask(User user, String title, TaskStatus status, LocalDateTime createdAt) {
        Task task = new Task(title, user, status);
        task.setCreatedAt(createdAt);
        return taskRepository.saveAndFlush(task);
    }

    private String taskPayload(String title, Long createdBy, String status) {
        return """
                {
                  "title": "%s",
                  "createdBy": %d,
                  "status": "%s"
                }
                """.formatted(title, createdBy, status);
    }

    private void cleanupTestData() {
        List<Long> userIds = jdbcTemplate.queryForList(
                "select id from \"user\" where username like ?",
                Long.class,
                TEST_USER_PREFIX + "%"
        );

        if (userIds.isEmpty()) {
            return;
        }

        String joinedIds = userIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        jdbcTemplate.execute("delete from task where created_by in (" + joinedIds + ")");
        jdbcTemplate.execute("delete from user_role where user_id in (" + joinedIds + ")");
        jdbcTemplate.execute("delete from \"user\" where id in (" + joinedIds + ")");
    }

    private void ensureSchema() {
        jdbcTemplate.execute("""
                create table if not exists "user" (
                    id bigserial primary key,
                    username varchar(100) not null unique
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists role (
                    id bigserial primary key,
                    name varchar(50) not null unique
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists user_role (
                    user_id bigint not null,
                    role_id bigint not null,
                    primary key (user_id, role_id)
                )
                """);
    }
}
