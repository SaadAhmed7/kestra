package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.utils.ReadOnlyDelegatingMap;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LazyLoadingOutputMap extends ReadOnlyDelegatingMap<String, Object> {
    private final Map<String, Object> lazyMap;

    public LazyLoadingOutputMap(Execution execution, TaskOutputService taskOutputService) {
        this.lazyMap = execution.getTaskRunList().stream()
            .map(TaskRun::getTaskId)
            .collect(Collectors.toMap(Function.identity(), id -> new LazyLoadingTaskOutputMap(execution, taskOutputService, id)));
    }

    @Override
    protected Map<String, Object> getDelegate() {
        return lazyMap;
    }


    @Override
    public boolean containsValue(Object value) {
        throw new UnsupportedOperationException("This map is lazy");
    }

    static class LazyLoadingTaskOutputMap extends ReadOnlyDelegatingMap<String, Object> {
        private final Execution execution;
        private final TaskOutputService taskOutputService;
        private final String id;

        private volatile Map<String, Object> delegate;

        LazyLoadingTaskOutputMap(Execution execution, TaskOutputService taskOutputService, String id) {
            this.execution = execution;
            this.taskOutputService = taskOutputService;
            this.id = id;
        }


        @Override
        protected Map<String, Object> getDelegate() {
            if (delegate == null) {
                synchronized (this) {
                    if (delegate == null) {
                        delegate = taskOutputService.computeOutputs(execution, id);
                    }
                }
            }

            return delegate;
        }
    }
}
