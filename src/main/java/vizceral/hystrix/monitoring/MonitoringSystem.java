package vizceral.hystrix.monitoring;

import vizceral.hystrix.VizceralNotice;

import java.util.Collection;

/**
 * Interface to fetch alerts per application for
 */
public interface MonitoringSystem
{
    /**
     * Gets all alerts for a specific cluster.
     *
     * @param clusterName The name of the cluster.
     *
     * @return Collection of notices.
     */
    Collection<VizceralNotice> getAlertsForCluster(String clusterName);

    /**
     * Starts the system. Might trigger timers, pollers etc.
     */
    void start();
}
