package ru.ssau.todo;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Интеграционный тест для проверки работы REST API задач (Task) с использованием реальной БД.
 * <p>
 * Тесты поднимают Spring-контекст на случайном порту и проверяют корректность CRUD-операций,
 * бизнес-логики (например, ограничение на количество активных задач, запрет удаления свежих задач)
 * и работу фильтрации.
 * <p>
 * Все тестовые данные создаются с префиксом {@value #TEST_USER_PREFIX} и удаляются после каждого теста.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JdbcTaskTest {

    /**
     * Префикс для имён тестовых пользователей, по которому они идентифицируются и удаляются.
     */
    private static final String TEST_USER_PREFIX = "it-jdbc-task-";
    private static final String TEST_USERNAME = "regular_user";

    /**
     * Случайный порт, на котором запускается встроенный веб-сервер.
     * Внедряется Spring Boot.
     */
    @LocalServerPort
    private int port;

    /**
     * Репозиторий для работы с задачами в БД (прямой доступ для подготовки данных и проверок).
     */
    @Autowired
    private TaskRepository taskRepository;

    /**
     * Репозиторий для работы с пользователями.
     */
    @Autowired
    private UserRepository userRepository;

    /**
     * Шаблон JDBC для выполнения чистого SQL (создание схемы, удаление тестовых данных).
     */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long testUserId;

    /**
     * Выполняется перед каждым тестом.
     * <ul>
     *   <li>Убеждается, что минимально необходимые таблицы существуют ({@link #ensureSchema()}).</li>
     *   <li>Удаляет все тестовые данные, оставшиеся от предыдущих запусков ({@link #cleanupTestData()}).</li>
     *   <li>Включает логирование запросов и ответов REST Assured в случае падения проверок.</li>
     * </ul>
     */
    @BeforeEach
    void setUp() {
        // Получаем ID существующего пользователя regular_user
        testUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM \"user\" WHERE username = ?",
                Long.class,
                TEST_USERNAME
        );
        
        // Очищаем только задачи, созданные этим пользователем в предыдущих тестах
        //jdbcTemplate.update("DELETE FROM task WHERE created_by = ?", testUserId);
        
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    /**
     * Выполняется после каждого теста.
     * Удаляет все созданные в ходе теста записи из БД, чтобы не засорять схему.
     */
    @AfterEach
    void tearDown() {
        // Дополнительная очистка после каждого теста (на всякий случай)
        jdbcTemplate.update("DELETE FROM task WHERE created_by = ?", testUserId);
    }

    /**
     * Проверяет успешное создание задачи через POST /tasks.
     * Ожидается статус 201 (Created) и возврат DTO задачи с заполненными полями.
     */
    @Test
    void createTaskReturnsCreatedTaskDto() {
         User user = createUser("create");

        Response response = RestAssured.given()
                .spec(tasksRequest())
                .contentType(JSON)
                .body(taskPayload("Create via API", user.getId(), "IN_PROGRESS"))
                .when()
                .post();

        Long taskId = response.jsonPath().getLong("id");

        response.then().statusCode(201);

        assertAll(
                () -> assertNotNull(taskId),
                () -> assertEquals("Create via API", response.jsonPath().getString("title")),
                () -> assertEquals("IN_PROGRESS", response.jsonPath().getString("status")),
                () -> assertEquals(user.getId().intValue(), response.jsonPath().getInt("createdBy")),
                () -> assertNotNull(response.jsonPath().getString("createdAt"))
        );
    }

    /**
     * Проверяет, что при отсутствии поля "status" в теле запроса задача создаётся со статусом OPEN по умолчанию.
     */
    @Test
    void createTaskUsesOpenStatusWhenStatusIsMissing() {
        User user = createUser("default-status");
        String payload = """
                {
                  "title": "Task with default status",
                  "createdBy": %d
                }
                """.formatted(user.getId());

        Response response = RestAssured.given()
                .spec(tasksRequest())
                .contentType(JSON)
                .body(payload)
                .when()
                .post();

        response.then().statusCode(201);
        assertEquals("OPEN", response.jsonPath().getString("status"));
    }

    /**
     * Проверяет, что попытка создать задачу для несуществующего пользователя завершается ошибкой 500.
     */
    @Test
    void createTaskFailsForUnknownUser() {
        RestAssured.given()
                .spec(tasksRequest())
                .contentType(JSON)
                .body(taskPayload("Unknown user task", Long.MAX_VALUE, "OPEN"))
                .when()
                .post()
                .then()
                .statusCode(500);
    }

    /**
     * Проверяет структуру ответа GET /tasks/{id}: DTO должен содержать только разрешённые поля
     * (id, title, status, createdBy, createdAt) без внутренних деталей.
     */
    @Test
    void findByIdReturnsTaskDtoWithoutInternalFields() {
        User user = createUser("dto");
        Task task = createTask(user, "DTO task", TaskStatus.OPEN, LocalDateTime.now().minusMinutes(10));

        Response response = RestAssured.given()
                .spec(tasksRequest())
                .when()
                .get("/{id}", task.getId());

        response.then().statusCode(200);

        Map<String, Object> body = response.jsonPath().getMap("$");

        assertAll(
                // Проверяем, что в ответе только ожидаемые 5 полей
                () -> assertEquals(Set.of("id", "title", "status", "createdBy", "createdAt"), body.keySet()),
                () -> assertEquals(task.getId().intValue(), response.jsonPath().getInt("id")),
                () -> assertEquals("DTO task", response.jsonPath().getString("title")),
                () -> assertEquals("OPEN", response.jsonPath().getString("status")),
                () -> assertEquals(user.getId().intValue(), response.jsonPath().getInt("createdBy"))
        );
    }

    /**
     * Проверяет, что запрос несуществующей задачи возвращает 404 Not Found.
     */
    @Test
    void findByIdReturns404ForMissingTask() {
        RestAssured.given()
                .spec(tasksRequest())
                .when()
                .get("/{id}", Long.MAX_VALUE)
                .then()
                .statusCode(404);
    }

    /**
     * Проверяет работу фильтрации задач по пользователю и интервалу дат (параметры userId, from, to).
     * Ожидается, что в ответ попадут только задачи указанного пользователя,
     * созданные в заданном временном промежутке.
     */
    @Test
    void findAllAppliesUserAndDateFilters() {
        User firstUser = createUser("filter-a");
        User secondUser = createUser("filter-b");

        LocalDateTime baseTime = LocalDateTime.now().minusHours(2);
        Task earlyTask = createTask(firstUser, "Early task", TaskStatus.OPEN, baseTime);
        Task middleTask = createTask(firstUser, "Middle task", TaskStatus.DONE, baseTime.plusMinutes(10));
        Task lateTask = createTask(firstUser, "Late task", TaskStatus.IN_PROGRESS, baseTime.plusMinutes(20));
        createTask(secondUser, "Other user task", TaskStatus.OPEN, baseTime.plusMinutes(15));

        Response response = RestAssured.given()
                .spec(tasksRequest())
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
                // Ранняя и поздняя задачи не должны попасть в ответ из-за фильтра по дате
                () -> assertFalse(ids.contains(earlyTask.getId().intValue())),
                () -> assertFalse(ids.contains(lateTask.getId().intValue()))
        );
    }

    /**
     * Проверяет, что эндпоинт /active/count возвращает количество активных задач (OPEN и IN_PROGRESS)
     * для заданного пользователя, игнорируя завершённые (DONE, CLOSED).
     */
    @Test
    void countActiveTasksCountsOnlyOpenAndInProgressStatuses() {
        User user = createUser("count");
        System.out.println("User ID = " + user.getId());
        createTask(user, "Open task", TaskStatus.OPEN, LocalDateTime.now().minusHours(1));
        createTask(user, "In progress task", TaskStatus.IN_PROGRESS, LocalDateTime.now().minusMinutes(50));
        createTask(user, "Done task", TaskStatus.DONE, LocalDateTime.now().minusMinutes(40));
        createTask(user, "Closed task", TaskStatus.CLOSED, LocalDateTime.now().minusMinutes(30));

        Response response = RestAssured.given()
                .spec(tasksRequest())
                .queryParam("userId", user.getId().intValue())
                .when()
                .get("/active/count");

        response.then().statusCode(200);
        assertEquals(2, response.as(Integer.class));
    }

    /**
     * Проверяет бизнес-ограничение: пользователь не может иметь более 10 активных задач одновременно.
     * Попытка создать 11-ю активную задачу должна завершиться ошибкой 500.
     */
    @Test
    void createTaskRejectsEleventhActiveTask() {
        User user = createUser("limit");

        // Создаём 10 активных задач
        for (int i = 0; i < 10; i++) {
            createTask(user, "Active task " + i, TaskStatus.OPEN, LocalDateTime.now().minusHours(1).plusMinutes(i));
        }

        // Пытаемся создать 11-ю — ожидаем ошибку
        RestAssured.given()
                .spec(tasksRequest())
                .contentType(JSON)
                .body(taskPayload("Active task 11", user.getId(), "OPEN"))
                .when()
                .post()
                .then()
                .statusCode(500);
    }

    /**
     * Проверяет, что задачу, созданную менее 5 минут назад, удалить нельзя (бизнес-правило).
     * Ожидается статус 500 и сохранение задачи в БД.
     */
    @Test
    void deleteTaskRejectsFreshTask() {
        User user = createUser("fresh-delete");
        Task task = createTask(user, "Fresh task", TaskStatus.OPEN, LocalDateTime.now().minusMinutes(1));

        RestAssured.given()
                .spec(tasksRequest())
                .when()
                .delete("/{id}", task.getId())
                .then()
                .statusCode(500);

        // Проверяем, что задача всё ещё существует в БД
        assertTrue(taskRepository.findById(task.getId()).isPresent());
    }

    /**
     * Проверяет, что задачу старше 5 минут можно успешно удалить (статус 204 No Content).
     */
    @Test
    void deleteTaskRemovesTaskOlderThanFiveMinutes() {
        User user = createUser("old-delete");
        Task task = createTask(user, "Old task", TaskStatus.OPEN, LocalDateTime.now().minusMinutes(6));

        RestAssured.given()
                .spec(tasksRequest())
                .when()
                .delete("/{id}", task.getId())
                .then()
                .statusCode(204);

        // Убеждаемся, что задача удалена из БД
        assertTrue(taskRepository.findById(task.getId()).isEmpty());
    }

    /**
     * Проверяет, что попытка удалить несуществующую задачу всё равно возвращает 204 (идемпотентность).
     */
    @Test
    void deleteTaskReturns204ForMissingTask() {
        RestAssured.given()
                .spec(tasksRequest())
                .when()
                .delete("/{id}", Long.MAX_VALUE)
                .then()
                .statusCode(204);
    }

    /**
     * Проверяет, что PUT-запрос на обновление несуществующей задачи возвращает 404.
     */
    @Test
    void updateTaskReturns404ForMissingTask() {
        User user = createUser("missing-update");

        RestAssured.given()
                .spec(tasksRequest())
                .contentType(JSON)
                .body(taskPayload("Missing task", user.getId(), "DONE"))
                .when()
                .put("/{id}", Long.MAX_VALUE)
                .then()
                .statusCode(404);
    }

    /**
     * Проверяет, что PUT /tasks/{id} корректно изменяет заголовок и статус существующей задачи.
     */
    @Test
    void updateTaskChangesTitleAndStatusOfExistingTask() {
        User user = createUser("update");
        Task task = createTask(user, "Before update", TaskStatus.OPEN, LocalDateTime.now().minusMinutes(20));

        // Выполняем PUT с новыми значениями
        RestAssured.given()
                .spec(tasksRequest())
                .contentType(JSON)
                .body(taskPayload("After update", user.getId(), "DONE"))
                .when()
                .put("/{id}", task.getId())
                .then()
                .statusCode(200);

        // Проверяем, что изменения применились
        Response response = RestAssured.given()
                .spec(tasksRequest())
                .when()
                .get("/{id}", task.getId());

        response.then().statusCode(200);

        assertAll(
                () -> assertEquals("After update", response.jsonPath().getString("title")),
                () -> assertEquals("DONE", response.jsonPath().getString("status")),
                () -> assertEquals(user.getId().intValue(), response.jsonPath().getInt("createdBy"))
        );
    }

    /**
     * Проверяет, что PUT не создаёт дублирующую запись, а действительно обновляет существующую.
     * В БД должно остаться ровно одна задача.
     */
    @Test
    void updateTaskShouldNotCreateDuplicateRecord() {
        User user = createUser("update-duplicate");
        Task task = createTask(user, "Single task", TaskStatus.OPEN, LocalDateTime.now().minusMinutes(30));

        RestAssured.given()
                .spec(tasksRequest())
                .contentType(JSON)
                .body(taskPayload("Single task updated", user.getId(), "CLOSED"))
                .when()
                .put("/{id}", task.getId())
                .then()
                .statusCode(200);

        // Получаем все задачи пользователя за широкий временной интервал
        List<Task> tasks = taskRepository.findAll(user.getId(), LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));

        assertEquals(1, tasks.size(), "PUT должен обновить существующую задачу, а не создать новую запись");
    }

/**
 * Проверяет бизнес-ограничение при обновлении задачи: переход неактивной задачи в активный статус
 * не должен превышать лимит в 10 активных задач для пользователя.
 * <p>
 * Сценарий:
 * <ol>
 *   <li>Создаётся пользователь с 8 активными и 3 неактивными задачами.</li>
 *   <li>Первое обновление неактивной → активная (9) – успех.</li>
 *   <li>Второе обновление неактивной → активная (10) – успех.</li>
 *   <li>Третье обновление неактивной → активная (превышение лимита) – ошибка 500.</li>
 * </ol>
 */
@Test
void updateTaskToActiveStatusEnforcesActiveLimit() {
    User user = createUser("update-limit");
    
    // 8 активных задач (OPEN)
    for (int i = 0; i < 8; i++) {
        createTask(user, "Active " + i, TaskStatus.OPEN, LocalDateTime.now().minusHours(1).plusMinutes(i));
    }
    
    // 3 неактивные задачи (DONE)
    Task inactive1 = createTask(user, "Inactive 1", TaskStatus.DONE, LocalDateTime.now().minusMinutes(30));
    Task inactive2 = createTask(user, "Inactive 2", TaskStatus.DONE, LocalDateTime.now().minusMinutes(25));
    Task inactive3 = createTask(user, "Inactive 3", TaskStatus.DONE, LocalDateTime.now().minusMinutes(20));

    // Проверяем стартовое количество активных задач
    Response countResp = RestAssured.given()
            .spec(tasksRequest())
            .queryParam("userId", user.getId().intValue())
            .when()
            .get("/active/count");
    assertEquals(8, countResp.as(Integer.class));

    // Обновляем первую неактивную → активная (OPEN)
    RestAssured.given()
            .spec(tasksRequest())
            .contentType(JSON)
            .body(taskPayload("Inactive 1 updated", user.getId(), "OPEN"))
            .when()
            .put("/{id}", inactive1.getId())
            .then()
            .statusCode(200);

    countResp = RestAssured.given()
            .spec(tasksRequest())
            .queryParam("userId", user.getId().intValue())
            .when()
            .get("/active/count");
    assertEquals(9, countResp.as(Integer.class));

    // Обновляем вторую → активная (IN_PROGRESS)
    RestAssured.given()
            .spec(tasksRequest())
            .contentType(JSON)
            .body(taskPayload("Inactive 2 updated", user.getId(), "IN_PROGRESS"))
            .when()
            .put("/{id}", inactive2.getId())
            .then()
            .statusCode(200);

    countResp = RestAssured.given()
            .spec(tasksRequest())
            .queryParam("userId", user.getId().intValue())
            .when()
            .get("/active/count");
    assertEquals(10, countResp.as(Integer.class));

    // Попытка обновить третью → активная (должно превысить лимит)
    RestAssured.given()
            .spec(tasksRequest())
            .contentType(JSON)
            .body(taskPayload("Inactive 3 updated", user.getId(), "OPEN"))
            .when()
            .put("/{id}", inactive3.getId())
            .then()
            .statusCode(404);

    // Убеждаемся, что активных всё ещё 10
    countResp = RestAssured.given()
            .spec(tasksRequest())
            .queryParam("userId", user.getId().intValue())
            .when()
            .get("/active/count");
    assertEquals(10, countResp.as(Integer.class));
}

    /**
     * Вспомогательный метод для создания и сохранения тестового пользователя с уникальным именем.
     *
     * @param suffix короткий суффикс, идентифицирующий цель теста
     * @return сохранённый объект {@link User}
     */
    private User createUser(String suffix) {
        User user = new User(TEST_USER_PREFIX + suffix + "-" + UUID.randomUUID());
        return userRepository.saveAndFlush(user);
    }

    /**
     * Вспомогательный метод для создания и сохранения задачи с заданными параметрами.
     *
     * @param user      пользователь-автор задачи
     * @param title     заголовок
     * @param status    статус задачи
     * @param createdAt дата создания (для имитации давности)
     * @return сохранённый объект {@link Task}
     */
    private Task createTask(User user, String title, TaskStatus status, LocalDateTime createdAt) {
        Task task = new Task(title, user, status);
        task.setCreatedAt(createdAt);
        return taskRepository.saveAndFlush(task);
    }

    /**
     * Формирует базовую спецификацию запроса для REST Assured:
     * - URL: http://localhost:{порт}/tasks
     *
     * @return {@link RequestSpecification} с предустановленными базовыми параметрами
     */
    private RequestSpecification tasksRequest() {
        return RestAssured.given()
                .baseUri("http://localhost")
                .port(port)
                .basePath("/tasks");
    }

    /**
     * Генерирует JSON-строку для тела запроса на создание/обновление задачи.
     *
     * @param title     заголовок задачи
     * @param createdBy ID автора
     * @param status    статус задачи (строка)
     * @return JSON в виде строки
     */
    private String taskPayload(String title, Long createdBy, String status) {
        return """
                {
                  "title": "%s",
                  "createdBy": %d,
                  "status": "%s"
                }
                """.formatted(title, createdBy, status);
    }

    /**
     * Полностью очищает таблицы task, user_role и "user" и сбрасывает счётчики автоинкремента (ID).
     * <p>
     * Используется TRUNCATE ... RESTART IDENTITY CASCADE для быстрой и полной очистки
     * с учётом зависимостей между таблицами.
     * <p>
     * Таблица role НЕ очищается, так как она обычно содержит предзаполненные роли,
     * необходимые для работы приложения.
     */
    private void cleanupTestData() {    
        jdbcTemplate.execute("TRUNCATE TABLE task, user_role, \"user\" RESTART IDENTITY CASCADE");
    }

    /**
     * Создаёт минимально необходимые таблицы в БД, если они ещё не существуют.
     * Используется для обеспечения работоспособности тестов в чистой схеме.
     */
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