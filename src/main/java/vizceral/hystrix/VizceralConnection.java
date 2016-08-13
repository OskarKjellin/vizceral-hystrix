package vizceral.hystrix;

/**
 * A connection between two nodes in Vizceral.
 */
public class VizceralConnection
{
    private final String name;
    private final int errors;
    private final int requests;
    private final int timeouts;

    /**
     * Creates a new Vizceral connection.
     *
     * @param name     The name of the cluster this connection is to.
     * @param errors   The number of errors seen in the period.
     * @param requests The number of successful requests seen in the period.
     * @param timeouts The number of timeouts seen in the period.
     */
    public VizceralConnection(String name, int errors, int requests, int timeouts)
    {
        this.name = name;
        this.errors = errors;
        this.requests = requests;
        this.timeouts = timeouts;
    }

    /**
     * Gets the name of the conncetion this is to.
     *
     * @return The target cluster name.
     */
    public String getName()
    {
        return name;
    }

    /**
     * Gets the number of errors seen in the period.
     *
     * @return Number of errors.
     */
    public int getErrors()
    {
        return errors;
    }

    /**
     * Gets the number of successful requests seen in the period.
     *
     * @return Number of successful requests
     */
    public int getRequests()
    {
        return requests;
    }

    /**
     * Gets the number of timeouts seen in the period.
     *
     * @return The number of timeouts.
     */
    public int getTimeouts()
    {
        return timeouts;
    }

}
