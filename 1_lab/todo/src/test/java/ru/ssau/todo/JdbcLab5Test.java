package ru.ssau.todo;

import io.restassured.RestAssured;
import io.restassured.response.Response;
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

import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Пример интеграционных тестов под ЛР5 (Angular + Auth API).
 *
 * В ЛР5 фронтенд ожидает:
 * - POST /auth/login (401 при неверных данных)
 * - GET /auth/me (возвращает id/username/roles, требует Basic Auth)
 *
 * Тесты сделаны в стиле ЛР4 (RestAssured + RANDOM_PORT) и рассчитаны на запуск
 * на вашей PostgreSQL (как у вас уже было в ЛР4).
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

        User user = new User(USERNAME, passwordEncoder.encode(PASSWORD));
        user.getRoles().add(userRole);
        user = userRepository.save(user);

        User admin = new User(ADMIN_USERNAME, passwordEncoder.encode(PASSWORD));
        admin.getRoles().add(adminRole);
        admin = userRepository.save(admin);

        // Наполняем задачами с разным временем/статусами.
        Task t1 = new Task("GelKod: старая OPEN", user, TaskStatus.OPEN);
        t1.setCreatedAt(LocalDateTime.now().minusDays(2));
        taskRepository.save(t1);

        Task t2 = new Task("GelKod: вчера IN_PROGRESS", user, TaskStatus.IN_PROGRESS);
        t2.setCreatedAt(LocalDateTime.now().minusHours(18));
        taskRepository.save(t2);

        Task t3 = new Task("GelKod_admin: DONE недавно", admin, TaskStatus.DONE);
        t3.setCreatedAt(LocalDateTime.now().minusMinutes(40));
        taskRepository.save(t3);
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

        assertTrue(me.jsonPath().getList("roles").contains("ROLE_USER"));
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
    void meReturns401WithoutAuthHeader() {
        RestAssured.given()
                .baseUri("http://localhost")
                .port(port)
                .get("/auth/me")
                .then()
                .statusCode(401);
    }
}

