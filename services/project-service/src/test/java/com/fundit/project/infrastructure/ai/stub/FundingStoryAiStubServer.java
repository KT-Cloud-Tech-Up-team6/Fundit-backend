package com.fundit.project.infrastructure.ai.stub;

import com.fundit.project.application.ai.FundingStoryAiContracts.*;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.ErrorResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Explicitly launched local QA fixture, never a Spring bean or production dependency. */
public final class FundingStoryAiStubServer implements AutoCloseable {
    private static final String PREFIX = "/api/v1/ai";
    private static final int MAX_REQUEST_BYTES = 1_048_576;
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final HttpServer server;
    private final ScheduledExecutorService callbacks = Executors.newSingleThreadScheduledExecutor();
    private final URI backend;
    private final String token;
    private final String internalKey;
    private final String result;
    private final long delayMs;
    private final Map<UUID, Session> sessions = new LinkedHashMap<>();
    private final Map<UUID, Chat> chats = new HashMap<>();
    private final Map<String, Run> runs = new HashMap<>();
    private final Map<UUID, CompletableFuture<Void>> deliveries = new ConcurrentHashMap<>();

    public FundingStoryAiStubServer(int port, URI backend, String token, String internalKey,
                                    String result, long delayMs) throws IOException {
        if (!"http".equals(backend.getScheme())
                || !Set.of("127.0.0.1", "localhost").contains(backend.getHost())
                || backend.getUserInfo() != null || backend.getQuery() != null
                || backend.getFragment() != null || !Set.of("", "/").contains(backend.getPath())) {
            throw new IllegalArgumentException("Stub callbacks require a loopback HTTP backend origin");
        }
        if (token == null || token.isBlank() || internalKey == null || internalKey.isBlank()
                || !Set.of("succeeded", "partially_succeeded", "failed", "no_callback").contains(result)
                || delayMs < 0) {
            throw new IllegalArgumentException("Invalid local stub configuration");
        }
        this.backend = backend;
        this.token = token;
        this.internalKey = internalKey;
        this.result = result;
        this.delayMs = delayMs;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::handle);
    }

    public void start() { server.start(); }
    public String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    CompletableFuture<Void> delivery(UUID runId) { return deliveries.get(runId); }

    public static void main(String[] args) throws Exception {
        var env = System.getenv();
        var stub = new FundingStoryAiStubServer(
                Integer.parseInt(env.getOrDefault("FUNDING_STORY_STUB_PORT", "8000")),
                URI.create(env.getOrDefault("FUNDING_STORY_STUB_BE_URL", "http://127.0.0.1:8083")),
                env.get("FUNDING_STORY_AI_SERVICE_TOKEN"), env.get("INTERNAL_API_KEY"),
                env.getOrDefault("FUNDING_STORY_STUB_RESULT", "succeeded"),
                Long.parseLong(env.getOrDefault("FUNDING_STORY_STUB_DELAY_MS", "1500")));
        Runtime.getRuntime().addShutdownHook(new Thread(stub::close));
        stub.start();
        System.out.println("LOCAL QA ONLY: Funding Story AI stub " + stub.baseUrl());
    }

    private synchronized void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            try {
                require(("Bearer " + token).equals(exchange.getRequestHeaders().getFirst("Authorization")), 401);
                UUID project = UUID.fromString(exchange.getRequestHeaders().getFirst("X-Project-Id"));
                route(exchange, project);
            } catch (StubError error) {
                CommonErrorCode code = switch (error.status) {
                    case 401 -> CommonErrorCode.UNAUTHORIZED;
                    case 404 -> CommonErrorCode.NOT_FOUND;
                    case 409 -> CommonErrorCode.CONFLICT;
                    case 422 -> CommonErrorCode.BUSINESS_RULE_VIOLATION;
                    default -> CommonErrorCode.INVALID_INPUT;
                };
                reply(exchange, code.getHttpStatus(), ErrorResponse.of(code));
            } catch (IllegalArgumentException | NullPointerException error) {
                reply(exchange, 400, ErrorResponse.of(CommonErrorCode.INVALID_INPUT));
            } catch (RuntimeException error) {
                reply(exchange, 500, ErrorResponse.of(CommonErrorCode.INTERNAL_ERROR));
            }
        }
    }

    private void route(HttpExchange exchange, UUID project) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        if (path.equals(PREFIX + "/sessions") && method.equals("POST")) {
            var request = read(exchange, SessionCreateRequest.class);
            require(request.context() != null && request.context().project() != null, 400);
            Session session = new Session(project, request.context());
            sessions.put(session.id, session);
            reply(exchange, 201, session.response());
        } else if (path.equals(PREFIX + "/sessions/latest") && method.equals("GET")) {
            Session latest = null;
            for (Session session : sessions.values()) if (session.project.equals(project)) latest = session;
            reply(exchange, 200, new LatestSessionResponse(latest == null ? null : latest.response()));
        } else if (path.startsWith(PREFIX + "/sessions/")) {
            String[] parts = path.substring((PREFIX + "/sessions/").length()).split("/");
            Session session = sessions.get(UUID.fromString(parts[0]));
            require(session != null && session.project.equals(project), 404);
            if (parts.length == 1 && method.equals("GET")) {
                reply(exchange, 200, session.response());
            } else if (parts.length == 2 && method.equals("POST")) {
                switch (parts[1]) {
                    case "start" -> {
                        require(session.initialChat != null || session.messages.isEmpty(), 409);
                        if (session.initialChat == null) session.initialChat = chat(session, null);
                        reply(exchange, 202, new ChatAcceptedResponse(session.initialChat, "queued"));
                    }
                    case "messages" -> {
                        var request = read(exchange, MessageRequest.class);
                        require(request.message_id() != null && !request.message_id().isBlank()
                                && request.text() != null && !request.text().isBlank(), 400);
                        Chat previous = session.messagesById.get(request.message_id());
                        if (previous != null) {
                            require(previous.request.equals(request), 409);
                            reply(exchange, 202, new ChatAcceptedResponse(previous.id, "queued"));
                        } else {
                            require(request.revision() == session.revision, 409);
                            UUID id = chat(session, request);
                            session.messagesById.put(request.message_id(), chats.get(id));
                            reply(exchange, 202, new ChatAcceptedResponse(id, "queued"));
                        }
                    }
                    case "confirm" -> {
                        var request = read(exchange, ConfirmRequest.class);
                        require(request.revision() == session.revision, 409);
                        require(session.ready, 422);
                        session.confirmed = session.revision;
                        reply(exchange, 200, new ConfirmResponse(session.id, session.confirmed));
                    }
                    default -> throw new StubError(404);
                }
            } else throw new StubError(404);
        } else if (path.matches(PREFIX + "/chats/[^/]+/events") && method.equals("GET")) {
            UUID id = UUID.fromString(path.substring((PREFIX + "/chats/").length()).split("/")[0]);
            Chat chat = chats.get(id);
            require(chat != null && chat.project.equals(project), 404);
            byte[] bytes = ("event: message\ndata: " + json.writeValueAsString(Map.of("chat_id", id, "text", chat.text))
                    + "\n\nevent: done\ndata: " + json.writeValueAsString(new Done(id, "succeeded", chat.session,
                    chat.revision, null)) + "\n\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        } else if (path.equals(PREFIX + "/runs") && method.equals("POST")) {
            var request = read(exchange, RunCreateRequest.class);
            Session session = sessions.get(request.session_id());
            require(session != null && session.project.equals(project), 404);
            require(request.idempotency_key() != null && !request.idempotency_key().isBlank(), 400);
            String key = project + ":" + session.id + ":" + request.idempotency_key();
            require(session.confirmed != null && request.confirmed_revision() == session.confirmed
                    && session.revision == session.confirmed, 409);
            Run previous = runs.get(key);
            boolean created = previous == null;
            require(created || previous.revision == request.confirmed_revision(), 409);
            RunAcceptedResponse response;
            if (created) {
                response = new RunAcceptedResponse(UUID.randomUUID(), "queued");
                runs.put(key, new Run(request.confirmed_revision(), response));
                deliveries.put(response.run_id(), new CompletableFuture<>());
            } else response = previous.response;
            reply(exchange, 202, response);
            if (created && !result.equals("no_callback")) {
                UUID runId = response.run_id();
                callbacks.schedule(() -> complete(project, runId), delayMs, TimeUnit.MILLISECONDS);
            }
        } else throw new StubError(404);
    }

    private UUID chat(Session session, MessageRequest request) {
        boolean initial = request == null;
        String text = initial ? "[STUB] 제품을 어떤 상황에서 사용하나요?"
                : "[STUB] 제품 소개와 사용 장면을 정리했습니다. 요약을 확인해 주세요.";
        if (!initial) session.messages.add(new ChatMessage("user", request.text()));
        session.messages.add(new ChatMessage("assistant", text));
        session.revision++;
        session.confirmed = null;
        session.ready = !initial;
        UUID id = UUID.randomUUID();
        chats.put(id, new Chat(id, session.project, session.id, session.revision, text, request));
        return id;
    }

    private void complete(UUID project, UUID runId) {
        try {
            AsyncError error = new AsyncError("GENERATION_FAILED", "[STUB] 생성 실패 시나리오", true, null);
            RunCompletionRequest body;
            if (result.equals("failed")) {
                body = new RunCompletionRequest(result, null, List.of(), List.of(), error);
            } else {
                byte[] png = png();
                List<String> slots = result.equals("succeeded") ? List.of("hero", "benefit") : List.of("hero");
                var outputs = slots.stream().map(slot -> new OutputDescriptor(slot, slot + ".png", "image/png", png.length)).toList();
                byte[] targetBody = json.writeValueAsBytes(new UploadTargetsRequest(outputs));
                var targets = json.readValue(send(project, "/internal/ai/media/upload-targets", targetBody), UploadTargetsResponse.class);
                require(targets.targets().size() == slots.size()
                        && new HashSet<>(targets.targets().stream().map(UploadTarget::slot_id).toList()).equals(new HashSet<>(slots)), 502);
                List<SuccessfulImage> images = new ArrayList<>();
                for (UploadTarget target : targets.targets()) {
                    HttpRequest put = HttpRequest.newBuilder(URI.create(target.upload_url())).timeout(Duration.ofSeconds(15))
                            .header("Content-Type", "image/png").PUT(HttpRequest.BodyPublishers.ofByteArray(png)).build();
                    var uploaded = http.send(put, HttpResponse.BodyHandlers.discarding());
                    require(uploaded.statusCode() >= 200 && uploaded.statusCode() < 300, 502);
                    images.add(new SuccessfulImage(target.slot_id(), target.file_url(), "image/png", png.length, 860, 320));
                }
                var generated = new GeneratedBody("hero", slots.stream()
                        .map(slot -> new GeneratedContentBlock("IMAGE", null, slot)).toList());
                var failures = result.equals("partially_succeeded")
                        ? List.of(new FailedSlot("benefit", "generation", error)) : List.<FailedSlot>of();
                body = new RunCompletionRequest(result, generated, images, failures, null);
            }
            // Serialize once. Lost responses must never turn a success into a different failure callback.
            byte[] payload = json.writeValueAsBytes(body);
            send(project, "/internal/ai/runs/" + runId + "/completion", payload);
            System.out.println("[STUB] callback acknowledged: " + runId + " " + result);
            deliveries.get(runId).complete(null);
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            System.err.println("[STUB] delivery failed: " + runId + " " + error.getClass().getSimpleName());
            deliveries.get(runId).completeExceptionally(error);
        }
    }

    private byte[] send(UUID project, String path, byte[] payload) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                var request = HttpRequest.newBuilder(backend.resolve(path)).timeout(Duration.ofSeconds(10))
                        .header("X-Internal-Api-Key", internalKey).header("X-Project-Id", project.toString())
                        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(payload)).build();
                var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();
                if (status >= 200 && status < 300) return response.body();
                // BE may still be committing the newly accepted run. Never retry a 409 conflict.
                if (!Set.of(404, 429, 500, 502, 503, 504).contains(status) || attempt == 3) {
                    throw new StubError(status);
                }
            } catch (IOException error) {
                if (attempt == 3) throw error;
            }
            Thread.sleep(250L * (1L << attempt));
        }
        throw new IOException("Callback retries exhausted");
    }

    private static byte[] png() throws IOException {
        var image = new BufferedImage(860, 320, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(new Color(235, 242, 255));
        graphics.fillRect(0, 0, 860, 320);
        graphics.setColor(Color.DARK_GRAY);
        graphics.drawString("FUNDING STORY - LOCAL STUB / NOT AI GENERATED", 40, 160);
        graphics.dispose();
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private <T> T read(HttpExchange exchange, Class<T> type) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        require(bytes.length <= MAX_REQUEST_BYTES, 400);
        try { return json.readValue(bytes, type); }
        catch (RuntimeException error) { throw new StubError(400); }
    }

    private void reply(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] bytes = json.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void require(boolean condition, int status) { if (!condition) throw new StubError(status); }
    private static final class StubError extends RuntimeException {
        final int status;
        StubError(int status) { this.status = status; }
    }
    private record Done(UUID chat_id, String status, UUID session_id, int revision, Object error) { }
    private record Run(int revision, RunAcceptedResponse response) { }
    private record Chat(UUID id, UUID project, UUID session, int revision, String text, MessageRequest request) { }
    private static final class Session {
        final UUID id = UUID.randomUUID();
        final UUID project;
        final FundingStoryContext context;
        final List<ChatMessage> messages = new ArrayList<>();
        final Map<String, Chat> messagesById = new HashMap<>();
        int revision = 1;
        Integer confirmed;
        UUID initialChat;
        boolean ready;
        Session(UUID project, FundingStoryContext context) { this.project = project; this.context = context; }
        SessionResponse response() {
            var summary = ready ? new StorySummary(context.project().title(), "[STUB] 테스트 스토리",
                    List.of(new StrengthSummary("테스트 강점 1", "실제 AI 분석이 아닙니다."),
                            new StrengthSummary("테스트 강점 2", "실제 AI 분석이 아닙니다."),
                            new StrengthSummary("테스트 강점 3", "실제 AI 분석이 아닙니다."))) : null;
            return new SessionResponse(id, revision, confirmed, List.copyOf(messages),
                    ready ? List.of() : List.of("story"), summary, null);
        }
    }

    @Override public void close() {
        callbacks.shutdownNow();
        server.stop(0);
        http.close();
    }
}
