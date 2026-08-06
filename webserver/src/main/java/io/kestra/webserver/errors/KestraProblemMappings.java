package io.kestra.webserver.errors;

import java.io.FileNotFoundException;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;

import io.kestra.core.exceptions.AlreadyExistsException;
import io.kestra.core.exceptions.ConflictException;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.exceptions.FlowBlockedException;
import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.exceptions.InputOutputValidationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.exceptions.InvalidException;
import io.kestra.core.exceptions.InvalidQueryFiltersException;
import io.kestra.core.exceptions.InvalidTriggerConfigurationException;
import io.kestra.core.exceptions.KilledException;
import io.kestra.core.exceptions.MigrationRequiredException;
import io.kestra.core.exceptions.NoMatchingWorkerQueueException;
import io.kestra.core.exceptions.NotFoundException;
import io.kestra.core.exceptions.ResourceAccessDeniedException;
import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.exceptions.TimeoutExceededException;
import io.kestra.core.exceptions.TypeConversionException;
import io.kestra.core.exceptions.ValidationErrorException;
import io.kestra.webserver.exceptions.BulkValidationException;
import io.kestra.core.lock.LockException;
import io.kestra.core.migration.MigrationLockedException;
import io.kestra.core.migration.MigrationPendingException;
import io.kestra.core.secret.SecretException;
import io.kestra.core.secret.SecretNotFoundException;
import io.kestra.core.storages.kv.KVStoreException;
import io.kestra.libs.copilot.exceptions.AiException;
import io.kestra.plugin.core.trigger.WebhookInputRenderException;
import io.kestra.webserver.services.ai.agent.tool.ToolPermissionDeniedException;

import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.web.router.exceptions.UnsatisfiedBodyRouteException;
import io.micronaut.web.router.exceptions.UnsatisfiedQueryValueRouteException;
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;

/**
 * The Open Source exception to {@link ProblemType} table.
 *
 * <p>{@code IllegalStateException} is deliberately absent: it is a JDK programming-error signal, not a domain
 * failure, so mapping it would present any accidental illegal state anywhere in the stack as a client error.
 * Throw sites that genuinely mean a conflict or an unprocessable entity raise {@link ConflictException} or
 * {@link InvalidException}; the rest report a server error.
 *
 * @see ProblemTypes the catalog of types referenced here
 */
@Singleton
public class KestraProblemMappings extends ExceptionTypeProblemMapper {
    @Override
    protected void register(final BiConsumer<Class<? extends Throwable>, ProblemType> to) {
        // Unreadable or undecodable request payload.
        to.accept(JsonParseException.class, ProblemTypes.INVALID_JSON);
        to.accept(InvalidTypeIdException.class, ProblemTypes.INVALID_PLUGIN_TYPE);
        to.accept(InvalidFormatException.class, ProblemTypes.INVALID_FORMAT);
        to.accept(JsonMappingException.class, ProblemTypes.INVALID_JSON_MAPPING);
        to.accept(ConversionErrorException.class, ProblemTypes.INVALID_ARGUMENT);
        to.accept(DeserializationException.class, ProblemTypes.INTERNAL_ERROR);

        // Request could not be bound to the route.
        to.accept(UnsatisfiedBodyRouteException.class, ProblemTypes.INVALID_REQUEST_BODY);
        to.accept(UnsatisfiedQueryValueRouteException.class, ProblemTypes.INVALID_QUERY_PARAMETER);
        to.accept(UnsatisfiedRouteException.class, ProblemTypes.BAD_REQUEST);

        // Entity validation.
        to.accept(ConstraintViolationException.class, ProblemTypes.VALIDATION_FAILED);
        to.accept(BulkValidationException.class, ProblemTypes.BULK_VALIDATION_FAILED);
        to.accept(ValidationErrorException.class, ProblemTypes.RESOURCE_VALIDATION_FAILED);
        to.accept(InputOutputValidationException.class, ProblemTypes.INVALID_ENTITY);
        to.accept(InvalidException.class, ProblemTypes.INVALID_ENTITY);
        to.accept(InvalidQueryFiltersException.class, ProblemTypes.INVALID_QUERY_FILTERS);
        to.accept(InvalidTriggerConfigurationException.class, ProblemTypes.INVALID_ENTITY);
        to.accept(IllegalArgumentException.class, ProblemTypes.INVALID_ARGUMENT);
        to.accept(TypeConversionException.class, ProblemTypes.INVALID_FORMAT);
        to.accept(KVStoreException.class, ProblemTypes.INVALID_ENTITY);
        to.accept(NoMatchingWorkerQueueException.class, ProblemTypes.INVALID_ENTITY);
        to.accept(WebhookInputRenderException.class, ProblemTypes.INVALID_REQUEST_BODY);

        // Authorization denials. 403 rather than a server error, so they are not recorded as incidents and
        // clients do not retry them.
        to.accept(ResourceAccessDeniedException.class, ProblemTypes.FORBIDDEN);
        to.accept(SecurityException.class, ProblemTypes.FORBIDDEN);
        to.accept(ToolPermissionDeniedException.class, ProblemTypes.FORBIDDEN);

        // Resource state.
        to.accept(NotFoundException.class, ProblemTypes.NOT_FOUND);
        to.accept(NoSuchElementException.class, ProblemTypes.NOT_FOUND);
        to.accept(FileNotFoundException.class, ProblemTypes.NOT_FOUND);
        to.accept(SecretNotFoundException.class, ProblemTypes.NOT_FOUND);
        to.accept(AlreadyExistsException.class, ProblemTypes.ENTITY_ALREADY_EXISTS);
        to.accept(ConflictException.class, ProblemTypes.CONFLICT);
        to.accept(FlowBlockedException.class, ProblemTypes.CONFLICT);
        to.accept(KilledException.class, ProblemTypes.CONFLICT);
        to.accept(LockException.class, ProblemTypes.CONFLICT);
        to.accept(ResourceExpiredException.class, ProblemTypes.RESOURCE_EXPIRED);

        // AI Copilot.
        to.accept(AiException.class, ProblemTypes.AI_REQUEST_FAILED);

        // Server-side conditions that are still worth distinguishing from a generic failure.
        to.accept(MigrationRequiredException.class, ProblemTypes.MIGRATION_REQUIRED);
        to.accept(MigrationPendingException.class, ProblemTypes.MIGRATION_REQUIRED);
        to.accept(MigrationLockedException.class, ProblemTypes.MIGRATION_REQUIRED);
        to.accept(TimeoutExceededException.class, ProblemTypes.TIMEOUT);
        to.accept(SecretException.class, ProblemTypes.INTERNAL_ERROR);
        to.accept(InternalException.class, ProblemTypes.INTERNAL_ERROR);
        to.accept(FlowProcessingException.class, ProblemTypes.INTERNAL_ERROR);
    }
}
