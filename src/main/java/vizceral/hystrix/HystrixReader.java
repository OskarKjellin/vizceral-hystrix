package vizceral.hystrix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.PrematureChannelClosureException;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.pipeline.ssl.DefaultFactories;
import io.reactivex.netty.pipeline.ssl.SSLEngineFactory;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientBuilder;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.sse.ServerSentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

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
        HttpClientBuilder<ByteBuf, ServerSentEvent> builder = RxNetty.newHttpClientBuilder(configuration.getTurbineHost(), configuration.getTurbinePort());
        builder.pipelineConfigurator(PipelineConfigurators.clientSseConfigurator());
        if (configuration.isSecure())
        {
            builder.withSslEngineFactory(new HystrixSSLEngineFactory(configuration.getTurbineHost(), configuration.getHttpPort()));
        }
        rxNetty = builder.build();
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
        if (configuration.authEnabled())
        {
            String authHeader = "Basic " + Base64.encode(Unpooled.copiedBuffer(configuration.getUsername() + ":" + configuration.getPassword(), StandardCharsets.UTF_8)).toString(StandardCharsets.UTF_8).replace("\n", "");
            request.getHeaders().add("Authorization", authHeader);
        }
        return rxNetty.submit(request)
                .flatMap(c ->
                {
                    logger.info("Http code {} for path {} in region {}", c.getStatus().code(), path, configuration.getRegionName());
                    if (c.getStatus().code() == 404)
                    {
                        return Observable.error(new UnknownClusterException("Turbine does not recognize cluster " + cluster));
                    }
                    else if (c.getStatus().code() != 200)
                    {
                        return Observable.error(new IllegalStateException("Got " + c.getStatus().code() + " from turbine"));
                    }
                    else
                    {
                        return c.getContent();
                    }
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
                                .rejectedCount((sumFields(objectNode, "rollingCountSemaphoreRejected", "rollingCountThreadPoolRejected")) / 10)
                                .timeoutCount(objectNode.get("rollingCountTimeout").asInt() / 10)
                                .errorCount((sumFields(objectNode, "rollingCountFailure", "rollingCountSemaphoreRejected", "rollingCountShortCircuited") / 10))
                                .requestCount(objectNode.get("rollingCountSuccess").asInt() / 10)
                                .totalRequestCount(objectNode.get("requestCount").asInt() / 10)
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
                .filter(Objects::nonNull)
                .onErrorResumeNext(ex ->
                {
                  if (ex instanceof UnknownClusterException) {
                    logger.warn("UnknownClusterException returned");
                      return Observable.error(ex);
                  }
                  if (ex instanceof IllegalStateException) {
                    logger.warn("IllegalStateException returned");
                    return Observable.error(ex);
                  }

                  logger.error("Exception from hystrix event for cluster " + cluster + " for region " + configuration.getRegionName() + ". Will retry in 10s", ex);
                  return Observable.timer(10, TimeUnit.SECONDS).flatMap(ignore -> read());
                })
                .doOnCompleted(() -> logger.info("Cluster {} got on completed", cluster))
                .repeatWhen(observable -> observable.flatMap(ignore -> read()));
    }

    private static int sumFields(JsonNode objectNode, String... keys)
    {
        int sum = 0;
        for (String key : keys)
        {
            if (objectNode.has(key))
            {
                sum += objectNode.get(key).asInt();
            }
        }
        return sum;
    }

    private static class HystrixSSLEngineFactory implements SSLEngineFactory {

        private final SslContext sslCtx;
        private final String host;
        private final int port;

        private HystrixSSLEngineFactory(String host, int port) {
            this.host = host;
            this.port = port;
            try {

                sslCtx = SslContextBuilder
                        .forClient()
                        .sslProvider(SslProvider.JDK)
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build();

            } catch (Exception e) {
                throw new IllegalStateException("Failed to create default SSL context", e);
            }
        }

        @Override
        public SSLEngine createSSLEngine(ByteBufAllocator allocator) {
            return sslCtx.newEngine(allocator, host, port);
        }
    }
}
