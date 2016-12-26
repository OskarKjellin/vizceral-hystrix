package vizceral.hystrix.monitoring;

import vizceral.hystrix.Configuration;
import vizceral.hystrix.VizceralNotice;

import java.util.ArrayList;
import java.util.List;

/**
 * Service that aggregates the monitoring services and gets alerts for them.
 */
public class MonitoringService
{
    private final List<MonitoringSystem> monitoringSystems;

    public MonitoringService(Configuration configuration)
    {
        this.monitoringSystems = configuration.getMonitoringSystems();
    }

    public void start()
    {
        for (MonitoringSystem monitoringSystem : monitoringSystems)
        {
            monitoringSystem.start();
        }
    }

    public List<VizceralNotice> getAlertsForCluster(String clusterName)
    {
        List<VizceralNotice> notices = new ArrayList<>();
        for (MonitoringSystem monitoringSystem : monitoringSystems)
        {
            notices.addAll(monitoringSystem.getAlertsForCluster(clusterName));
        }
        return notices;
    }
}
