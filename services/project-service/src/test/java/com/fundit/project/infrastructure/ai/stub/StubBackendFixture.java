package com.fundit.project.infrastructure.ai.stub;

import com.fundit.project.application.ai.FundingStoryAiContracts.*;
import com.fundit.project.application.ai.FundingStoryService;
import com.fundit.project.application.media.MediaStorageClient;
import com.fundit.project.application.project.ProjectIndexEventPublisher;
import com.fundit.project.application.project.SellerProfileClient;
import com.fundit.project.domain.aifundingstory.FundingStorySession;
import com.fundit.project.domain.aifundingstory.FundingStorySessionRepository;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real HTTP/PNG transport and real BE completion logic; persistence/storage metadata are in memory. */
final class StubBackendFixture implements AutoCloseable {
    final UUID projectId = UUID.randomUUID();
    final Project project = Project.builder().id(1L).publicId(projectId).sellerId(UUID.randomUUID())
            .status(ProjectStatus.DRAFT).title("QA").createdAt(Instant.now()).updatedAt(Instant.now()).build();
    final Map<String, byte[]> uploads = new ConcurrentHashMap<>();
    final List<byte[]> callbacks = new CopyOnWriteArrayList<>();
    final CompletableFuture<RunCompletionRequest> completed = new CompletableFuture<>();
    final FundingStorySessionRepository runs = mock(FundingStorySessionRepository.class);
    final JsonMapper json = JsonMapper.builder().build();
    final HttpServer server;
    final int initialStatus;
    final FundingStoryService service;

    StubBackendFixture(int initialStatus) throws IOException {
        this.initialStatus = initialStatus;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ProjectRepository projects = mock(ProjectRepository.class);
        when(projects.findByPublicId(projectId)).thenReturn(Optional.of(project));
        when(projects.save(project)).thenReturn(project);
        when(runs.save(any())).thenAnswer(call -> call.getArgument(0));
        MediaStorageClient storage = mock(MediaStorageClient.class);
        when(storage.extractKey(any())).thenAnswer(call -> {
            String url = call.getArgument(0);
            return url.startsWith(base() + "/") ? Optional.of(url.substring(base().length() + 1)) : Optional.empty();
        });
        when(storage.headObject(any())).thenAnswer(call -> {
            byte[] bytes = uploads.get(call.getArgument(0));
            return bytes == null ? Optional.empty() : Optional.of(new MediaStorageClient.StoredObject(bytes.length, "image/png"));
        });
        service = new FundingStoryService(projects, null, runs, null, null, storage,
                mock(ProjectIndexEventPublisher.class), mock(SellerProfileClient.class));
        server.createContext("/", exchange -> {
            try (exchange) {
                try {
                    String path = exchange.getRequestURI().getPath();
                    byte[] bytes = exchange.getRequestBody().readAllBytes();
                    Object response;
                    if (path.startsWith("/projects/")) {
                        assertThat(exchange.getRequestMethod()).isEqualTo("PUT");
                        assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).isEqualTo("image/png");
                        var png = ImageIO.read(new ByteArrayInputStream(bytes));
                        assertThat(png.getWidth()).isEqualTo(860);
                        assertThat(png.getHeight()).isEqualTo(320);
                        uploads.put(path.substring(1), bytes);
                        response = Map.of();
                    } else {
                        assertThat(exchange.getRequestHeaders().getFirst("X-Internal-Api-Key")).isEqualTo("qa-key");
                        assertThat(exchange.getRequestHeaders().getFirst("X-Project-Id")).isEqualTo(projectId.toString());
                        if (path.equals("/internal/ai/media/upload-targets")) {
                            var request = json.readValue(bytes, UploadTargetsRequest.class);
                            response = new UploadTargetsResponse(request.outputs().stream().map(output -> {
                                String url = base() + "/projects/" + projectId + "/ai/" + output.slot_id() + ".png";
                                return new UploadTarget(output.slot_id(), url, url, Instant.now().plusSeconds(60));
                            }).toList());
                        } else {
                            callbacks.add(bytes);
                            if (initialStatus != 200 && callbacks.size() == 1) {
                                exchange.sendResponseHeaders(initialStatus, -1);
                                return;
                            }
                            var request = json.readValue(bytes, RunCompletionRequest.class);
                            UUID runId = UUID.fromString(path.split("/")[4]);
                            when(runs.findById(runId)).thenReturn(Optional.of(FundingStorySession.trackRun(
                                    runId, project.getId(), project.getSellerId(), UUID.randomUUID())));
                            response = service.completeRun(projectId, runId, request);
                            completed.complete(request);
                        }
                    }
                    byte[] body = json.writeValueAsBytes(response);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                } catch (Throwable error) {
                    completed.completeExceptionally(error);
                    exchange.sendResponseHeaders(500, -1);
                }
            }
        });
        server.start();
    }

    String base() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    @Override public void close() { server.stop(0); }
}
