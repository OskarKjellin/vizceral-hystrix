package vizceral.hystrix;

/**
 * An event in the hystrix stream.
 */
public class HystrixEvent
{
    private final int rejectedCount;
    private final int timeoutCount;
    private final int requestCount;
    private final int errorCount;
    private final int totalRequestCount;
    private final boolean isCircuitBreakerOpen;
    private final String group;
    private final String name;
    private final long created = System.currentTimeMillis();

    private HystrixEvent(Builder builder)
    {
        rejectedCount = builder.rejectedCount;
        timeoutCount = builder.timeoutCount;
        requestCount = builder.requestCount;
        errorCount = builder.errorCount;
        totalRequestCount = builder.totalRequestCount;
        isCircuitBreakerOpen = builder.isCircuitBreakerOpen;
        group = builder.group;
        name = builder.name;
    }

    public static Builder newBuilder(HystrixEvent copy)
    {
        Builder builder = new Builder();
        builder.rejectedCount = copy.rejectedCount;
        builder.timeoutCount = copy.timeoutCount;
        builder.requestCount = copy.requestCount;
        builder.errorCount = copy.errorCount;
        builder.totalRequestCount = copy.totalRequestCount;
        builder.isCircuitBreakerOpen = copy.isCircuitBreakerOpen;
        builder.group = copy.group;
        builder.name = copy.name;
        return builder;
    }

    /**
     * Gets the current successful request count.
     *
     * @return Request count.
     */
    public int getRequestCount()
    {
        return requestCount;
    }

    /**
     * Gets the current error count.
     *
     * @return Error count.
     */
    public int getErrorCount()
    {
        return errorCount;
    }

    /**
     * Gets the group in the hystrix event.
     *
     * @return Hystrix group.
     */
    public String getGroup()
    {
        return group;
    }

    /**
     * Gets the name of the hystrix command.
     *
     * @return Hystrix command.
     */
    public String getName()
    {
        return name;
    }

    /**
     * Gets when this event was received by the aggregator.
     *
     * @return epoch millis of created.
     */
    public long getCreated()
    {
        return created;
    }

    /**
     * Gets the current timeout count.
     *
     * @return Timeout count.
     */
    public int getTimeoutCount()
    {
        return timeoutCount;
    }

    /**
     * Gets the current count of thread pool/semaphore rejected count.
     *
     * @return Rejected count.
     */
    public int getRejectedCount()
    {
        return rejectedCount;
    }

    /**
     * Gets if the circuit breaker is currently open.
     *
     * @return true if it's open, otherwise false.
     */
    public boolean isCircuitBreakerOpen()
    {
        return isCircuitBreakerOpen;
    }

    /**
     * Gets total count of all requests, rejected, timeouts, error, success etc.
     *
     * @return Sum of all requests.
     */
    public int getTotalRequestCount()
    {
        return totalRequestCount;
    }

    @Override
    public String toString()
    {
        return "HystrixEvent{" +
                "rejectedCount=" + rejectedCount +
                ", timeoutCount=" + timeoutCount +
                ", requestCount=" + requestCount +
                ", errorCount=" + errorCount +
                ", group='" + group + '\'' +
                ", name='" + name + '\'' +
                ", created=" + created +
                '}';
    }

    public static Builder newBuilder()
    {
        return new Builder();
    }

    public static final class Builder
    {
        private int errorCount;
        private int totalRequestCount;
        private boolean isCircuitBreakerOpen;
        private String group;
        private String name;
        private int requestCount;
        private int timeoutCount;
        private int rejectedCount;

        private Builder()
        {
        }

        public Builder errorCount(int errorCount)
        {
            this.errorCount = errorCount;
            return this;
        }

        public Builder totalRequestCount(int totalRequestCount)
        {
            this.totalRequestCount = totalRequestCount;
            return this;
        }

        public Builder isCircuitBreakerOpen(boolean isCircuitBreakerOpen)
        {
            this.isCircuitBreakerOpen = isCircuitBreakerOpen;
            return this;
        }

        public Builder group(String group)
        {
            this.group = group;
            return this;
        }

        public Builder name(String name)
        {
            this.name = name;
            return this;
        }

        public HystrixEvent build()
        {
            return new HystrixEvent(this);
        }

        public Builder requestCount(int requestCount)
        {
            this.requestCount = requestCount;
            return this;
        }

        public Builder timeoutCount(int timeoutCount)
        {
            this.timeoutCount = timeoutCount;
            return this;
        }

        public Builder rejectedCount(int rejectedCount)
        {
            this.rejectedCount = rejectedCount;
            return this;
        }
    }
}
