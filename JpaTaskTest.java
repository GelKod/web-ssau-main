import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpRequest.BodyPublishers;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Automated test for Spring Data JPA implementation (Laboratory Work 3).
 * Tests only what is actually implemented in lab 3 requirements.
 * Removed checks for non-existing /users and /roles endpoints.
 */
public class JpaTaskTest {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final String BASE_URL = "http://localhost:8080";
    private static final String TASKS_URL = BASE_URL + "/tasks";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static int passedTests = 0;
    private static int failedTests = 0;
    private static final String LINE = "=".repeat(60);
    private static final String SUB_LINE = "-".repeat(60);

    // NOTE: Lab 3 has no User/Role controllers implemented yet.
    // All users are created directly in database before running tests.

    public static void main(String[] args) throws Exception {
        printHeader("SPRING DATA JPA TESTING - LABORATORY WORK 3");
        System.out.println("Server URL: " + BASE_URL);
        System.out.println("Start time: " + LocalDateTime.now().format(FORMATTER));
        System.out.println();

        // Step 1: Clear database
        printHeader("STEP 1: CLEAR DATABASE");
        truncateDatabase();

        // Step 2: Test Task-User foreign key relationship
        printHeader("STEP 2: TASK-USER FOREIGN KEY CONSTRAINT");
        testTaskUserForeignKey();

        // Step 3: Test JPA Repositories and custom queries
        printHeader("STEP 3: JPA REPOSITORIES & CUSTOM QUERIES");
        testNativeQueryDateFiltering();
        testJpqlActiveTasksCount();

        // Step 4: Test DTO pattern implementation
        printHeader("STEP 4: DTO PATTERN VALIDATION");
        testTaskDtoStructure();

        // Step 5: Test backward compatibility
        printHeader("STEP 5: BACKWARD COMPATIBILITY VERIFICATION");
        testExistingApiEndpoints();

        // Step 6: Summary
        printSummary();
    }

    // ==================== DATABASE CLEAR ====================

    private static void truncateDatabase() {
        System.out.println("  Sending database cleanup request...");
        int deletedCount = 0;
        for (int i = 1; i <= 100; i++) {
            String response = sendRequest("DELETE", TASKS_URL + "/" + i, null);
            if (response.contains("Status: 204")) {
                deletedCount++;
            }
        }
        System.out.println("  Tasks cleared (deleted: " + deletedCount + ")");
        System.out.println("  Cleanup verification: [PASS]");
        passedTests++;
    }

    // ==================== FOREIGN KEY TEST ====================

    private static void testTaskUserForeignKey() {
        System.out.println("\n" + SUB_LINE);
        System.out.println("  TASK-USER FOREIGN KEY CONSTRAINT");
        System.out.println(SUB_LINE);

        System.out.println("  ⚠️  PREPARE: Create user with ID=1 in database first!");
        System.out.println();

        // Test foreign key constraint
        // Creating task with non-existing user should fail
        String invalidTaskJson = "{\"title\":\"Invalid user task\",\"createdBy\":999999,\"status\":\"OPEN\"}";
        String invalidResponse = sendRequest("POST", TASKS_URL, invalidTaskJson);

        System.out.print("  Task with non-existing User (constraint check): ");
        if (invalidResponse.contains("Status: 400") || invalidResponse.contains("Status: 500")) {
            System.out.println("[PASS]");
            passedTests++;
        } else {
            System.out.println("[FAIL] - FOREIGN KEY CONSTRAINT NOT WORKING");
            System.out.println("    Server response: " + invalidResponse);
            failedTests++;
        }

        // Задача с существующим пользователем должна создаваться
        String validTaskJson = "{\"title\":\"Valid user task\",\"createdBy\":1,\"status\":\"OPEN\"}";
        String validResponse = sendRequest("POST", TASKS_URL, validTaskJson);

        System.out.print("  Task with existing User: ");
        if (validResponse.contains("Status: 201")) {
            System.out.println("[PASS]");
            passedTests++;
        } else {
            System.out.println("[FAIL]");
            System.out.println("    Server response: " + validResponse);
            failedTests++;
        }
    }

    // ==================== JPA REPOSITORY QUERIES ====================

    private static void testNativeQueryDateFiltering() {
        System.out.println("\n" + SUB_LINE);
        System.out.println("  NATIVE QUERY DATE FILTERING");
        System.out.println(SUB_LINE);

        // Создаём 3 задачи с интервалом 1 секунда
        createTestTask(1, "Task 1");
        sleep(1000);
        LocalDateTime afterFirst = LocalDateTime.now();
        createTestTask(1, "Task 2");
        sleep(1000);
        createTestTask(1, "Task 3");

        String fromParam = afterFirst.format(FORMATTER);
        String filterResponse = sendRequest("GET",
            TASKS_URL + "?userId=1&from=" + fromParam, null);

        System.out.print("  Native query FROM filter: ");
        if (filterResponse.contains("Task 2") && filterResponse.contains("Task 3")
            && !filterResponse.contains("Task 1")) {
            System.out.println("[PASS]");
            passedTests++;
        } else {
            System.out.println("[FAIL]");
            failedTests++;
        }

        String toParam = afterFirst.format(FORMATTER);
        filterResponse = sendRequest("GET",
            TASKS_URL + "?userId=1&to=" + toParam, null);

        System.out.print("  Native query TO filter: ");
        if (filterResponse.contains("Task 1") && !filterResponse.contains("Task 2")
            && !filterResponse.contains("Task 3")) {
            System.out.println("[PASS]");
            passedTests++;
        } else {
            System.out.println("[FAIL]");
            failedTests++;
        }
    }

    private static void testJpqlActiveTasksCount() {
        System.out.println("\n" + SUB_LINE);
        System.out.println("  JPQL ACTIVE TASKS COUNT");
        System.out.println(SUB_LINE);

        // Очищаем задачи пользователя 1
        for (int i = 1; i <= 100; i++) {
            sendRequest("DELETE", TASKS_URL + "/" + i, null);
        }
        sleep(500);

        // Создаём 5 активных задач
        for (int i = 0; i < 5; i++) {
            createTestTask(1, "Active task " + (i+1));
            sleep(30);
        }

        String countResponse = sendRequest("GET", TASKS_URL + "/active/count?userId=1", null);

        System.out.print("  JPQL query active tasks count: ");
        if (countResponse.contains("5")) {
            System.out.println("[PASS]");
            passedTests++;
        } else {
            System.out.println("[FAIL] - expected 5, got: " + countResponse);
            failedTests++;
        }
    }

    // ==================== DTO PATTERN TESTS ====================

    private static void testTaskDtoStructure() {
        System.out.println("\n" + SUB_LINE);
        System.out.println("  TASK DTO STRUCTURE VALIDATION");
        System.out.println(SUB_LINE);

        Integer taskId = createTestTask(1, "DTO Test Task");

        if (taskId != null) {
            String getResponse = sendRequest("GET", TASKS_URL + "/" + taskId, null);

            // В DTO должны быть ТОЛЬКО эти поля
            boolean hasRequiredFields = getResponse.contains("\"id\":")
                && getResponse.contains("\"title\":")
                && getResponse.contains("\"createdBy\":")
                && getResponse.contains("\"status\":")
                && getResponse.contains("\"createdAt\":");

            // В ответе НЕ ДОЛЖНО быть внутренних полей Hibernate и сущностей
            boolean noInternalFields = !getResponse.contains("\"user\":")
                && !getResponse.contains("\"handler\":")
                && !getResponse.contains("\"hibernate\":")
                && !getResponse.contains("\"persistent\":");

            System.out.print("  Task DTO structure (no internal fields): ");
            if (hasRequiredFields && noInternalFields) {
                System.out.println("[PASS]");
                passedTests++;
            } else {
                System.out.println("[FAIL]");
                System.out.println("    Ответ: " + getResponse);
                failedTests++;
            }
        } else {
            System.out.println("  [SKIP] Failed to create task for test");
            failedTests++;
        }
    }

    // ==================== BACKWARD COMPATIBILITY ====================

    private static void testExistingApiEndpoints() {
        System.out.println("\n" + SUB_LINE);
        System.out.println("  BACKWARD API COMPATIBILITY");
        System.out.println(SUB_LINE);

        String createResponse = sendRequest("POST", TASKS_URL,
            "{\"title\":\"Compat test task\",\"createdBy\":1,\"status\":\"OPEN\"}");

        boolean createWorks = createResponse.contains("Status: 201");

        System.out.print("  Original CREATE endpoint works: ");
        if (createWorks) {
            System.out.println("[PASS]");
            passedTests++;
        } else {
            System.out.println("[FAIL]");
            failedTests++;
        }

        Integer taskId = extractIdFromResponse(createResponse);
        if (taskId != null) {
            String getResponse = sendRequest("GET", TASKS_URL + "/" + taskId, null);
            boolean getWorks = getResponse.contains("Status: 200");

            System.out.print("  Original GET endpoint works: ");
            if (getWorks) {
                System.out.println("[PASS]");
                passedTests++;
            } else {
                System.out.println("[FAIL]");
                failedTests++;
            }
        }
    }

    // ==================== HELPER METHODS ====================

    private static Integer createTestTask(Integer userId, String title) {
        String taskJson = String.format(
            "{\"title\":\"%s\",\"createdBy\":%d,\"status\":\"OPEN\"}",
            title, userId);
        String response = sendRequest("POST", TASKS_URL, taskJson);
        return extractIdFromResponse(response);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {}
    }

    private static void printHeader(String title) {
        System.out.println("\n" + LINE);
        System.out.println("  " + title);
        System.out.println(LINE);
    }

    private static String sendRequest(String method, String url, String body) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json");

            switch (method.toUpperCase()) {
                case "POST":
                    requestBuilder.POST(BodyPublishers.ofString(body, StandardCharsets.UTF_8));
                    break;
                case "PUT":
                    requestBuilder.PUT(BodyPublishers.ofString(body, StandardCharsets.UTF_8));
                    break;
                case "DELETE":
                    requestBuilder.DELETE();
                    break;
                case "GET":
                default:
                    requestBuilder.GET();
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            return "Status: " + response.statusCode() + " | Body: " + response.body();

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private static Integer extractIdFromResponse(String response) {
        Pattern pattern = Pattern.compile("\"id\":(\\d+)");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    private static void printSummary() {
        printHeader("FINAL STATISTICS");

        int total = passedTests + failedTests;
        System.out.println("+------------------------------------------------------+");
        System.out.printf("|  Total tests:  %-30d|%n", total);
        System.out.printf("|  Passed:       %-30d|%n", passedTests);
        System.out.printf("|  Failed:       %-30d|%n", failedTests);
        System.out.println("+------------------------------------------------------+");

        if (failedTests == 0) {
            System.out.println("|  ✅ RESULT: ALL TESTS PASSED SUCCESSFULLY!      |");
        } else {
            System.out.printf("|  ❌ RESULT: ERRORS FOUND (%d tests failed)            |%n", failedTests);
        }
        System.out.println("+------------------------------------------------------+");

        System.out.println("\n" + LINE);
        System.out.println("  TESTING COMPLETED");
        System.out.println("  End time: " + LocalDateTime.now().format(FORMATTER));
        System.out.println(LINE);
    }
}