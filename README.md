# vizceral-hystrix

[Vizceral](https://github.com/Netflix/vizceral) is a tool from Netflix to visualize traffic between components.
This data needs to be fed from somewhere however. 
[Hystrix](https://github.com/Netflix/Hystrix) provides fault tolerance, and a nice stream of all metrics.
[Turbine](https://github.com/Netflix/Turbine) aggregates the hystrix streams per cluster.

This tool reads the hystrix streams and aggregates them and provides an API to get the JSON to be fed into Vizceral.

It assumes that the hystrix "group" has a correlation with the cluster to tail.
Once a new group is discovered a new stream will be tailed from turbine.
If the group is not named the same as the cluster, mappings can be applied.

You need to specify the entry clusters to start tailing. 
Usually this is the outermost cluster. All dependencies of this will be automatically discovered.

If a circuit breaker is triggered, the cluster that is the target of the circuit breaker will be set as danger.
When some requests are rejected by the hystrix thread pool, the source cluster will be yellow (warning). 

Timeouts are treated as warnings (yellow dots), errors are danger (red dots).

**Configuration**

[Sample config file](/config.json)

```
{
  "httpPort": 8081, (optional) the http port to listen on
  "turbine": {
    "host": "127.0.0.1", //required, host of the turbine cluster
    "port": 8080, //required, port of the turbine cluster
    "path": "/turbine/turbine.stream?cluster=", //optional, path to the stream on the turbine cluster. Defauls to /turbine.stream?cluster=
  },
  "entryClusters": [  //required, some some initial clusters to tail
    "prod-proxy",
    "someinternalcomponent"
  ],
  "internetClusters": [  //optional, clusters that will be painted as receiving traffic from the internet
    "prod-proxy"
  ],
  "hystrixGroupToCluster": [  //optional, special mappings if the group doesn't match the hystrix cluster name
    {
      "group": "hystrix-prod-proxy",
      "cluster": "prod-proxy"
    }
  ]
}
```

**Running**

To run the app, send the config file as the first argument:
```
java -jar vizceral-hystrix-1.0.jar
```

