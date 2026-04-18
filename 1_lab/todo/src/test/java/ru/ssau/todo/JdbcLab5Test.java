package ru.ssau.todo;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
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

import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Интеграционные тесты под ЛР5 (Angular + Auth API).
 *
 * Покрывает API-пункты из задания:
 * - /auth/login и /auth/me
 * - список задач текущего пользователя
 * - получение задачи по id (для редактирования)
 * - создание/редактирование задачи
 * - удаление только администратором
 * - CORS preflight (вариант с CORS вместо proxy)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcLab5Test {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String USERNAME = "GelKod";
    private static final String ADMIN_USERNAME = "GelKod_admin";
    private static final String PASSWORD = "GelKod";
    private static final String FRONTEND_ORIGIN = "http://localhost:4200";

    private User regularUser;
    private User adminUser;
    private Task oldTaskRegularOpen;
    private Task regularInProgressTask;
    private Task adminDoneTask;

    @BeforeEach
    void setUp() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        // Полная очистка данных (без удаления схемы), чтобы тесты были повторяемыми.
        jdbcTemplate.execute("TRUNCATE TABLE task RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE user_role RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE role RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE \"user\" RESTART IDENTITY CASCADE");

        Role adminRole = roleRepository.save(new Role("ROLE_ADMIN"));
        Role userRole = roleRepository.save(new Role("ROLE_USER"));

        regularUser = new User(USERNAME, passwordEncoder.encode(PASSWORD));
        regularUser.getRoles().add(userRole);
        regularUser = userRepository.save(regularUser);

        adminUser = new User(ADMIN_USERNAME, passwordEncoder.encode(PASSWORD));
        adminUser.getRoles().add(adminRole);
        adminUser = userRepository.save(adminUser);

        // Наполняем задачами с разным временем/статусами.
        oldTaskRegularOpen = new Task("GelKod: старая OPEN", regularUser, TaskStatus.OPEN);
        oldTaskRegularOpen.setCreatedAt(LocalDateTime.now().minusDays(2));
        oldTaskRegularOpen = taskRepository.save(oldTaskRegularOpen);

        regularInProgressTask = new Task("GelKod: вчера IN_PROGRESS", regularUser, TaskStatus.IN_PROGRESS);
        regularInProgressTask.setCreatedAt(LocalDateTime.now().minusHours(18));
        regularInProgressTask = taskRepository.save(regularInProgressTask);

        adminDoneTask = new Task("GelKod_admin: DONE недавно", adminUser, TaskStatus.DONE);
        adminDoneTask.setCreatedAt(LocalDateTime.now().minusMinutes(40));
        adminDoneTask = taskRepository.save(adminDoneTask);
    }

    @Test
    void loginReturns401ForWrongCredentials() {
        Response response = RestAssured.given()
                .baseUri("http://localhost")
                .port(port)
                .contentType(JSON)
                .body("""
                        {
                          "username": "no-such-user",
                          "password": "bad"
                        }
                        """)
                .post("/auth/login");

        response.then().statusCode(401);
    }

    @Test
    void loginReturns401ForBlankPassword() {
        RestAssured.given()
                .baseUri("http://localhost")
                .port(port)
                .contentType(JSON)
                .body("{\"username\":\"" + USERNAME + "\",\"password\":\"\"}")
                .post("/auth/login")
                .then()
                .statusCode(401);
    }

    @Test
    void loginAndMeWorkForRegularUserGelKod() {
        RestAssured.given()
                .baseUri("http://localhost")
                .port(port)
                .contentType(JSON)
                .body("{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}")
                .post("/auth/login")
                .then()
                .statusCode(200);

        Response me = RestAssured.given()
                .baseUri("http://localhost")
                .port(port)
                .auth().preemptive().basic(USERNAME, PASSWORD)
                .get("/auth/me");

        me.then().statusCode(200);

        Integer id = me.jsonPath().getInt("id");
        String meUsername = me.jsonPath().getString("username");

        assertNotNull(id);
        assertEquals(USERNAME, meUsername);
        assertEquals(regularUser.getId().intValue(), id.intValue());
        assertTrue(me.jsonPath().getList("roles").contains("ROLE_USER"));
        assertFalse(me.jsonPath().getList("roles").contains("ROLE_ADMIN"));
    }

    @Test
    void loginAndMeWorkForAdminUserGelKodAdmin() {
        RestAssured.given()
                .baseUri("http://localhost")
                .port(port)
                .contentType(JSON)
                .body("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"" + PASSWORD + "\"}")
                .post("/auth/login")
                .then()
                .statusCode(200);

        Response me = RestAssured.given()
                .baseUri("http://localhost")
                .port(port)
                .auth().preemptive().basic(ADMIN_USERNAME, PASSWORD)
                .get("/auth/me");

        me.then().statusCode(200);
        assertEquals(ADMIN_USERNAME, me.jsonPath().getString("username"));
        assertTrue(me.jsonPath().getList("roles").contains("ROLE_ADMIN"));
    }

    @Test
    void tasksListReturnsOnlyCurrentUserTasksByUserId() {
        Response response = tasksRequest(USERNAME, PASSWORD)
                .queryParam("userId", regularUser.getId())
                .when()
                .get();

        response.then().statusCode(200);

        List<Map<String, Object>> tasks = response.jsonPath().getList("$");
        assertEquals(2, tasks.size());
        assertTrue(tasks.stream().allMatch(t -> ((Number) t.get("createdBy")).longValue() == regularUser.getId()));
    }

    @Test
    void getTaskByIdWorksForExistingTaskAndReturns404ForMissing() {
        tasksRequest(USERNAME, PASSWORD)
                .when()
                .get("/{id}", oldTaskRegularOpen.getId())
                .then()
                .statusCode(200);

        tasksRequest(USERNAME, PASSWORD)
                .when()
                .get("/{id}", 9_999_999L)
                .then()
                .statusCode(404);
    }

    @Test
    void createTaskReturns201AndUsesAuthenticatedUser() {
        Response response = tasksRequest(USERNAME, PASSWORD)
                .contentType(JSON)
                .body("""
                        {
                          "title": "Создано из теста ЛР5",
                          "status": "DONE",
                          "createdBy": 123456
                        }
                        """)
                .when()
                .post();

        response.then().statusCode(201);
        assertAll(
                () -> assertNotNull(response.jsonPath().getLong("id")),
                () -> assertEquals("Создано из теста ЛР5", response.jsonPath().getString("title")),
                () -> assertEquals("DONE", response.jsonPath().getString("status")),
                () -> assertEquals(regularUser.getId().intValue(), response.jsonPath().getInt("createdBy")),
                () -> assertNotNull(response.jsonPath().getString("createdAt"))
        );
    }

    @Test
    void createTaskWithoutStatusUsesOpenByDefault() {
        Response response = tasksRequest(USERNAME, PASSWORD)
                .contentType(JSON)
                .body("""
                        {
                          "title": "Без статуса"
                        }
                        """)
                .when()
                .post();

        response.then().statusCode(201);
        assertEquals("OPEN", response.jsonPath().getString("status"));
    }

    @Test
    void updateTaskByIdChangesTitleAndStatus() {
        tasksRequest(USERNAME, PASSWORD)
                .contentType(JSON)
                .body("""
                        {
                          "id": 777777,
                          "title": "Обновлённый заголовок",
                          "status": "CLOSED"
                        }
                        """)
                .when()
                .put("/{id}", regularInProgressTask.getId())
                .then()
                .statusCode(200);

        Response fetch = tasksRequest(USERNAME, PASSWORD)
                .when()
                .get("/{id}", regularInProgressTask.getId());

        fetch.then().statusCode(200);
        assertEquals("Обновлённый заголовок", fetch.jsonPath().getString("title"));
        assertEquals("CLOSED", fetch.jsonPath().getString("status"));
    }

    @Test
    void deleteTaskReturns403ForRegularUserAnd204ForAdmin() {
        tasksRequest(USERNAME, PASSWORD)
                .when()
                .delete("/{id}", oldTaskRegularOpen.getId())
                .then()
                .statusCode(403);

        tasksRequest(ADMIN_USERNAME, PASSWORD)
                .when()
                .delete("/{id}", oldTaskRegularOpen.getId())
                .then()
                .statusCode(204);
    }

    @Test
    void meReturns401WithoutAuthHeader() {
        RestAssured.given()
                .baseUri("http://localhost")
                .port(port)
                .get("/auth/me")
                .then()
                .statusCode(401);
    }

    @Test
    void corsPreflightAllowsFrontendOriginForAuthMe() {
        Response response = RestAssured.given()
                .baseUri("http://localhost")
                .port(port)
                .header("Origin", FRONTEND_ORIGIN)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization, Content-Type")
                .when()
                .options("/auth/me");

        response.then().statusCode(200);
        assertEquals(FRONTEND_ORIGIN, response.getHeader("Access-Control-Allow-Origin"));
    }

    private RequestSpecification tasksRequest(String username, String password) {
        return RestAssured.given()
                .baseUri("http://localhost")
                .port(port)
                .basePath("/tasks")
                .auth().preemptive().basic(username, password);
    }
}

