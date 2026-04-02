package io.kestra.mcp;

import  io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@KestraTest
class McpToolServiceTest {
    @Inject
    McpToolService mcpToolService;

    @Test
    void givenFlowWithMcpToolTrigger_whenListToolSpecsForServer_thenReturnToolSpecForFlow() {
        // Given

        // When

        // Then
    }

    @Test
    void givenFlowWithoutMcpToolTrigger_whenListToolSpecsForServer_thenNoToolSpecsReturned() {
        // Given

        // When

        // Then
    }

    @Test
    void givenFlowWithMultipleMcpToolTrigger_whenListToolSpecsForServer_thenToolSpecsReturnedForEachTrigger() {
        // Given

        // When

        // Then
    }


    @Test
    void givenMultipleFlowsWithMcpToolTrigger_whenListToolSpecsForServer_thenToolSpecsReturnedForEachFlow() {
        // Given

        // When

        // Then
    }

    @Test
    void givenDeletedFlowWithMcpToolTrigger_whenListToolSpecsForServer_thenNoToolSpecsReturned() {
        // Given

        // When

        // Then
    }

    @Test
    void givenFlowWithDisabledMcpToolTrigger_whenListToolSpecsForServer_thenNoToolSpecsReturned() {
        // Given

        // When

        // Then
    }

    @Test
    void givenFlowsWithoutTrigger_whenListToolSpecsForServer_thenNoToolSpecsReturned() {
        // Given

        // When

        // Then
    }
}