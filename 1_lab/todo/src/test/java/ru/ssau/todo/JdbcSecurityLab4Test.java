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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JdbcSecurityLab4Test {

    private static final String TEST_USER_PREFIX = "it-jdbc-lab4-";
    private static final String USER_PASSWORD = "password123";
    private static final String ADMIN_PASSWORD = "admin123";

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterEach
    void tearDown() {
        //jdbcTemplate.update("DELETE FROM task WHERE created_by IN (SELECT id FROM \"user\" WHERE username LIKE ?)", TEST_USER_PREFIX + "%");
        //jdbcTemplate.update("DELETE FROM user_role WHERE user_id IN (SELECT id FROM \"user\" WHERE username LIKE ?)", TEST_USER_PREFIX + "%");
        //jdbcTemplate.update("DELETE FROM \"user\" WHERE username LIKE ?", TEST_USER_PREFIX + "%");
    }

    @Test
    void registerUserCreatesUserWithEncodedPasswordAndUserRole() {
        String username = uniqueUsername("register-user");
        String rawPassword = "raw-secret";

        Response response = RestAssured.given()
                .baseUri("http://localhost")
                .port(port)
                .basePath("/users/register")
                .contentType(JSON)
                .body(userPayload(username, rawPassword))
                .when()
                .post();

        response.then().statusCode(201);

        User saved = userRepository.findByUsername(username).orElseThrow();

        assertAll(
                () -> assertNotNull(response.jsonPath().getLong("id")),
                () -> assertEquals(username, response.jsonPath().getString("username")),
                () -> assertNull(response.jsonPath().get("password")),
                () -> assertNotNull(saved.getPassword()),
                () -> assertTrue(passwordEncoder.matches(rawPassword, saved.getPassword())),
                () -> assertFalse(rawPassword.equals(saved.getPassword())),
                () -> assertEquals(Set.of("ROLE_USER"), saved.getRoles().stream().map(Role::getName).collect(java.util.stream.Collectors.toSet()))
        );
    }

    @Test
    void registerAdminAssignsAdminRole() {
        String normalizedAdmin = "admin";
        userRepository.findByUsername(normalizedAdmin).ifPresent(existingAdmin -> {
            jdbcTemplate.update("DELETE FROM user_role WHERE user_id = ?", existingAdmin.getId());
            jdbcTemplate.update("DELETE FROM \"user\" WHERE id = ?", existingAdmin.getId());
        });

        RestAssured.given()
                .baseUri("http://localhost")
                .port(port)
                .basePath("/users/register")
                .contentType(JSON)
                .body(userPayload(normalizedAdmin, ADMIN_PASSWORD))
                .when()
                .post()
                .then()
                .statusCode(201);

        User admin = userRepository.findByUsername(normalizedAdmin).orElseThrow();
        assertEquals(Set.of("ROLE_ADMIN"), admin.getRoles().stream().map(Role::getName).collect(java.util.stream.Collectors.toSet()));

        // clean fixed username explicitly, because this one is out of TEST_USER_PREFIX.
        jdbcTemplate.update("DELETE FROM user_role WHERE user_id = ?", admin.getId());
        jdbcTemplate.update("DELETE FROM \"user\" WHERE id = ?", admin.getId());
    }

    @Test
    void protectedEndpointReturns401WithoutAuthentication() {
        RestAssured.given()
                .baseUri("http://localhost")
                .port(port)
                .basePath("/tasks")
                .queryParam("userId", 1)
                .when()
                .get()
                .then()
                .statusCode(401);
    }

    @Test
    void deleteTaskReturns403ForRegularUser() {
        User owner = createUser("owner", false);
        Task task = createTask(owner, "Protected", TaskStatus.OPEN, LocalDateTime.now().minusMinutes(10));

        tasksRequest(owner.getUsername(), USER_PASSWORD)
                .when()
                .delete("/{id}", task.getId())
                .then()
                .statusCode(403);
    }

    @Test
    void deleteTaskAllowedForAdmin() {
        User owner = createUser("delete-owner", false);
        User admin = createUser("delete-admin", true);
        Task task = createTask(owner, "Old task", TaskStatus.OPEN, LocalDateTime.now().minusMinutes(6));

        tasksRequest(admin.getUsername(), ADMIN_PASSWORD)
                .when()
                .delete("/{id}", task.getId())
                .then()
                .statusCode(204);
    }

    @Test
    void createTaskIgnoresCreatedByFromBodyAndUsesAuthenticatedUser() {
        User authenticatedUser = createUser("creator", false);
        User anotherUser = createUser("another", false);

        Response response = tasksRequest(authenticatedUser.getUsername(), USER_PASSWORD)
                .contentType(JSON)
                .body(taskPayload("Task from auth user", anotherUser.getId(), "OPEN"))
                .when()
                .post();

        response.then().statusCode(201);
        assertEquals(authenticatedUser.getId().intValue(), response.jsonPath().getInt("createdBy"));
    }

    @Test
    void formLoginCreatesSessionAndAllowsAuthorizedRequest() {
        User user = createUser("form-user", false);
        createTask(user, "Task for session auth", TaskStatus.OPEN, LocalDateTime.now().minusMinutes(15));

        Response loginResponse = RestAssured.given()
                .baseUri("http://localhost")
                .port(port)
                .contentType("application/x-www-form-urlencoded")
                .formParam("username", user.getUsername())
                .formParam("password", USER_PASSWORD)
                .redirects().follow(false)
                .when()
                .post("/login");

        loginResponse.then().statusCode(302);
        String sessionId = loginResponse.getCookie("JSESSIONID");
        assertNotNull(sessionId);

        RestAssured.given()
                .baseUri("http://localhost")
                .port(port)
                .basePath("/tasks")
                .cookie("JSESSIONID", sessionId)
                .queryParam("userId", user.getId())
                .when()
                .get()
                .then()
                .statusCode(200);
    }

    @Test
    void createTaskReturns201AndGeneratedFields() {
        User user = createUser("create-contract", false);

        Response response = tasksRequest(user.getUsername(), USER_PASSWORD)
                .contentType(JSON)
                .body("""
                        {
                          "id": 999999,
                          "title": "Contract create",
                          "status": "DONE",
                          "createdBy": 123456
                        }
                        """)
                .when()
                .post();

        response.then().statusCode(201);
        assertAll(
                () -> assertNotNull(response.jsonPath().getLong("id")),
                () -> assertEquals("Contract create", response.jsonPath().getString("title")),
                () -> assertEquals("DONE", response.jsonPath().getString("status")),
                () -> assertEquals(user.getId().intValue(), response.jsonPath().getInt("createdBy")),
                () -> assertNotNull(response.jsonPath().getString("createdAt"))
        );
    }

    @Test
    void createTaskWithoutStatusUsesOpenByDefault() {
        User user = createUser("default-status", false);

        Response response = tasksRequest(user.getUsername(), USER_PASSWORD)
                .contentType(JSON)
                .body("""
                        {
                          "title": "Default status task"
                        }
                        """)
                .when()
                .post();

        response.then().statusCode(201);
        assertEquals("OPEN", response.jsonPath().getString("status"));
    }

    @Test
    void getTaskByIdReturnsDtoAnd404ForMissing() {
        User user = createUser("find-by-id", false);
        Task task = createTask(user, "Find me", TaskStatus.OPEN, LocalDateTime.now().minusMinutes(10));

        Response found = tasksRequest(user.getUsername(), USER_PASSWORD)
                .when()
                .get("/{id}", task.getId());

        found.then().statusCode(200);
        Map<String, Object> body = found.jsonPath().getMap("$");

        assertAll(
                () -> assertEquals(Set.of("id", "title", "status", "createdBy", "createdAt"), body.keySet()),
                () -> assertEquals(task.getId().intValue(), found.jsonPath().getInt("id")),
                () -> assertEquals("Find me", found.jsonPath().getString("title"))
        );

        tasksRequest(user.getUsername(), USER_PASSWORD)
                .when()
                .get("/{id}", Long.MAX_VALUE)
                .then()
                .statusCode(404);
    }

    @Test
    void listTasksAppliesDateAndUserFilters() {
        User firstUser = createUser("filter-a", false);
        User secondUser = createUser("filter-b", false);
        LocalDateTime baseTime = LocalDateTime.now().minusHours(2);

        Task beforeWindow = createTask(firstUser, "Before window", TaskStatus.OPEN, baseTime);
        Task inWindow = createTask(firstUser, "In window", TaskStatus.IN_PROGRESS, baseTime.plusMinutes(10));
        Task afterWindow = createTask(firstUser, "After window", TaskStatus.DONE, baseTime.plusMinutes(20));
        createTask(secondUser, "Another user", TaskStatus.OPEN, baseTime.plusMinutes(10));

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
                () -> assertTrue(ids.contains(inWindow.getId().intValue())),
                () -> assertTrue(titles.contains("In window")),
                () -> assertFalse(ids.contains(beforeWindow.getId().intValue())),
                () -> assertFalse(ids.contains(afterWindow.getId().intValue()))
        );
    }

    @Test
    void updateTaskUsesPathIdAndSupports404() {
        User user = createUser("update", false);
        Task task = createTask(user, "Before update", TaskStatus.OPEN, LocalDateTime.now().minusMinutes(15));

        tasksRequest(user.getUsername(), USER_PASSWORD)
                .contentType(JSON)
                .body("""
                        {
                          "id": 987654,
                          "title": "After update",
                          "status": "DONE",
                          "createdBy": 111111
                        }
                        """)
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

        tasksRequest(user.getUsername(), USER_PASSWORD)
                .contentType(JSON)
                .body(taskPayload("No task", user.getId(), "OPEN"))
                .when()
                .put("/{id}", Long.MAX_VALUE)
                .then()
                .statusCode(404);
    }

    @Test
    void deleteBusinessRulesAndStatuses() {
        User owner = createUser("delete-rules-owner", false);
        User admin = createUser("delete-rules-admin", true);

        Task fresh = createTask(owner, "Fresh task", TaskStatus.OPEN, LocalDateTime.now().minusMinutes(1));
        Task old = createTask(owner, "Old task", TaskStatus.OPEN, LocalDateTime.now().minusMinutes(6));

        tasksRequest(admin.getUsername(), ADMIN_PASSWORD)
                .when()
                .delete("/{id}", fresh.getId())
                .then()
                .statusCode(400);

        tasksRequest(admin.getUsername(), ADMIN_PASSWORD)
                .when()
                .delete("/{id}", old.getId())
                .then()
                .statusCode(204);

        tasksRequest(admin.getUsername(), ADMIN_PASSWORD)
                .when()
                .delete("/{id}", Long.MAX_VALUE)
                .then()
                .statusCode(204);
    }

    @Test
    void activeCountAndLimitsWorkForCreateAndUpdate() {
        User user = createUser("limits", false);

        for (int i = 0; i < 8; i++) {
            createTask(user, "Active " + i, TaskStatus.OPEN, LocalDateTime.now().minusHours(1).plusMinutes(i));
        }
        Task inactive1 = createTask(user, "Inactive 1", TaskStatus.DONE, LocalDateTime.now().minusMinutes(30));
        Task inactive2 = createTask(user, "Inactive 2", TaskStatus.DONE, LocalDateTime.now().minusMinutes(25));
        Task inactive3 = createTask(user, "Inactive 3", TaskStatus.DONE, LocalDateTime.now().minusMinutes(20));

        Response countResponse = tasksRequest(user.getUsername(), USER_PASSWORD)
                .queryParam("userId", user.getId())
                .when()
                .get("/active/count");
        assertEquals(8, countResponse.as(Integer.class));

        tasksRequest(user.getUsername(), USER_PASSWORD)
                .contentType(JSON)
                .body(taskPayload("Inactive 1 to open", user.getId(), "OPEN"))
                .when()
                .put("/{id}", inactive1.getId())
                .then()
                .statusCode(200);

        tasksRequest(user.getUsername(), USER_PASSWORD)
                .contentType(JSON)
                .body(taskPayload("Inactive 2 to in progress", user.getId(), "IN_PROGRESS"))
                .when()
                .put("/{id}", inactive2.getId())
                .then()
                .statusCode(200);

        countResponse = tasksRequest(user.getUsername(), USER_PASSWORD)
                .queryParam("userId", user.getId())
                .when()
                .get("/active/count");
        assertEquals(10, countResponse.as(Integer.class));

        tasksRequest(user.getUsername(), USER_PASSWORD)
                .contentType(JSON)
                .body(taskPayload("Inactive 3 to open", user.getId(), "OPEN"))
                .when()
                .put("/{id}", inactive3.getId())
                .then()
                .statusCode(400);

        tasksRequest(user.getUsername(), USER_PASSWORD)
                .contentType(JSON)
                .body(taskPayload("Eleventh task", user.getId(), "OPEN"))
                .when()
                .post()
                .then()
                .statusCode(400);
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

    private Task createTask(User user, String title, TaskStatus status, LocalDateTime createdAt) {
        Task task = new Task(title, user, status);
        task.setCreatedAt(createdAt);
        return taskRepository.saveAndFlush(task);
    }

    private String uniqueUsername(String suffix) {
        return TEST_USER_PREFIX + suffix + "-" + UUID.randomUUID();
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
