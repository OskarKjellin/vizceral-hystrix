package vizceral.hystrix.monitoring.zmon;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import vizceral.hystrix.ConfigurationException;

import java.util.Collections;
import java.util.Map;

/**
 * Configuration for zmon.
 */
@JsonDeserialize(builder = ZmonConfiguration.Builder.class)
public class ZmonConfiguration
{
    private final String host;
    private final int port;
    private final String path;
    private final boolean secure;
    private final Map<String, String> headers;

    private ZmonConfiguration(Builder builder)
    {
        host = builder.host;
        port = builder.port;
        path = builder.path == null ? "/" : (builder.path.endsWith("/") ? builder.path : builder.path + "/");
        secure = builder.secure;
        headers = builder.headers == null ? Collections.emptyMap() : Collections.unmodifiableMap(builder.headers);
        if (host == null)
        {
            throw new ConfigurationException("zmon.host cannot be null");
        }
        if (port <= 0)
        {
            throw new ConfigurationException("zmon.port must be set and larger than 0");
        }
    }

    public static Builder newBuilder()
    {
        return new Builder();
    }

    public String getHost()
    {
        return host;
    }

    public int getPort()
    {
        return port;
    }

    public String getPath()
    {
        return path;
    }

    public boolean isSecure()
    {
        return secure;
    }

    public Map<String, String> getHeaders()
    {
        return headers;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder
    {
        private String host;
        private int port;
        private String path;
        private boolean secure;
        private Map<String, String> headers;

        private Builder()
        {
        }

        @JsonSetter
        public Builder host(String val)
        {
            host = val;
            return this;
        }

        @JsonSetter
        public Builder port(int val)
        {
            port = val;
            return this;
        }

        @JsonSetter
        public Builder path(String val)
        {
            path = val;
            return this;
        }

        @JsonSetter
        public Builder secure(boolean val)
        {
            secure = val;
            return this;
        }

        @JsonSetter
        public Builder headers(Map<String, String> val)
        {
            headers = val;
            return this;
        }

        public ZmonConfiguration build()
        {
            return new ZmonConfiguration(this);
        }
    }
}
