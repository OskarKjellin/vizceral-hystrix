package vizceral.hystrix;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A monitored cluster through hystrix, together with all events for it.
 */
public class HystrixCluster
{
    private final String name;
    private final ConcurrentMap<String, HystrixEvent> events = new ConcurrentHashMap<>();
    AtomicInteger maxValue = new AtomicInteger();

    /**
     * Creates a new cluster
     *
     * @param name The name of the cluster
     */
    public HystrixCluster(String name)
    {
        this.name = name;
    }

    /**
     * Adds an event, storing it as the last one for the command.
     *
     * @param event The event to add.
     */
    public void addEvent(HystrixEvent event)
    {
        events.put(event.getName(), event);
        int currentSum = events.values().stream().mapToInt(c -> c.getRequestCount()).sum();
        int before = maxValue.get();
        if (currentSum > before)
        {
            //This isn't really thread safe, but least we'll never inadvertently lower the value
            //Plus, this should only be called from one thread anyhow
            maxValue.compareAndSet(before, currentSum);
        }
    }

    /***
     * Sums up all request going out from this cluster.
     * @return The sum of all outgoing requests.
     */
    public int getSumOfOutgoingRequests()
    {
        return events.values().stream().mapToInt(c -> c.getRequestCount()).sum();
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
     * @return Collection of connections.
     */
    public Collection<VizceralConnection> getConnections()
    {
        Map<String, AtomicInteger> errorsPerGroup = new HashMap<>();
        Map<String, AtomicInteger> requestsPerGroup = new HashMap<>();
        Map<String, AtomicInteger> timeoutsPerGroup = new HashMap<>();
        for (HystrixEvent hystrixEvent : events.values())
        {
            String group = hystrixEvent.getGroup();
            if (!errorsPerGroup.containsKey(group))
            {
                errorsPerGroup.put(group, new AtomicInteger());
                requestsPerGroup.put(group, new AtomicInteger());
                timeoutsPerGroup.put(group, new AtomicInteger());
            }
            errorsPerGroup.get(group).addAndGet(hystrixEvent.getErrorCount());
            requestsPerGroup.get(group).addAndGet(hystrixEvent.getRequestCount());
            timeoutsPerGroup.get(group).addAndGet(hystrixEvent.getTimeoutCount());
        }
        List<VizceralConnection> connections = new ArrayList<>();
        for (String group : errorsPerGroup.keySet())
        {
            VizceralConnection connection = new VizceralConnection(group, errorsPerGroup.get(group).get(), requestsPerGroup.get(group).get(), timeoutsPerGroup.get(group).get());
            connections.add(connection);
        }
        return Collections.unmodifiableCollection(connections);
    }

    /**
     * Gets if this cluster has any thread poll/semaphore rejected requests going outwards.
     *
     * @return true if any requests are rejected, false otherwise.
     */
    public boolean anyRejected()
    {
        return events.values().stream().anyMatch(c -> c.getRejectedCount() > 0);
    }
}
