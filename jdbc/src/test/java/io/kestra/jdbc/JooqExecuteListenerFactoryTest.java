package io.kestra.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jooq.ExecuteListener;
import org.jooq.ExecuteListenerProvider;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.kestra.core.metrics.MetricConfig;
import io.kestra.core.metrics.MetricRegistry;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JooqExecuteListenerFactoryTest {
    @Mock
    private JdbcTableConfigs tableConfigs;

    @Test
    void shouldRecordSaneDurationWhenQueryFailsBeforeExecuteStart() throws Exception {
        // Given
        // jOOQ's start()/end() callback pair is the only one guaranteed to fire exactly once for
        // every execution, including one that fails during rendering/prepare/bind, before
        // executeStart()/executeEnd() ever run. Closing the connection up front forces exactly
        // that: the failure happens while binding, so executeStart() never fires but end() does.
        when(tableConfigs.getTableConfigs()).thenReturn(List.of());
        MetricRegistry metricRegistry = new MetricRegistry(new SimpleMeterRegistry(), new MetricConfig());
        ExecuteListenerProvider provider = new JooqExecuteListenerFactory()
            .jooqConfiguration(metricRegistry, new JdbcQueryTags(tableConfigs));

        Connection connection = DriverManager.getConnection("jdbc:h2:mem:jooq-execute-listener-factory-test;DB_CLOSE_DELAY=-1", "sa", "");
        connection.close();
        var dsl = DSL.using(new DefaultConfiguration()
            .set(connection)
            .set(SQLDialect.H2)
            .set(new Settings())
            .set(provider));

        // When
        assertThatThrownBy(() -> dsl.select(field("id")).from(table("t")).fetch())
            .isInstanceOf(DataAccessException.class);

        // Then
        Timer timer = metricRegistry.find(MetricRegistry.METRIC_JDBC_QUERY_DURATION).timer();
        assertThat(timer).isNotNull();
        assertThat(Duration.ofNanos((long) timer.totalTime(TimeUnit.NANOSECONDS)))
            .isLessThan(Duration.ofSeconds(10));
    }

    @Test
    void shouldProvideANewListenerInstancePerCall() {
        // Given
        when(tableConfigs.getTableConfigs()).thenReturn(List.of());
        MetricRegistry metricRegistry = new MetricRegistry(new SimpleMeterRegistry(), new MetricConfig());
        ExecuteListenerProvider provider = new JooqExecuteListenerFactory()
            .jooqConfiguration(metricRegistry, new JdbcQueryTags(tableConfigs));

        // When
        ExecuteListener first = provider.provide();
        ExecuteListener second = provider.provide();

        // Then
        assertThat(first).isNotSameAs(second);
    }
}
