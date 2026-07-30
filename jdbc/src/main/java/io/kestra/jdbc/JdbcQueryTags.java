package io.kestra.jdbc;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.jooq.DDLQuery;
import org.jooq.Delete;
import org.jooq.ExecuteContext;
import org.jooq.Insert;
import org.jooq.Merge;
import org.jooq.Query;
import org.jooq.ResultQuery;
import org.jooq.Select;
import org.jooq.Update;

import io.kestra.core.utils.ListUtils;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Derives bounded, dialect-independent {@code type}/{@code table} tags for JDBC query metrics.
 * <p>
 * {@code type} is read off the jOOQ {@link Query} object itself, never parsed from SQL text.
 * {@code table} is recovered from the rendered SQL but validated against the tables configured in
 * {@link JdbcTableConfigs}: a parsed identifier is only ever returned if it is a known table, so the
 * tag's cardinality is bounded by the number of configured tables regardless of parsing edge cases
 * (aliases, derived tables, identifier casing/quoting differ per dialect).
 */
@Singleton
public class JdbcQueryTags {
    private static final String UNKNOWN = "<unknown>";

    private final Map<String, String> tableNamesByLowerCase;

    @Inject
    public JdbcQueryTags(JdbcTableConfigs tableConfigs) {
        this.tableNamesByLowerCase = ListUtils.emptyOnNull(tableConfigs.getTableConfigs())
            .stream()
            .collect(Collectors.toMap(
                config -> config.table().toLowerCase(Locale.ROOT),
                JdbcTableConfig::table,
                (first, second) -> first
            ));
    }

    /**
     * Builds the {@code batch}/{@code type}/{@code table} tags for a completed jOOQ execution.
     *
     * @param ctx the execution context, at any listener callback, including one that fires before
     *            the query ever reaches {@code executeStart} (e.g. a rendering/bind failure)
     * @return an even-length tag array suitable for {@code MetricRegistry.timer(...)}
     */
    public String[] tags(ExecuteContext ctx) {
        QueryType type = resolveType(resolveQuery(ctx));

        return new String[] {
            "batch", ctx.batchMode().name(),
            "type", type.name(),
            "table", findTable(ctx.sql(), type)
        };
    }

    /**
     * Resolves the query type from the jOOQ query object itself, giving a fixed, closed set of
     * values with no string parsing involved.
     */
    QueryType resolveType(Query query) {
        return switch (query) {
            case null -> QueryType.UNKNOWN;
            case Select<?> _ -> QueryType.SELECT;
            case Insert<?> _ -> QueryType.INSERT;
            case Update<?> _ -> QueryType.UPDATE;
            case Delete<?> _ -> QueryType.DELETE;
            case Merge<?> _ -> QueryType.MERGE;
            case DDLQuery _ -> QueryType.DDL;
            case ResultQuery<?> _ -> QueryType.SELECT; // plain-SQL SELECT; must stay after Select
            default -> QueryType.OTHER;
        };
    }

    private Query resolveQuery(ExecuteContext ctx) {
        if (ctx.batchMode() == ExecuteContext.BatchMode.NONE) {
            return ctx.query();
        }

        Query[] batchQueries = ctx.batchQueries();
        return batchQueries.length > 0 ? batchQueries[0] : null;
    }

    /**
     * Scans the rendered SQL for the table name following the query type's keyword, only accepting
     * a candidate that matches a configured table. A candidate that does not match (a derived-table
     * alias, an empty token before a sub-select's opening parenthesis, ...) is skipped in favor of
     * the next occurrence of the keyword, rather than failing the whole lookup, so joins and derived
     * tables still resolve to their driving/underlying table.
     */
    String findTable(String sql, QueryType type) {
        String keyword = type.tableKeyword();
        if (keyword == null || sql == null) {
            return UNKNOWN;
        }

        int from = 0;
        int keywordIndex;
        while ((keywordIndex = indexOfKeyword(sql, keyword, from)) != -1) {
            String identifier = extractIdentifier(sql, keywordIndex + keyword.length());
            String canonical = identifier.isEmpty() ? null : tableNamesByLowerCase.get(identifier.toLowerCase(Locale.ROOT));
            if (canonical != null) {
                return canonical;
            }

            from = keywordIndex + keyword.length();
        }

        return UNKNOWN;
    }

    // finds the keyword as a whole word (not a substring of a longer identifier), case-insensitive
    // and independent of surrounding whitespace, so it matches both "\nFROM " (formatted rendering)
    // and " from " alike without lowercasing or copying the SQL string
    private int indexOfKeyword(String sql, String keyword, int from) {
        int max = sql.length() - keyword.length();
        for (int i = from; i <= max; i++) {
            if (sql.regionMatches(true, i, keyword, 0, keyword.length())
                && (i == 0 || !Character.isLetterOrDigit(sql.charAt(i - 1)))
                && (i + keyword.length() >= sql.length() || !Character.isLetterOrDigit(sql.charAt(i + keyword.length())))) {
                return i;
            }
        }

        return -1;
    }

    private String extractIdentifier(String sql, int from) {
        int start = from;
        while (start < sql.length() && Character.isWhitespace(sql.charAt(start))) {
            start++;
        }

        int end = start;
        while (end < sql.length() && !isIdentifierDelimiter(sql.charAt(end))) {
            end++;
        }

        return stripQuotes(sql.substring(start, end));
    }

    private boolean isIdentifierDelimiter(char c) {
        return Character.isWhitespace(c) || c == '(' || c == ')' || c == ',' || c == ';';
    }

    private String stripQuotes(String identifier) {
        if (identifier.length() < 2) {
            return identifier;
        }

        char first = identifier.charAt(0);
        char last = identifier.charAt(identifier.length() - 1);
        if ((first == '"' && last == '"') || (first == '`' && last == '`')) {
            return identifier.substring(1, identifier.length() - 1);
        }

        return identifier;
    }

    /**
     * The kind of SQL statement a query renders to, used as a bounded {@code type} metric tag.
     */
    public enum QueryType {
        SELECT("from"),
        INSERT("into"),
        UPDATE("update"),
        DELETE("from"),
        MERGE("into"),
        DDL(null),
        OTHER(null),
        UNKNOWN(null);

        private final String tableKeyword;

        QueryType(String tableKeyword) {
            this.tableKeyword = tableKeyword;
        }

        /**
         * @return the SQL keyword the table name follows for this query type, or {@code null} if
         *         this type has no meaningful single table (DDL, unrecognized, unknown).
         */
        public String tableKeyword() {
            return tableKeyword;
        }
    }
}
