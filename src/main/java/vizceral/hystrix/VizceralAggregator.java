package vizceral.hystrix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

/**
 * Reads events from hystrix and aggregates them into a json that Vizceral can understand.
 * Will start reading for new clusters as they are discovered in the dependency tree.
 */
public class VizceralAggregator
{
    private static final Logger logger = LoggerFactory.getLogger(VizceralAggregator.class);
    private final ConcurrentMap<String, HystrixCluster> clusters = new ConcurrentHashMap<>();
    private final Map<String, HystrixReader> readers = new HashMap<>();
    private final Configuration configuration;

    /**
     * Creates a new VizceralAggregator
     *
     * @param configuration The configuration to use.
     */
    public VizceralAggregator(Configuration configuration)
    {
        this.configuration = configuration;
    }

    /**
     * Starts the reading and aggregration by tailing the entry clusters.
     */
    public void start()
    {
        for (String cluster : configuration.getEntryClusters())
        {
            startReader(cluster);
        }
    }

    /**
     * Gets the configuration.
     *
     * @return The configuration.
     */
    public Configuration getConfiguration()
    {
        return configuration;
    }

    /**
     * Gets a vizceral json
     *
     * @return JsonNode that can be fed to vizceral.
     */
    public JsonNode vizceral()
    {
        String regionName = configuration.getRegionName();
        ObjectNode objectNode = JsonNodeFactory.instance.objectNode()
                .put("renderer", "global")
                .put("name", "edge");
        ArrayNode nodes = objectNode.putArray("nodes");
        nodes.addObject()
                .put("renderer", "region")
                .put("name", "INTERNET")
                .put("class", "normal")
                .put("updated", System.currentTimeMillis())
                .putArray("nodes");
        ObjectNode regionNode = nodes.addObject()
                .put("renderer", "region")
                .put("name", regionName)
                .put("class", "normal")
                .put("updated", getUpdated());

        ArrayNode regionNodes = regionNode.putArray("nodes");

        Set<String> allClusters = new HashSet<>(clusters.keySet());
        allClusters.add("INTERNET");
        for (String clusterName : allClusters)
        {
            String status = "normal";
            if (clusters.containsKey(clusterName))
            {
                HystrixCluster cluster = clusters.get(clusterName);
                if (hasAnyoneCircuitBreakerOnMe(clusterName))
                {
                    status = "danger";
                }
                else if (cluster.anyRejected())
                {
                    status = "warning";
                }
            }
            ObjectNode clusterNode = regionNodes.addObject()
                    .put("name", clusterName)
                    .put("class", status);
            clusterNode
                    .putObject("metadata")
                    .put("streaming", 1);
            clusterNode.putArray("nodes");
        }

        //Max volume in cluster
        int maxVolume = clusters.values().stream().mapToInt(c -> c.getMaxValue()).max().orElse(0);

        ArrayNode connectionNodes = regionNode.putArray("connections");
        for (Map.Entry<String, HystrixCluster> cluster : clusters.entrySet())
        {
            for (VizceralConnection connection : cluster.getValue().getConnections(configuration))
            {
                ObjectNode connectionNode = connectionNodes.addObject()
                        .put("source", cluster.getKey())
                        .put("target", connection.getName());
                connectionNode.putObject("metadata").put("streaming", 1);
                connectionNode.putObject("metrics")
                        .put("danger", connection.getErrors())
                        .put("warning", connection.getTimeouts())
                        .put("normal", connection.getRequests());
                ArrayNode notices = connectionNode.withArray("notices");
                for (VizceralNotice notice : connection.getNotices())
                {
                    ObjectNode noticeNode = notices.addObject();
                    noticeNode.put("title", notice.getTitle());
                    if (notice.getSubtitle() != null)
                    {
                        noticeNode.put("subtitle", notice.getSubtitle());
                    }
                    if (notice.getLink() != null)
                    {
                        noticeNode.put("linked", notice.getLink());
                    }
                    if (notice.getSeverity() != null)
                    {
                        noticeNode.put("severity", notice.getSeverity().get());
                    }
                }
            }
        }
        for (String internetCluster : configuration.getInternetClusters())
        {
            HystrixCluster cluster = clusters.get(internetCluster);
            ObjectNode connectionNode = connectionNodes.addObject()
                    .put("source", "INTERNET")
                    .put("target", internetCluster);
            connectionNode.putObject("metadata").put("streaming", 1);
            connectionNode.putObject("metrics").put("normal", cluster.getSumOfOutgoingRequests());
        }
        regionNode.put("maxVolume", maxVolume);
        //Requests are all nodes that are leaving the internet clusters (not really true, but close enough)
        int currentRequests = clusters.values().stream().filter(c -> configuration.isInternetCluster(c.getName())).mapToInt(c -> c.getSumOfOutgoingRequests()).sum();

        ObjectNode internetConnection = objectNode.putArray("connections")
                .addObject()
                .put("source", "INTERNET")
                .put("target", regionName);
        internetConnection
                .putObject("metrics")
                .put("normal", currentRequests);
        internetConnection.putArray("notices");
        internetConnection.put("class", "normal");
        objectNode.put("maxVolume", maxVolume);
        return objectNode;
    }

    private long getUpdated()
    {
        return clusters.values().stream().flatMap(c -> c.getEvents().stream()).mapToLong(c -> c.getCreated()).max().orElse(0);
    }

    private boolean hasAnyoneCircuitBreakerOnMe(String cluster)
    {
        return getEventsTowardsMe(cluster).anyMatch(c -> c.isCircuitBreakerOpen());
    }

    private Stream<HystrixEvent> getEventsTowardsMe(String cluster)
    {
        return clusters.values().stream().flatMap(c -> c.getEvents().stream()).filter(c -> cluster.equals(c.getGroup()));
    }

    private void startReader(String clusterName)
    {
        logger.info("Starting to tail cluster " + clusterName);
        HystrixCluster cluster = new HystrixCluster(clusterName, configuration.getMaxTrafficTtlSeconds());
        clusters.put(clusterName, cluster);
        HystrixReader reader = new HystrixReader(configuration, clusterName);
        readers.put(clusterName, reader);
        reader.read().subscribe(c ->
        {
            if (!readers.containsKey(c.getGroup()))
            {
                startReader(c.getGroup());
            }
            logger.debug("Cluster {} has event towards {}, {}", clusterName, c.getGroup(), c);
            cluster.addEvent(c);
        }, ex ->
        {
            if (ex instanceof UnknownClusterException)
            {
                logger.info("Turbine does not recognize cluster " + clusterName + " for region " + configuration.getRegionName());
            }
            else
            {
                logger.error("Exception from hystrix event for cluster " + clusterName + " for region " + configuration.getRegionName(), ex);
            }
        });
    }
}
