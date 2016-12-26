package vizceral.hystrix.monitoring.zmon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.pipeline.ssl.DefaultFactories;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientBuilder;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import vizceral.hystrix.NoticeSeverity;
import vizceral.hystrix.VizceralNotice;
import vizceral.hystrix.monitoring.MonitoringSystem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Monitoring that fetches alerts from zmon.
 */
public class ZmonMonitoringSystem implements MonitoringSystem
{
    private static final Logger logger = LoggerFactory.getLogger(ZmonMonitoringSystem.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient<ByteBuf, ByteBuf> rxNetty;
    private final ZmonConfiguration configuration;
    private volatile Map<String, String> entitiesToApplication = new HashMap<>();
    private volatile Map<String, Collection<VizceralNotice>> notices = new HashMap<>();

    public ZmonMonitoringSystem(ZmonConfiguration configuration)
    {
        HttpClientBuilder<ByteBuf, ByteBuf> builder = RxNetty.newHttpClientBuilder(configuration.getHost(), configuration.getPort());
        if (configuration.isSecure())
        {
            builder.withSslEngineFactory(DefaultFactories.trustAll());
        }
        rxNetty = builder.build();
        this.configuration = configuration;
    }

    /**
     * Gets all alerts for a specific cluster.
     *
     * @param clusterName The name of the cluster.
     *
     * @return Collection of notices.
     */
    @Override
    public Collection<VizceralNotice> getAlertsForCluster(String clusterName)
    {
        return notices.getOrDefault(clusterName.toLowerCase(), Collections.emptyList());
    }

    /**
     * Starts polling from zmon.
     */
    @Override
    public void start()
    {
        pollEntities()
                .subscribe(entities ->
                {
                    Map<String, String> applicationsById = new HashMap<>();
                    for (JsonNode entity : entities)
                    {
                        String id = entity.get("id").asText();
                        String applicationId = entity.has("application_id")
                                ? entity.get("application_id").asText()
                                : id;
                        applicationsById.put(id.toLowerCase(), applicationId.toLowerCase());
                    }
                    entitiesToApplication = applicationsById;
                });
        pollAlerts().subscribe(alerts -> this.notices = getNotices(alerts));
    }

    private Map<String, Collection<VizceralNotice>> getNotices(JsonNode alerts)
    {
        Map<String, Collection<VizceralNotice>> newNotices = new HashMap<>();
        for (JsonNode node : alerts)
        {
            JsonNode alertDefinition = node.get("alert_definition");
            String alertName = alertDefinition.get("name").asText();
            for (JsonNode entity : node.get("entities"))
            {
                String entityId = entity.get("entity").asText();
                String applicationId = getApplicationId(entityId);
                String subtitle = "Active for " + format(entity.get("result").get("ts").longValue() - entity.get("result").get("start_time").asLong());
                if (!newNotices.containsKey(applicationId))
                {
                    newNotices.put(applicationId, new ArrayList<>());
                }
                String title = alertName;
                JsonNode captures = entity.get("result").get("captures");
                for (Iterator<String> it = captures.fieldNames(); it.hasNext(); )
                {
                    String key = it.next();
                    String value = captures.get(key).asText();
                    title = title.replace("{" + key + "}", value);
                }
                String protocol = configuration.isSecure() ? "https" : "http";
                String link = protocol + "://" + configuration.getHost() + ":" + configuration.getPort() + configuration.getPath() + "#/alert-details/" + alertDefinition.get("id").asText();
                newNotices.get(applicationId).add(
                        VizceralNotice
                                .newBuilder()
                                .severity(fromZmonPriority(alertDefinition.get("priority").asInt()))
                                .title(title)
                                .link(link)
                                .subtitle(subtitle)
                                .build());
            }
        }
        return newNotices;
    }

    private static String format(long s)
    {
        return String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, (s % 60));

    }

    private NoticeSeverity fromZmonPriority(int priority)
    {
        switch (priority)
        {
            case 1:
                return NoticeSeverity.ERROR;
            case 2:
            case 3:
                return NoticeSeverity.WARNING;
            default:
                logger.error("Unknown priority {}", priority);
                return NoticeSeverity.ERROR;
        }
    }

    private String getApplicationId(String id)
    {
        String mapped = entitiesToApplication.get(id.toLowerCase());
        if (mapped != null)
        {
            return mapped;
        }
        else
        {
            return id.toLowerCase();
        }
    }

    private Observable<ArrayNode> pollAlerts()
    {
        return Observable.interval(0, 10, TimeUnit.SECONDS)
                .flatMap(ignore -> get("api/v1/status/active-alerts"))
                .cast(ArrayNode.class);
    }


    private Observable<ArrayNode> pollEntities()
    {
        return Observable.interval(0, 10, TimeUnit.SECONDS)
                .flatMap(ignore -> get("api/v1/entities"))
                .cast(ArrayNode.class);
    }

    private Observable<JsonNode> get(String url)
    {
        return rxNetty.submit(withHeaders(HttpClientRequest.create(HttpMethod.GET, configuration.getPath() + url)))
                .flatMap(clientResponse ->
                {
                    if (clientResponse.getStatus().code() == 200)
                    {
                        return clientResponse.getContent();
                    }
                    else
                    {
                        logger.error("Got http {} from zmon", clientResponse.getStatus().code());
                        return Observable.empty();
                    }
                })
                .flatMap(this::parse);
    }

    private Observable<JsonNode> parse(ByteBuf byteBuf)
    {
        try
        {
            return Observable.just(objectMapper.readTree(new ByteBufInputStream(byteBuf)));
        }
        catch (IOException e)
        {
            return Observable.error(e);
        }
    }

    private <T> HttpClientRequest<T> withHeaders(HttpClientRequest<T> request)
    {
        for (Map.Entry<String, String> header : configuration.getHeaders().entrySet())
        {
            request.withHeader(header.getKey(), header.getValue());
        }
        return request;
    }
}
