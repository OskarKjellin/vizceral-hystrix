package vizceral.hystrix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.server.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class Main
{
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws InterruptedException, IOException
    {
        try
        {
            if (args.length < 1)
            {
                logger.info("Need to specify at least one   configuration file");
                return;
            }
            List<VizceralAggregator> aggregators = new ArrayList<>();
            int port = 0;
            for (String file : args)
            {
                Configuration configuration;
                try
                {
                    configuration = Configuration.load(file);
                }
                catch (ConfigurationException ex)
                {
                    if (ex.getCause() != null)
                    {
                        logger.info(ex.getMessage(), ex.getCause());
                    }
                    else
                    {
                        logger.info(ex.getMessage());
                    }
                    return;
                }
                if (port == 0)
                {
                    port = configuration.getHttpPort();
                }
                VizceralAggregator vizceralAggregator = new VizceralAggregator(configuration);
                aggregators.add(vizceralAggregator);
                vizceralAggregator.start();
            }

            HttpServer<ByteBuf, ByteBuf> server = RxNetty.<ByteBuf, ByteBuf>newHttpServerBuilder(port, (request, response) ->
            {
                JsonNode jsonNode = null;
                for (VizceralAggregator vizceralAggregator : aggregators)
                {
                    if (jsonNode == null)
                    {
                        jsonNode = vizceralAggregator.vizceral();
                    }
                    else
                    {
                        for (JsonNode node : vizceralAggregator.vizceral().get("nodes"))
                        {
                            if ("INTERNET".equals(node.get("name").asText()))
                            {
                                continue;
                            }
                            ((ArrayNode) jsonNode.get("nodes")).add(node);
                        }
                        for (JsonNode node : vizceralAggregator.vizceral().get("connections"))
                        {
                            ((ArrayNode) jsonNode.get("connections")).add(node);
                        }
                    }
                    ArrayNode connections = (ArrayNode) jsonNode.get("connections");
                    for (VizceralAggregator otherAggregator : aggregators)
                    {
                        if (otherAggregator == vizceralAggregator)
                        {
                            continue;
                        }
                        connections.addObject()
                                .put("source", vizceralAggregator.getConfiguration().getRegionName())
                                .put("target", otherAggregator.getConfiguration().getRegionName())
                                .putObject("metrics");
                    }
                }
                response.getHeaders().add("Access-Control-Allow-Origin", "*");
                response.getHeaders().add("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
                response.getHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                response.getHeaders().add("Content-Type", "application/json");
                return response.writeStringAndFlush(jsonNode.toString());
            }).build();

            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread()
            {
                @Override
                public void run()
                {
                    try
                    {
                        logger.info("Shutting down server");
                        server.shutdown();
                        logger.info("Server shut down");
                    }
                    catch (InterruptedException e)
                    {
                        logger.error("Could not stop server", e);
                    }
                }
            });

            server.waitTillShutdown();
        }
        catch (Throwable t)
        {
            logger.error("Error when starting", t);
        }
    }
}
