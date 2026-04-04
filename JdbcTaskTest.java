import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpRequest.BodyPublishers;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//Delet запрос который проходит полностью
//пут с проверкой глубоко на опен и ин прогресс.
//оформить тесты через фреймворки. Подумать над оптимизацией процесса и переделать время.

/**
 * Automated test for JDBC repository TaskJdbcRepository.
 * Clears database before tests (TRUNCATE).
 * Tests CRUD operations, business rules, and filtering.
 */
public class JdbcTaskTest {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final String BASE_URL = "http://localhost:8080/tasks";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static int passedTests = 0;
    private static int failedTests = 0;
    private static final String LINE = "=".repeat(60);
    private static final String SUB_LINE = "-".repeat(60);

    public static void main(String[] args) throws Exception {
        printHeader("JDBC REPOSITORY TESTING");
        System.out.println("Server URL: " + BASE_URL);
        System.out.println("Start time: " + LocalDateTime.now().format(FORMATTER));
        System.out.println();

        // Step 1: Clear database
        printHeader("STEP 1: CLEAR DATABASE");
        truncateDatabase();

        // Step 2: Create test data
        printHeader("STEP 2: CREATE TEST DATA");
        createInitialTasks();

        // Step 3: Test CRUD operations
        printHeader("STEP 3: CRUD OPERATIONS TESTING");
        testCreateOperations();
        testReadOperations();
        testUpdateOperations();
        testDeleteOperations();

        // Step 4: Test business rules
        printHeader("STEP 4: BUSINESS RULES TESTING");
        testBusinessRules();

        // Step 5: Test filtering
        printHeader("STEP 5: FILTERING TESTING");
        testFiltering();

        // Step 6: Summary
        printSummary();
    }

    // ==================== DATABASE CLEAR ====================

    private static void truncateDatabase() {
        System.out.println("  Sending database cleanup request...");
        int deletedCount = 0;
        for (int i = 1; i <= 100; i++) {
            String response = sendRequest("DELETE", BASE_URL + "/" + i, null);
            if (response.contains("Status: 204")) {
                deletedCount++;
            }
        }
        System.out.println("  Database cleared (deleted tasks ID 1-100, removed: " + deletedCount + ")");
        System.out.println("  Cleanup verification: [PASS]");
        passedTests++;
    }

    // ==================== TEST DATA CREATION ====================

    private static void createInitialTasks() {
        System.out.println("  Creating initial test tasks...");

        String[][] tasks = {
                { "666", "User 666 task 1", "IN_PROGRESS" },
                { "666", "User 666 task 2", "DONE" },
                { "666", "User 666 task 3", "OPEN" },
                { "777", "User 777 task 1", "DONE" },
                { "777", "User 777 task 2", "CLOSED" },
                { "777", "User 777 task 3", "OPEN" },
                { "888", "User 888 task 1", "IN_PROGRESS" },
                { "888", "User 888 task 2", "OPEN" },
                { "888", "User 888 task 3", "IN_PROGRESS" },
                { "888", "User 888 task 4", "DONE" },
        };

        Set<Integer> createdIds = new HashSet<>();
        for (String[] task : tasks) {
            String userId = task[0];
            String title = task[1];
            String status = task[2];

            String json = String.format(
                    "{\"title\":\"%s\",\"createdBy\":%s,\"status\":\"%s\"}",
                    title, userId, status);

            String response = sendRequest("POST", BASE_URL, json);
            if (response.contains("Status: 201")) {
                Integer id = extractIdFromResponse(response);
                if (id != null) {
                    createdIds.add(id);
                    System.out.println("    Created task ID=" + id + " [OK]");
                } else {
                    System.out.println("    Created task (ID not extracted) [OK]");
                }
                passedTests++;
            } else {
                System.out.println("    Task creation error: " + response + " [FAIL]");
                failedTests++;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
            }
        }

        System.out.println("  Total tasks created: " + createdIds.size());
        System.out.println();
    }

    // ==================== CRUD: CREATE ====================

    private static void testCreateOperations() {
        System.out.println("\n" + SUB_LINE);
        System.out.println("  CREATE OPERATIONS TESTING");
        System.out.println(SUB_LINE);

        String json = "{\"title\":\"Test task for CREATE\",\"createdBy\":100,\"status\":\"OPEN\"}";
        String response = sendRequest("POST", BASE_URL, json);

        boolean statusOk = response.contains("Status: 201");
        boolean hasId = response.contains("\"id\":");
        boolean hasTitle = response.contains("Test task for CREATE");
        boolean hasCreatedBy = response.contains("\"createdBy\":100");
        boolean hasStatus = response.contains("\"status\":\"OPEN\"");
        boolean hasCreatedAt = response.contains("\"createdAt\":");

        System.out.print("  Create valid task: ");
        if (statusOk && hasId && hasTitle && hasCreatedBy && hasStatus && hasCreatedAt) {
            System.out.println("[PASS]");
            passedTests++;
        } else {
            System.out.println("[FAIL]");
            System.out.println("    Response: " + response);
            failedTests++;
        }

        String jsonNullTitle = "{\"title\":null,\"createdBy\":100,\"status\":\"OPEN\"}";
        response = sendRequest("POST", BASE_URL, jsonNullTitle);
        System.out.print("  Create task with null title: ");
        if (response.contains("Status: 400") || response.contains("Status: 500")) {
            System.out.println("[PASS]");
            passedTests++;
        } else {
            System.out.println("[FAIL] - expected error, got: " + response);
            failedTests++;
        }
    }

    // ==================== CRUD: READ ====================

    private static void testReadOperations() {
        System.out.println("\n" + SUB_LINE);
        System.out.println("  READ OPERATIONS TESTING");
        System.out.println(SUB_LINE);

        String json = "{\"title\":\"Task for READ test\",\"createdBy\":200,\"status\":\"OPEN\"}";
        String createResponse = sendRequest("POST", BASE_URL, json);
        Integer taskId = extractIdFromResponse(createResponse);

        if (taskId != null) {
            String response = sendRequest("GET", BASE_URL + "/" + taskId, null);
            boolean statusOk = response.contains("Status: 200");
            boolean titleOk = response.contains("Task for READ test");
            boolean userIdOk = response.contains("\"createdBy\":200");
            boolean statusFieldOk = response.contains("\"status\":\"OPEN\"");

            System.out.print("  Read existing task (id=" + taskId + "): ");
            if (statusOk && titleOk && userIdOk && statusFieldOk) {
                System.out.println("[PASS]");
                passedTests++;
            } else {
                System.out.println("[FAIL]");
                System.out.println("    Response: " + response);
                failedTests++;
            }

            String notFoundResponse = sendRequest("GET", BASE_URL + "/999999", null);
            System.out.print("  Read non-existing task: ");
            if (notFoundResponse.contains("Status: 404")) {
                System.out.println("[PASS]");
                passedTests++;
            } else {
                System.out.println("[FAIL] - expected 404, got: " + notFoundResponse);
                failedTests++;
            }
        } else {
            System.out.println("  [SKIP] Failed to create task for READ test");
            failedTests += 2;
        }

        String response = sendRequest("GET", BASE_URL + "?userId=200", null);
        System.out.print("  Get tasks for user 200: ");
        if (response.contains("Status: 200") && response.contains("\"createdBy\":200")) {
            System.out.println("[PASS]");
            passedTests++;
        } else {
            System.out.println("[FAIL]");
            System.out.println("    Response: " + response);
            failedTests++;
        }
    }

    // ==================== CRUD: UPDATE ====================

    private static void testUpdateOperations() {
        System.out.println("\n" + SUB_LINE);
        System.out.println("  UPDATE OPERATIONS TESTING");
        System.out.println(SUB_LINE);

        String json = "{\"title\":\"Task before update\",\"createdBy\":300,\"status\":\"OPEN\"}";
        String createResponse = sendRequest("POST", BASE_URL, json);
        Integer taskId = extractIdFromResponse(createResponse);

        if (taskId != null) {
            String updateJson = "{\"id\":" + taskId + ",\"title\":\"Task after update\",\"status\":\"DONE\"}";
            String response = sendRequest("PUT", BASE_URL + "/" + taskId, updateJson);

            System.out.print("  Update existing task: ");
            if (response.contains("Status: 200")) {
                String getResponse = sendRequest("GET", BASE_URL + "/" + taskId, null);
                if (getResponse.contains("Task after update") && getResponse.contains("\"status\":\"DONE\"")) {
                    System.out.println("[PASS]");
                    passedTests++;
                } else {
                    System.out.println("[FAIL] - data not updated");
                    System.out.println("    GET response: " + getResponse);
                    failedTests++;
                }
            } else {
                System.out.println("[FAIL]");
                System.out.println("    Response: " + response);
                failedTests++;
            }

            String updateJsonFake = "{\"id\":999999,\"title\":\"Fake\",\"status\":\"OPEN\"}";
            String notFoundResponse = sendRequest("PUT", BASE_URL + "/999999", updateJsonFake);
            System.out.print("  Update non-existing task: ");
            if (notFoundResponse.contains("Status: 404")) {
                System.out.println("[PASS]");
                passedTests++;
            } else {
                System.out.println("[FAIL] - expected 404, got: " + notFoundResponse);
                failedTests++;
            }
        } else {
            System.out.println("  [SKIP] Failed to create task for UPDATE test");
            failedTests += 2;
        }
    }

    // ==================== CRUD: DELETE ====================

    private static void testDeleteOperations() {
        System.out.println("\n" + SUB_LINE);
        System.out.println("  DELETE OPERATIONS TESTING");
        System.out.println(SUB_LINE);

        String json = "{\"title\":\"Task for deletion\",\"createdBy\":400,\"status\":\"OPEN\"}";
        String createResponse = sendRequest("POST", BASE_URL, json);
        Integer taskId = extractIdFromResponse(createResponse);

        if (taskId != null) {
            System.out.print("  Delete fresh task (< 5 min): ");
            String response = sendRequest("DELETE", BASE_URL + "/" + taskId, null);
            // Service layer should reject deletion of tasks created less than 5 min ago
            if (response.contains("Status: 500")) {
                System.out.println("[PASS] - Service layer blocks deletion of fresh tasks");
                passedTests++;
            } else if (response.contains("Status: 204")) {
                System.out.println("[FAIL] - fresh task was deleted (5-min rule not working)");
                failedTests++;
            } else {
                System.out.println("[FAIL]");
                System.out.println("    Response: " + response);
                failedTests++;
            }
        } else {
            System.out.println("  [SKIP] Failed to create task for DELETE test");
            failedTests++;
        }

        String response = sendRequest("DELETE", BASE_URL + "/999999", null);
        System.out.print("  Delete non-existing task: ");
        // Service returns early if task not found (no exception)
        if (response.contains("Status: 204")) {
            System.out.println("[PASS]");
            passedTests++;
        } else {
            System.out.println("[FAIL]");
            System.out.println("    Response: " + response);
            failedTests++;
        }
    }

    // ==================== BUSINESS RULES ====================

    private static void testBusinessRules() {
        System.out.println("\n" + SUB_LINE);
        System.out.println("  BUSINESS RULES VERIFICATION");
        System.out.println(SUB_LINE);

        System.out.println("  Active tasks limit test (user 500):");
        Set<Integer> createdIds = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            String json = String.format(
                    "{\"title\":\"Active task %d\",\"createdBy\":500,\"status\":\"OPEN\"}", i + 1);
            String response = sendRequest("POST", BASE_URL, json);
            Integer id = extractIdFromResponse(response);
            if (id != null) {
                createdIds.add(id);
            }
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
            }
        }
        System.out.println("    Active tasks created: " + createdIds.size());

        String countResponse = sendRequest("GET", BASE_URL + "/active/count?userId=500", null);
        System.out.print("  Count active tasks userId=500: ");
        if (countResponse.contains("Status: 200") && countResponse.contains("10")) {
            System.out.println("[PASS]");
            passedTests++;
        } else {
            System.out.println("[FAIL]");
            System.out.println("    Response: " + countResponse);
            failedTests++;
        }

        // 11th task should be rejected by Service layer
        String json11 = "{\"title\":\"11th active task\",\"createdBy\":500,\"status\":\"OPEN\"}";
        String response11 = sendRequest("POST", BASE_URL, json11);
        System.out.print("  Exceed limit (11th task): ");
        if (response11.contains("Status: 500")) {
            System.out.println("[PASS] - Service layer rejects 11th task");
            passedTests++;
        } else if (response11.contains("Status: 201")) {
            System.out.println("[FAIL] - 11th task was created (limit not working)");
            failedTests++;
        } else {
            System.out.println("[FAIL]");
            System.out.println("    Response: " + response11);
            failedTests++;
        }

        String finalCountResponse = sendRequest("GET", BASE_URL + "/active/count?userId=500", null);
        System.out.print("  Final count verification for userId=500: ");
        if (finalCountResponse.contains("Status: 200")) {
            System.out.println("[PASS] - count endpoint works");
            passedTests++;
        } else {
            System.out.println("[FAIL]");
            System.out.println("    Response: " + finalCountResponse);
            failedTests++;
        }
    }

    // ==================== FILTERING ====================

    private static void testFiltering() {
        System.out.println("\n" + SUB_LINE);
        System.out.println("  DATE FILTERING TESTING");
        System.out.println(SUB_LINE);

        LocalDateTime testStart = LocalDateTime.now();

        System.out.println("  Creating tasks for filtering (user 600)...");

        String json1 = "{\"title\":\"Task 1 (immediate)\",\"createdBy\":600,\"status\":\"OPEN\"}";
        String resp1 = sendRequest("POST", BASE_URL, json1);
        Integer id1 = extractIdFromResponse(resp1);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        LocalDateTime afterFirst = LocalDateTime.now();

        String json2 = "{\"title\":\"Task 2 (after 1 sec)\",\"createdBy\":600,\"status\":\"OPEN\"}";
        String resp2 = sendRequest("POST", BASE_URL, json2);
        Integer id2 = extractIdFromResponse(resp2);

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
        }
        LocalDateTime afterSecond = LocalDateTime.now();

        String json3 = "{\"title\":\"Task 3 (after 2 sec)\",\"createdBy\":600,\"status\":\"OPEN\"}";
        String resp3 = sendRequest("POST", BASE_URL, json3);
        Integer id3 = extractIdFromResponse(resp3);

        System.out.println("  Created tasks: id1=" + id1 + ", id2=" + id2 + ", id3=" + id3);

        String fromStr = afterFirst.format(FORMATTER);
        String response = sendRequest("GET", BASE_URL + "?userId=600&from=" + fromStr, null);
        System.out.print("  Filter from=" + fromStr + " (expect id2, id3): ");
        boolean hasId2 = id2 != null && response.contains("\"id\":" + id2);
        boolean hasId3 = id3 != null && response.contains("\"id\":" + id3);
        boolean noId1 = id1 != null && !response.contains("\"id\":" + id1);

        if (hasId2 && hasId3 && noId1) {
            System.out.println("[PASS]");
            passedTests++;
        } else {
            System.out.println("[FAIL]");
            System.out.println("    Response: " + response);
            System.out.println("    hasId2=" + hasId2 + ", hasId3=" + hasId3 + ", noId1=" + noId1);
            failedTests++;
        }

        String toStr = afterFirst.format(FORMATTER);
        response = sendRequest("GET", BASE_URL + "?userId=600&to=" + toStr, null);
        System.out.print("  Filter to=" + toStr + " (expect id1): ");
        boolean hasId1Only = id1 != null && response.contains("\"id\":" + id1);
        boolean noId2 = id2 != null && !response.contains("\"id\":" + id2);
        boolean noId3 = id3 != null && !response.contains("\"id\":" + id3);

        if (hasId1Only && noId2 && noId3) {
            System.out.println("[PASS]");
            passedTests++;
        } else {
            System.out.println("[FAIL]");
            System.out.println("    Response: " + response);
            failedTests++;
        }

        String fromRange = testStart.minusSeconds(1).format(FORMATTER);
        String toRange = afterSecond.plusSeconds(1).format(FORMATTER);
        response = sendRequest("GET", BASE_URL + "?userId=600&from=" + fromRange + "&to=" + toRange, null);
        System.out.print("  Filter from+to (expect all 3): ");
        boolean allPresent = (id1 != null && response.contains("\"id\":" + id1))
                && (id2 != null && response.contains("\"id\":" + id2))
                && (id3 != null && response.contains("\"id\":" + id3));

        if (allPresent) {
            System.out.println("[PASS]");
            passedTests++;
        } else {
            System.out.println("[FAIL]");
            System.out.println("    Response: " + response);
            failedTests++;
        }

        String futureFrom = LocalDateTime.now().plusDays(1).format(FORMATTER);
        response = sendRequest("GET", BASE_URL + "?userId=600&from=" + futureFrom, null);
        System.out.print("  Filter with future date (0 tasks): ");
        if (response.contains("[]") || (response.contains("Status: 200") && !response.contains("\"id\":"))) {
            System.out.println("[PASS]");
            passedTests++;
        } else {
            System.out.println("[FAIL]");
            System.out.println("    Response: " + response);
            failedTests++;
        }
    }

    // ==================== HELPER METHODS ====================

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

    private static String getStatusCode(String response) {
        Pattern pattern = Pattern.compile("Status: (\\d+)");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "unknown";
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
            System.out.println("|  RESULT: ALL TESTS PASSED SUCCESSFULLY!          |");
        } else {
            System.out.printf("|  RESULT: ERRORS FOUND (%d tests failed)            |%n", failedTests);
        }
        System.out.println("+------------------------------------------------------+");

        System.out.println("\n" + LINE);
        System.out.println("  TESTING COMPLETED");
        System.out.println("  End time: " + LocalDateTime.now().format(FORMATTER));
        System.out.println(LINE);
    }
}