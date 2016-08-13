package vizceral.hystrix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Wrapper for the configuration file.
 */
public class Configuration
{
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String fileName;
    private String turbineHost;
    private int turbinePort;
    private String turbinePath;
    private Set<String> entryClusters = new HashSet<>();
    private Set<String> internetClusters = new HashSet<>();
    private final Map<String, String> hystrixGroupsToCluster = new HashMap<>();
    private int httpPort = 8081;

    private Configuration(String fileName)
    {
        this.fileName = fileName;
    }

    /**
     * Gets the host name or ip of the turbine server to read from.
     *
     * @return The string host.
     */
    public String getTurbineHost()
    {
        return turbineHost;
    }

    /**
     * Gets the port that turbine is listening on.
     *
     * @return The port.
     */
    public int getTurbinePort()
    {
        return turbinePort;
    }

    /**
     * Gets the path relative to the root of the turbine server to listen for a specific cluster.
     *
     * @param cluster The cluster to listen for events on.
     *
     * @return The path to listen on.
     */
    public String getTurbinePath(String cluster)
    {
        return turbinePath + cluster;
    }

    /**
     * Gets entry clusters to read from. Entry clusters are typically the outermost clusters that does not have any
     * other clusters sending in requests to them. If any other cluster depends on it, they'll be discovered anyhow.
     *
     * @return Iterable of the entry clusters.
     */
    public Iterable<String> getEntryClusters()
    {
        return Collections.unmodifiableSet(entryClusters);
    }

    /**
     * Gets the internet clusters. This is clusters that should be drawn as receiving traffic from the internet.
     *
     * @return Iterable of the internet clusters
     */
    public Iterable<String> getInternetClusters()
    {
        return Collections.unmodifiableSet(internetClusters);
    }

    /**
     * Checks if this cluster is configured as an internet cluster in the configuration.
     *
     * @param cluster The cluster to check.
     *
     * @return True if the cluster is an internet cluster, otherwise false.
     */
    public boolean isInternetCluster(String cluster)
    {
        return internetClusters.contains(cluster);
    }

    /**
     * Gets the group (cluster to be used) based on the group from a hystrix event.
     * This is mapped using the hystrixGroupToCluster in order to allow groups to not be exact match of the cluster name
     *
     * @param name The name of the group to check.
     *
     * @return The mapped group if found, otherwise returns the input.
     */
    public String getEffectiveGroup(String name)
    {
        String lower = name.toLowerCase();
        String effectiveGroup = hystrixGroupsToCluster.get(lower);
        if (effectiveGroup != null)
        {
            return effectiveGroup;
        }
        else
        {
            return name;
        }
    }

    /**
     * Gets the port to start the http server on. Default 8081.
     *
     * @return The port to listen on.
     */
    public int getHttpPort()
    {
        return httpPort;
    }

    /**
     * Loads configuration from the specified file.
     *
     * @param fileName The file to load configuration from.
     *
     * @return The loaded configuration.
     *
     * @throws ConfigurationException If the configuration is invalid or not found.
     */
    public static Configuration load(String fileName) throws ConfigurationException
    {
        Configuration configuration = new Configuration(fileName);
        configuration.load();
        return configuration;
    }

    private void load() throws ConfigurationException
    {
        File file = new File(fileName);
        if (!file.exists())
        {
            throw new ConfigurationException("File " + file.getPath() + " does not exist");
        }
        JsonNode objectNode = null;
        try
        {
            objectNode = objectMapper.readTree(file);
        }
        catch (IOException e)
        {
            throw new ConfigurationException("Exception when reading file", e);
        }
        //Http conf
        if (objectNode.has("httpPort"))
        {
            JsonNode httpPortNode = objectNode.get("httpPort");
            if (!httpPortNode.isInt())
            {
                throw new ConfigurationException("/httpPort must be an int");
            }
            httpPort = httpPortNode.asInt();
        }
        //Turbine conf
        if (!objectNode.has("turbine"))
        {
            throw new ConfigurationException("Json must have /turbine");
        }
        JsonNode turbineNode = objectNode.get("turbine");
        if (!turbineNode.has("host"))
        {
            throw new ConfigurationException("Json must have /turbine/host");
        }
        if (!turbineNode.get("host").isTextual())
        {
            throw new ConfigurationException("/turbine/host must be a string");
        }
        if (!turbineNode.has("port"))
        {
            throw new ConfigurationException("Json must have /turbine/port");
        }
        if (!turbineNode.get("port").isInt())
        {
            throw new ConfigurationException("/turbine/port must be an int");
        }
        turbineHost = turbineNode.get("host").asText();
        turbinePort = turbineNode.get("port").asInt();
        if (turbineNode.has("path"))
        {
            if (!turbineNode.get("path").isTextual())
            {
                throw new ConfigurationException("/turbine/path must be a string");
            }
            turbinePath = turbineNode.get("path").asText();
        }
        else
        {
            turbinePath = "/turbine.stream?cluster=";
        }

        //Entry clusters conf
        if (!objectNode.has("entryClusters"))
        {
            throw new ConfigurationException("/Json must have /entryClusters");
        }
        if (!objectNode.get("entryClusters").isArray())
        {
            throw new ConfigurationException("/entryClusters must be an array");
        }

        for (JsonNode node : objectNode.get("entryClusters"))
        {
            if (!node.isTextual())
            {
                throw new ConfigurationException("Element in /entryClusters must be a string: " + node);
            }
            entryClusters.add(node.asText());
        }

        //Internet clusters conf
        if (objectNode.has("internetClusters"))
        {
            JsonNode internetClustersNode = objectNode.get("internetClusters");
            if (!internetClustersNode.isArray())
            {
                throw new ConfigurationException("/internetClusters must be an array");
            }

            for (JsonNode node : internetClustersNode)
            {
                if (!node.isTextual())
                {
                    throw new ConfigurationException("Element in /internetClusters must be a string: " + node);
                }
                internetClusters.add(node.asText());
            }
        }
        if (objectNode.has("hystrixGroupToCluster"))
        {
            JsonNode hystrixGroupToClusterNode = objectNode.get("hystrixGroupToCluster");
            if (!hystrixGroupToClusterNode.isArray())
            {
                throw new ConfigurationException("/hystrixGroupToCluster must be an array");
            }

            for (JsonNode node : hystrixGroupToClusterNode)
            {
                if (!node.isObject())
                {
                    throw new ConfigurationException("Element in /hystrixGroupToCluster must be an object:" + node);
                }
                if (!node.has("group"))
                {
                    throw new ConfigurationException("Element in /hystrixGroupToCluster must be have key group:" + node);
                }
                if (!node.get("group").isTextual())
                {
                    throw new ConfigurationException("Element in /hystrixGroupToCluster, group must be text:" + node);
                }
                if (!node.has("cluster"))
                {
                    throw new ConfigurationException("Element in /hystrixGroupToCluster must be have key cluster:" + node);
                }
                if (!node.get("cluster").isTextual())
                {
                    throw new ConfigurationException("Element in /hystrixGroupToCluster, cluster must be text:" + node);
                }
                if (hystrixGroupsToCluster.containsKey(node.get("group").asText()))
                {
                    throw new ConfigurationException("Duplicate group in /hystrixGroupToCluster " + node.get("group").asText());
                }
                hystrixGroupsToCluster.put(node.get("group").asText(), node.get("cluster").asText());
            }
        }
    }
}
