package ru.ssau.todo;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.ssau.todo.entity.Role;
import ru.ssau.todo.entity.Task;
import ru.ssau.todo.entity.TaskStatus;
import ru.ssau.todo.entity.User;
import ru.ssau.todo.repository.RoleRepository;
import ru.ssau.todo.repository.TaskRepository;
import ru.ssau.todo.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

//3lab tests

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JdbcTaskTest {

    private static final String TEST_USER_PREFIX = "it-jdbc-task-";
    private static final String USER_PASSWORD = "password123";
    private static final String ADMIN_PASSWORD = "admin123";

    @LocalServerPort
    private int port;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM task WHERE created_by IN (SELECT id FROM \"user\" WHERE username LIKE ?)", TEST_USER_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM user_role WHERE user_id IN (SELECT id FROM \"user\" WHERE username LIKE ?)", TEST_USER_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM \"user\" WHERE username LIKE ?", TEST_USER_PREFIX + "%");
    }

    @Test
    void registerUserReturnsCreatedUserDto() {
        Response response = RestAssured.given()
                .baseUri("http://localhost")
                .port(port)
                .basePath("/users/register")
                .contentType(JSON)
                .body(userPayload(uniqueUsername("register"), USER_PASSWORD))
                .when()
                .post();

        response.then().statusCode(201);

        assertAll(
                () -> assertNotNull(response.jsonPath().getLong("id")),
                () -> assertNotNull(response.jsonPath().getString("username")),
                () -> assertNull(response.jsonPath().get("password"))
        );
    }

    @Test
    void createTaskUsesAuthenticatedUserInsteadOfRequestBodyCreatedBy() {
        User user = createUser("creator", false);
        User anotherUser = createUser("another", false);

        Response response = tasksRequest(user.getUsername(), USER_PASSWORD)
                .contentType(JSON)
                .body(taskPayload("Create via API", anotherUser.getId(), "IN_PROGRESS"))
                .when()
                .post();

        response.then().statusCode(201);

        assertAll(
                () -> assertEquals("Create via API", response.jsonPath().getString("title")),
                () -> assertEquals("IN_PROGRESS", response.jsonPath().getString("status")),
                () -> assertEquals(user.getId().intValue(), response.jsonPath().getInt("createdBy"))
        );
    }

    @Test
    void createTaskRequiresAuthentication() {
        RestAssured.given()
                .baseUri("http://localhost")
                .port(port)
                .basePath("/tasks")
                .contentType(JSON)
                .body("""
                        {
                          "title": "Unauthorized task"
                        }
                        """)
                .when()
                .post()
                .then()
                .statusCode(401);
    }

    @Test
    void createTaskUsesOpenStatusWhenStatusIsMissing() {
        User user = createUser("default-status", false);

        Response response = tasksRequest(user.getUsername(), USER_PASSWORD)
                .contentType(JSON)
                .body("""
                        {
                          "title": "Task with default status"
                        }
                        """)
                .when()
                .post();

        response.then().statusCode(201);
        assertEquals("OPEN", response.jsonPath().getString("status"));
    }

    @Test
    void findByIdReturnsTaskDtoWithoutInternalFields() {
        User user = createUser("dto", false);
        Task task = createTask(user, "DTO task", TaskStatus.OPEN, LocalDateTime.now().minusMinutes(10));

        Response response = tasksRequest(user.getUsername(), USER_PASSWORD)
                .when()
                .get("/{id}", task.getId());

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
        User user = createUser("missing-get", false);

        tasksRequest(user.getUsername(), USER_PASSWORD)
                .when()
                .get("/{id}", Long.MAX_VALUE)
                .then()
                .statusCode(404);
    }

    @Test
    void findAllAppliesUserAndDateFilters() {
        User firstUser = createUser("filter-a", false);
        User secondUser = createUser("filter-b", false);

        LocalDateTime baseTime = LocalDateTime.now().minusHours(2);
        Task earlyTask = createTask(firstUser, "Early task", TaskStatus.OPEN, baseTime);
        Task middleTask = createTask(firstUser, "Middle task", TaskStatus.DONE, baseTime.plusMinutes(10));
        Task lateTask = createTask(firstUser, "Late task", TaskStatus.IN_PROGRESS, baseTime.plusMinutes(20));
        createTask(secondUser, "Other user task", TaskStatus.OPEN, baseTime.plusMinutes(15));

        Response response = tasksRequest(firstUser.getUsername(), USER_PASSWORD)
                .queryParam("userId", firstUser.getId())
                .queryParam("from", baseTime.plusMinutes(5).toString())
                .queryParam("to", baseTime.plusMinutes(15).toString())
                .when()
                .get();

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
        User user = createUser("count", false);
        createTask(user, "Open task", TaskStatus.OPEN, LocalDateTime.now().minusHours(1));
        createTask(user, "In progress task", TaskStatus.IN_PROGRESS, LocalDateTime.now().minusMinutes(50));
        createTask(user, "Done task", TaskStatus.DONE, LocalDateTime.now().minusMinutes(40));
        createTask(user, "Closed task", TaskStatus.CLOSED, LocalDateTime.now().minusMinutes(30));

        Response response = tasksRequest(user.getUsername(), USER_PASSWORD)
                .queryParam("userId", user.getId().intValue())
                .when()
                .get("/active/count");

        response.then().statusCode(200);
        assertEquals(2, response.as(Integer.class));
    }

    @Test
    void createTaskRejectsEleventhActiveTask() {
        User user = createUser("limit", false);

        for (int i = 0; i < 10; i++) {
            createTask(user, "Active task " + i, TaskStatus.OPEN, LocalDateTime.now().minusHours(1).plusMinutes(i));
        }

        tasksRequest(user.getUsername(), USER_PASSWORD)
                .contentType(JSON)
                .body(taskPayload("Active task 11", user.getId(), "OPEN"))
                .when()
                .post()
                .then()
                .statusCode(400);
    }

    @Test
    void deleteTaskRejectsFreshTaskForAdmin() {
        User owner = createUser("fresh-delete-owner", false);
        User admin = createUser("fresh-delete-admin", true);
        Task task = createTask(owner, "Fresh task", TaskStatus.OPEN, LocalDateTime.now().minusMinutes(1));

        tasksRequest(admin.getUsername(), ADMIN_PASSWORD)
                .when()
                .delete("/{id}", task.getId())
                .then()
                .statusCode(400);

        assertTrue(taskRepository.findById(task.getId()).isPresent());
    }

    @Test
    void deleteTaskRequiresAdminRole() {
        User owner = createUser("delete-owner", false);
        Task task = createTask(owner, "Protected task", TaskStatus.OPEN, LocalDateTime.now().minusMinutes(10));

        tasksRequest(owner.getUsername(), USER_PASSWORD)
                .when()
                .delete("/{id}", task.getId())
                .then()
                .statusCode(403);
    }

    @Test
    void deleteTaskRemovesTaskOlderThanFiveMinutesForAdmin() {
        User owner = createUser("old-delete-owner", false);
        User admin = createUser("old-delete-admin", true);
        Task task = createTask(owner, "Old task", TaskStatus.OPEN, LocalDateTime.now().minusMinutes(6));

        tasksRequest(admin.getUsername(), ADMIN_PASSWORD)
                .when()
                .delete("/{id}", task.getId())
                .then()
                .statusCode(204);

        assertTrue(taskRepository.findById(task.getId()).isEmpty());
    }

    @Test
    void updateTaskReturns404ForMissingTask() {
        User user = createUser("missing-update", false);

        tasksRequest(user.getUsername(), USER_PASSWORD)
                .contentType(JSON)
                .body(taskPayload("Missing task", user.getId(), "DONE"))
                .when()
                .put("/{id}", Long.MAX_VALUE)
                .then()
                .statusCode(404);
    }

    @Test
    void updateTaskChangesTitleAndStatusOfExistingTask() {
        User user = createUser("update", false);
        Task task = createTask(user, "Before update", TaskStatus.OPEN, LocalDateTime.now().minusMinutes(20));

        tasksRequest(user.getUsername(), USER_PASSWORD)
                .contentType(JSON)
                .body(taskPayload("After update", user.getId(), "DONE"))
                .when()
                .put("/{id}", task.getId())
                .then()
                .statusCode(200);

        Response response = tasksRequest(user.getUsername(), USER_PASSWORD)
                .when()
                .get("/{id}", task.getId());

        response.then().statusCode(200);

        assertAll(
                () -> assertEquals("After update", response.jsonPath().getString("title")),
                () -> assertEquals("DONE", response.jsonPath().getString("status")),
                () -> assertEquals(user.getId().intValue(), response.jsonPath().getInt("createdBy"))
        );
    }

    @Test
    void updateTaskShouldNotCreateDuplicateRecord() {
        User user = createUser("update-duplicate", false);
        Task task = createTask(user, "Single task", TaskStatus.OPEN, LocalDateTime.now().minusMinutes(30));

        tasksRequest(user.getUsername(), USER_PASSWORD)
                .contentType(JSON)
                .body(taskPayload("Single task updated", user.getId(), "CLOSED"))
                .when()
                .put("/{id}", task.getId())
                .then()
                .statusCode(200);

        List<Task> tasks = taskRepository.findAll(user.getId(), LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
        assertEquals(1, tasks.size());
    }

    @Test
    void updateTaskToActiveStatusEnforcesActiveLimit() {
        User user = createUser("update-limit", false);

        for (int i = 0; i < 8; i++) {
            createTask(user, "Active " + i, TaskStatus.OPEN, LocalDateTime.now().minusHours(1).plusMinutes(i));
        }

        Task inactive1 = createTask(user, "Inactive 1", TaskStatus.DONE, LocalDateTime.now().minusMinutes(30));
        Task inactive2 = createTask(user, "Inactive 2", TaskStatus.DONE, LocalDateTime.now().minusMinutes(25));
        Task inactive3 = createTask(user, "Inactive 3", TaskStatus.DONE, LocalDateTime.now().minusMinutes(20));

        Response countResp = tasksRequest(user.getUsername(), USER_PASSWORD)
                .queryParam("userId", user.getId().intValue())
                .when()
                .get("/active/count");
        assertEquals(8, countResp.as(Integer.class));

        tasksRequest(user.getUsername(), USER_PASSWORD)
                .contentType(JSON)
                .body(taskPayload("Inactive 1 updated", user.getId(), "OPEN"))
                .when()
                .put("/{id}", inactive1.getId())
                .then()
                .statusCode(200);

        countResp = tasksRequest(user.getUsername(), USER_PASSWORD)
                .queryParam("userId", user.getId().intValue())
                .when()
                .get("/active/count");
        assertEquals(9, countResp.as(Integer.class));

        tasksRequest(user.getUsername(), USER_PASSWORD)
                .contentType(JSON)
                .body(taskPayload("Inactive 2 updated", user.getId(), "IN_PROGRESS"))
                .when()
                .put("/{id}", inactive2.getId())
                .then()
                .statusCode(200);

        countResp = tasksRequest(user.getUsername(), USER_PASSWORD)
                .queryParam("userId", user.getId().intValue())
                .when()
                .get("/active/count");
        assertEquals(10, countResp.as(Integer.class));

        tasksRequest(user.getUsername(), USER_PASSWORD)
                .contentType(JSON)
                .body(taskPayload("Inactive 3 updated", user.getId(), "OPEN"))
                .when()
                .put("/{id}", inactive3.getId())
                .then()
                .statusCode(400);

        countResp = tasksRequest(user.getUsername(), USER_PASSWORD)
                .queryParam("userId", user.getId().intValue())
                .when()
                .get("/active/count");
        assertEquals(10, countResp.as(Integer.class));
    }

    private User createUser(String suffix, boolean admin) {
        String roleName = admin ? "ROLE_ADMIN" : "ROLE_USER";
        String password = admin ? ADMIN_PASSWORD : USER_PASSWORD;
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not found: " + roleName));

        User user = new User();
        user.setUsername(uniqueUsername(suffix));
        user.setPassword(passwordEncoder.encode(password));
        user.getRoles().add(role);
        return userRepository.saveAndFlush(user);
    }

    private String uniqueUsername(String suffix) {
        return TEST_USER_PREFIX + suffix + "-" + UUID.randomUUID();
    }

    private Task createTask(User user, String title, TaskStatus status, LocalDateTime createdAt) {
        Task task = new Task(title, user, status);
        task.setCreatedAt(createdAt);
        return taskRepository.saveAndFlush(task);
    }

    private RequestSpecification tasksRequest(String username, String password) {
        return RestAssured.given()
                .baseUri("http://localhost")
                .port(port)
                .basePath("/tasks")
                .auth()
                .preemptive()
                .basic(username, password);
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

    private String userPayload(String username, String password) {
        return """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);
    }
}
