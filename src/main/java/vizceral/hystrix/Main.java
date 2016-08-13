package vizceral.hystrix;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.server.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


public class Main
{
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws InterruptedException, IOException
    {
        if (args.length < 1)
        {
            logger.info("Need to specify configuration file as first and only argument");
            return;
        }
        String file = args[0];
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
        VizceralAggregator vizceralAggregator = new VizceralAggregator(configuration);
        vizceralAggregator.start();

        HttpServer<ByteBuf, ByteBuf> server = RxNetty.<ByteBuf, ByteBuf>newHttpServerBuilder(configuration.getHttpPort(), (request, response) ->
        {
            String json = vizceralAggregator.vizceral().toString();
            response.getHeaders().add("Access-Control-Allow-Origin", "*");
            response.getHeaders().add("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
            response.getHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            response.getHeaders().add("Content-Type", "application/json");
            return response.writeStringAndFlush(json);
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
}
