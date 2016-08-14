package vizceral.hystrix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.sse.ServerSentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.io.IOException;

/**
 * Reads a hystrix event stream (typically from turbine) and emits events when items are received in the SSE stream.
 */
public class HystrixReader
{
    private static final Logger logger = LoggerFactory.getLogger(HystrixReader.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient<ByteBuf, ServerSentEvent> rxNetty;
    private final Configuration configuration;
    private final String cluster;

    /**
     * Creates a new hystrix reader.
     *
     * @param configuration The configuration to use.
     * @param cluster       The cluster to read from.
     */
    public HystrixReader(Configuration configuration, String cluster)
    {
        this.configuration = configuration;
        this.cluster = cluster;
        rxNetty = RxNetty.createHttpClient(configuration.getTurbineHost(), configuration.getTurbinePort(), PipelineConfigurators.<ByteBuf>clientSseConfigurator());
    }

    /**
     * Starts reading Sever Sent Events from hystrix and emits one item to the observable per HystrixCommand type event.
     *
     * @return Observable that can be subscribed to receive events from hystrix.
     */
    public Observable<HystrixEvent> read()
    {
        String path = configuration.getTurbinePath(cluster);
        logger.info("Starting to read from path {}", path);
        final HttpClientRequest<ByteBuf> request = HttpClientRequest.create(HttpMethod.GET, path);

        return rxNetty.submit(request)
                .flatMap(c ->
                {
                    logger.info("Http code {} for path {}", c.getStatus().code(), path);
                    if (c.getStatus().code() != 200)
                    {
                        return Observable.error(new UnknownClusterException("Turbine does not recognize cluster " + cluster));
                    }
                    return c.getContent();
                })
                .map(sse ->
                {
                    try
                    {
                        JsonNode objectNode = objectMapper.readTree(sse.contentAsString());
                        if (!"HystrixCommand".equals(objectNode.get("type").asText()))
                        {
                            return null;
                        }
                        String commandName = objectNode.get("name").asText();
                        String group = configuration.getEffectiveGroup(objectNode.get("group").asText());
                        if (group.isEmpty())
                        {
                            logger.warn("Invalid hystrix event with an empty group for command {}", commandName);
                            return null;
                        }
                        return HystrixEvent
                                .newBuilder()
                                .rejectedCount((objectNode.get("rollingCountSemaphoreRejected").asInt() + objectNode.get("rollingCountThreadPoolRejected").asInt()) / 10)
                                .timeoutCount(objectNode.get("rollingCountTimeout").asInt() / 10)
                                .errorCount((objectNode.get("rollingCountFailure").asInt() + objectNode.get("rollingCountSemaphoreRejected").asInt() + objectNode.get("rollingCountShortCircuited").asInt()) / 10)
                                .requestCount(objectNode.get("rollingCountSuccess").asInt() / 10)
                                .group(group)
                                .name(commandName)
                                .isCircuitBreakerOpen(objectNode.get("isCircuitBreakerOpen").asBoolean())
                                .build();
                    }
                    catch (IOException e)
                    {
                        logger.error("Could not parse json", e);
                        return null;
                    }
                })
                .filter(c -> c != null);
    }
}
