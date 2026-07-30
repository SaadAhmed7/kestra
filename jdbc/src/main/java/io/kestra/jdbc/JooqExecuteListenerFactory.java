package io.kestra.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.jooq.ExecuteListenerProvider;

import io.kestra.core.metrics.MetricRegistry;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Factory
public class JooqExecuteListenerFactory {
    private static final Duration SLOW_QUERY_THRESHOLD = Duration.ofMillis(500);

    @EachBean(DataSource.class)
    public ExecuteListenerProvider jooqConfiguration(MetricRegistry metricRegistry, JdbcQueryTags queryTags) {
        return new ExecuteListenerProvider() {
            @Override
            public @NotNull ExecuteListener provide() {
                return new ExecuteListener() {
                    private static final AtomicBoolean CONNECTION_CHECKED = new AtomicBoolean(false);

                    private long startTime;

                    @Override
                    public void start(ExecuteContext ctx) {
                        // start()/end() is the only listener callback pair jOOQ guarantees to fire exactly
                        // once for every execution, including one that fails during rendering/prepare/bind
                        // before executeStart() ever runs; executeStart()/executeEnd() do not fire in that case
                        startTime = System.nanoTime();
                    }

                    @Override
                    public void executeStart(ExecuteContext ctx) {
                        // check that isolation level is READ UNCOMMITED, it's the default for Postgres but not for MySQL,
                        // our queue system didn't work correctly otherwise.
                        if (!CONNECTION_CHECKED.getAndSet(true)) {
                            try {
                                if (ctx.connection().getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
                                    throw new IllegalStateException("Isolation level must be READ COMMITTED");
                                }
                            } catch (SQLException e) {
                                // silently ignore any exception here
                            }
                        }
                    }

                    @Override
                    public void end(ExecuteContext ctx) {
                        // recorded in end() rather than executeEnd(), which fires before jOOQ fetches any rows:
                        // executeEnd() alone under-reports the duration of anything that returns a result set
                        Duration duration = Duration.ofNanos(System.nanoTime() - startTime);

                        // record a single condensed timer for the table/query type
                        String[] tags = queryTags.tags(ctx);
                        metricRegistry.timer(MetricRegistry.METRIC_JDBC_QUERY_DURATION, MetricRegistry.METRIC_JDBC_QUERY_DURATION_DESCRIPTION, tags)
                            .record(duration);

                        // for slow queries, record a timer for the exact SQL
                        // for batch queries, the query will be expanded without parameters, and will lead to overflow of metrics so we exclude them
                        if (duration.compareTo(SLOW_QUERY_THRESHOLD) > 0 && ctx.batchMode() != ExecuteContext.BatchMode.MULTIPLE && ctx.sql() != null) {
                            String[] slowQueryTags = { "sql", ctx.sql() };
                            metricRegistry.timer(MetricRegistry.METRIC_JDBC_SLOW_QUERY_DURATION, MetricRegistry.METRIC_JDBC_SLOW_QUERY_DURATION_DESCRIPTION, slowQueryTags)
                                .record(duration);
                        }

                        if (log.isTraceEnabled()) {
                            log.trace("[Duration: {}] [Rows: {}] [Query: {}]", duration, ctx.rows(), ctx.query());
                        } else if (log.isDebugEnabled()) {
                            log.debug("[Duration: {}] [Rows: {}] [Query: {}]", duration, ctx.rows(), ctx.sql());
                        }
                    }
                };
            }
        };
    }
}
