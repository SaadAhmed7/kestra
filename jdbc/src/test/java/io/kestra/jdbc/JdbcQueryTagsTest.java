package io.kestra.jdbc;

import java.util.List;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderKeywordCase;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.kestra.jdbc.JdbcQueryTags.QueryType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcQueryTagsTest {
    private static final Settings SETTINGS = new Settings()
        .withRenderKeywordCase(RenderKeywordCase.UPPER)
        .withRenderFormatted(true);

    @Mock
    private JdbcTableConfigs tableConfigs;

    @Test
    void shouldReturnSelectTypeWhenSqlIsRenderedFormatted() {
        // Given
        // jOOQ's formatted rendering (JooqSettings#withRenderFormatted) puts a newline right after
        // the keyword for a multi-column/aggregate select ("SELECT\n  count(*),\n  state_current\n
        // FROM ..."); a naive "first word before the first space" parse used to yield "SELECT\n".
        var query = dsl().select(DSL.count(), field("state_current")).from(table("executions"));

        // When
        QueryType type = queryTags().resolveType(query);

        // Then
        assertThat(type).isEqualTo(QueryType.SELECT);
    }

    @Test
    void shouldReturnCanonicalTableNameWhenIdentifierCaseDiffers() {
        // Given
        givenTableConfigs(new JdbcTableConfig("executions", null, "executions"));
        JdbcQueryTags queryTags = queryTags();

        // When / Then
        assertThat(queryTags.findTable("SELECT * FROM EXECUTIONS", QueryType.SELECT)).isEqualTo("executions");
        assertThat(queryTags.findTable("SELECT * FROM \"executions\"", QueryType.SELECT)).isEqualTo("executions");
        assertThat(queryTags.findTable("select * from executions", QueryType.SELECT)).isEqualTo("executions");
    }

    @Test
    void shouldSkipDerivedTableAliasWhenSelectingFromSubquery() {
        // Given
        givenTableConfigs(new JdbcTableConfig("executions", null, "executions"));
        String sql = "SELECT t.c\nFROM (\n  SELECT c\n  FROM executions\n) AS \"cte\"";

        // When
        String table = queryTags().findTable(sql, QueryType.SELECT);

        // Then
        assertThat(table).isEqualTo("executions");
    }

    @Test
    void shouldReturnDrivingTableWhenQueryJoins() {
        // Given
        givenTableConfigs(new JdbcTableConfig("flows", null, "flows"));
        String sql = "SELECT *\nFROM flows\nJOIN flows AS \"ft\" ON flows.id = ft.id";

        // When
        String table = queryTags().findTable(sql, QueryType.SELECT);

        // Then
        assertThat(table).isEqualTo("flows");
    }

    @Test
    void shouldReturnUnknownWhenTableIsNotConfigured() {
        // Given
        givenTableConfigs(new JdbcTableConfig("executions", null, "executions"));

        // When
        String table = queryTags().findTable("SELECT * FROM unknown_table", QueryType.SELECT);

        // Then
        assertThat(table).isEqualTo("<unknown>");
    }

    @Test
    void shouldReturnUnknownWhenTableConfigsAreEmpty() {
        // Given
        givenTableConfigs();

        // When
        String table = queryTags().findTable("SELECT * FROM executions", QueryType.SELECT);

        // Then
        assertThat(table).isEqualTo("<unknown>");
    }

    @Test
    void shouldReturnUnknownWhenQueryTypeHasNoTableKeyword() {
        // Given
        givenTableConfigs(new JdbcTableConfig("executions", null, "executions"));

        // When
        String table = queryTags().findTable("CREATE TABLE executions (id VARCHAR(255))", QueryType.DDL);

        // Then
        assertThat(table).isEqualTo("<unknown>");
    }

    @Test
    void shouldResolveTypeForEachQueryShape() {
        // Given
        DSLContext dsl = dsl();
        var table = table("queues");
        var value = field("value");
        JdbcQueryTags queryTags = queryTags();

        // When / Then
        // these facade objects (InsertImpl/UpdateImpl/DeleteImpl) are the same concrete classes
        // jOOQ hands back through ExecuteContext#batchQueries() in batch mode, so asserting on them
        // directly also covers the batch path, where the previous UpdateQuery/DeleteQuery-based
        // switch used to fall through to UNKNOWN
        assertThat(queryTags.resolveType(dsl.select(value).from(table))).isEqualTo(QueryType.SELECT);
        assertThat(queryTags.resolveType(dsl.insertInto(table).set(value, (Object) "v"))).isEqualTo(QueryType.INSERT);
        assertThat(queryTags.resolveType(dsl.update(table).set(value, (Object) "v"))).isEqualTo(QueryType.UPDATE);
        assertThat(queryTags.resolveType(dsl.deleteFrom(table))).isEqualTo(QueryType.DELETE);
        assertThat(queryTags.resolveType(dsl.createTableIfNotExists(table("tmp")).column(field("c", SQLDataType.VARCHAR)))).isEqualTo(QueryType.DDL);
        assertThat(queryTags.resolveType(dsl.resultQuery("SELECT 1"))).isEqualTo(QueryType.SELECT);
        assertThat(queryTags.resolveType(null)).isEqualTo(QueryType.UNKNOWN);
    }

    private void givenTableConfigs(JdbcTableConfig... configs) {
        when(tableConfigs.getTableConfigs()).thenReturn(List.of(configs));
    }

    private JdbcQueryTags queryTags() {
        return new JdbcQueryTags(tableConfigs);
    }

    private static DSLContext dsl() {
        return DSL.using(SQLDialect.H2, SETTINGS);
    }
}
