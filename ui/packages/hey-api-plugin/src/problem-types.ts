/**
 * The problem types the UI branches on.
 *
 * Only types with a behavioural consequence belong here — a branch, a redirect, a specific dialog. Types
 * that are merely displayed need no constant: the toast renders `title` and `detail` generically.
 *
 * These mirror the backend catalog (`io.kestra.core.models.errors.ProblemTypes` and its Enterprise
 * counterpart). Keep them in sync when adding a branch: a slug absent server-side silently stops matching,
 * which is why every constant here is exercised by a unit test.
 */
export const ProblemTypes = {
    /** Creating an entity whose id is already taken. Drives create-then-update fallbacks. */
    ENTITY_ALREADY_EXISTS: "entity-already-exists",
    ENTITY_NOT_FOUND: "not-found",
    VALIDATION_FAILED: "validation-failed",
    BULK_VALIDATION_FAILED: "bulk-validation-failed",
    FORBIDDEN: "forbidden",
    UNAUTHENTICATED: "unauthenticated",
    CONFLICT: "conflict",
    TOO_MANY_REQUESTS: "too-many-requests",
    SERVICE_UNAVAILABLE: "service-unavailable",
    INTERNAL_ERROR: "internal-error",
} as const

export type ProblemType = (typeof ProblemTypes)[keyof typeof ProblemTypes]
