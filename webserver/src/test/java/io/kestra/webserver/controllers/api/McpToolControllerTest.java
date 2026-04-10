package io.kestra.webserver.controllers.api;

import io.kestra.core.models.mcp.Mcp;
import io.kestra.core.repositories.McpRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@io.kestra.core.junit.annotations.KestraTest
class McpToolControllerTest {

}