package de.dtfb.sportshub.backend.tracker;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Plain unit test (no Spring context, no mocking of this class) against a real local HTTP server —
 * verifies the actual request path GitHubIssueClient sends. A Mockito mock of this class (as used
 * in TrackerIssueAdminControllerSecurityTest) hides exactly this bug class: a single "{repo}" URI
 * template variable containing a slash gets percent-encoded as ONE path segment ("%2F") instead of
 * two, which 404s against the real GitHub API regardless of the token's actual permissions.
 */
class GitHubIssueClientTest {

    private static HttpServer server;
    private static final AtomicReference<String> capturedPath = new AtomicReference<>();

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            capturedPath.set(exchange.getRequestURI().getPath());
            byte[] responseBody = "{\"html_url\":\"https://github.com/acme/proj/issues/1\"}"
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @Test
    void createIssue_sendsTwoSeparatePathSegments_notOneSlashEncodedSegment() {
        GitHubIssueClient client = new GitHubIssueClient("http://localhost:" + server.getAddress().getPort());

        String url = client.createIssue("acme/proj", "Title", "Body", "token");

        assertEquals("/repos/acme/proj/issues", capturedPath.get());
        assertEquals("https://github.com/acme/proj/issues/1", url);
    }

    @Test
    void createIssue_rejectsRepoWithoutOwnerSlashName() {
        GitHubIssueClient client = new GitHubIssueClient("http://localhost:" + server.getAddress().getPort());

        try {
            client.createIssue("not-owner-slash-name", "Title", "Body", "token");
            throw new AssertionError("expected a rejection for a malformed repo");
        } catch (Exception ex) {
            assertEquals("400 BAD_REQUEST \"Repository must be in \"owner/repo\" form, got: not-owner-slash-name\"",
                ex.getMessage());
        }
    }
}
