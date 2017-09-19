package vizceral.hystrix;

import rx.schedulers.Schedulers;
import rx.subjects.ReplaySubject;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A monitored cluster through hystrix, together with all events for it.
 */
public class HystrixCluster
{
    private static final DecimalFormat FORMAT = new DecimalFormat("#.##");
    private final String name;
    private final ConcurrentMap<String, HystrixEvent> events = new ConcurrentHashMap<>();
    private final ReplaySubject<Integer> maxSubject;

    /**
     * Creates a new cluster
     *
     * @param name                 The name of the cluster
     * @param maxTrafficTtlSeconds How many seconds back in the future we should consider max traffic
     */
    public HystrixCluster(String name, int maxTrafficTtlSeconds)
    {
        this.name = name;
        this.maxSubject = ReplaySubject.createWithTime(maxTrafficTtlSeconds, TimeUnit.SECONDS, Schedulers.computation());
    }

    /**
     * Adds an event, storing it as the last one for the command.
     *
     * @param event The event to add.
     */
    public void addEvent(HystrixEvent event)
    {
        events.put(event.getName(), event);
        int currentSum = events.values().stream().mapToInt(c -> c.getTotalRequestCount()).sum();
        maxSubject.onNext(currentSum);
    }

    /***
     * Sums up all request going out from this cluster.
     * @return The sum of all outgoing requests.
     */
    public int getSumOfOutgoingRequests()
    {
        return events.values().stream().mapToInt(c -> c.getTotalRequestCount()).sum();
    }

    /**
     * Gets the name of the cluster.
     *
     * @return The name.
     */
    public String getName()
    {
        return name;
    }

    /**
     * Gets all events that have been stored for hystrix commands (one per command).
     *
     * @return Collection of events.
     */
    public Collection<HystrixEvent> getEvents()
    {
        return Collections.unmodifiableCollection(events.values());
    }

    /**
     * Gets all connections going out from this cluster.
     *
     * @param configuration The configuration to use for creating notices.
     *
     * @return Collection of connections.
     */
    public Collection<VizceralConnection> getConnections(Configuration configuration)
    {
        Map<String, AtomicInteger> errorsPerGroup = new HashMap<>();
        Map<String, AtomicInteger> requestsPerGroup = new HashMap<>();
        Map<String, AtomicInteger> timeoutsPerGroup = new HashMap<>();
        Map<String, List<VizceralNotice>> notices = new HashMap<>();
        for (HystrixEvent hystrixEvent : events.values())
        {
            String group = hystrixEvent.getGroup();
            if (!errorsPerGroup.containsKey(group))
            {
                notices.put(group, new ArrayList<>());
                errorsPerGroup.put(group, new AtomicInteger());
                requestsPerGroup.put(group, new AtomicInteger());
                timeoutsPerGroup.put(group, new AtomicInteger());
            }
            errorsPerGroup.get(group).addAndGet(hystrixEvent.getErrorCount());
            requestsPerGroup.get(group).addAndGet(hystrixEvent.getRequestCount());
            timeoutsPerGroup.get(group).addAndGet(hystrixEvent.getTimeoutCount());
            double failurePercentage = (double) hystrixEvent.getErrorCount() / hystrixEvent.getTotalRequestCount();
            double timeoutPercentage = (double) hystrixEvent.getTimeoutCount() / hystrixEvent.getTotalRequestCount();
            if (configuration.getTimeoutPercentageThreshold() != null)
            {
                if (configuration.getTimeoutPercentageThreshold() < timeoutPercentage)
                {
                    notices.get(group).add(VizceralNotice.newBuilder().severity(NoticeSeverity.WARNING).title(FORMAT.format(timeoutPercentage * 100) + "% timeouts").subtitle(hystrixEvent.getName()).build());
                }
            }
            if (configuration.getFailurePercentageThreshold() != null)
            {
                if (configuration.getFailurePercentageThreshold() < failurePercentage)
                {
                    notices.get(group).add(VizceralNotice.newBuilder().severity(NoticeSeverity.ERROR).title(FORMAT.format(failurePercentage * 100) + "% failures").subtitle(hystrixEvent.getName()).build());
                }
            }
            if (hystrixEvent.isCircuitBreakerOpen())
            {
                notices.get(group).add(VizceralNotice.newBuilder().severity(NoticeSeverity.ERROR).title("Circuit breaker triggered").subtitle(hystrixEvent.getName()).build());
            }
        }
        List<VizceralConnection> connections = new ArrayList<>();
        for (String group : errorsPerGroup.keySet())
        {
            VizceralConnection connection = new VizceralConnection(group, errorsPerGroup.get(group).get(), requestsPerGroup.get(group).get(), timeoutsPerGroup.get(group).get(), notices.get(group));
            connections.add(connection);
        }
        return Collections.unmodifiableCollection(connections);
    }

    /**
     * Gets the maximum number of requests per second seen.
     *
     * @return Maximum number of requests seen.
     */
    public int getMaxValue()
    {
        int size = maxSubject.size();
        if (size == 0)
        {
            return 0;
        }
        return Collections.max(Arrays.asList(maxSubject.getValues(new Integer[size])));
    }

    /**
     * Gets if this cluster has any thread pool/semaphore rejected requests going outwards.
     *
     * @return true if any requests are rejected, false otherwise.
     */
    public boolean anyRejected()
    {
        return events.values().stream().anyMatch(c -> c.getRejectedCount() > 0);
    }
}
