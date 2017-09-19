package vizceral.hystrix;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for the max volume sliding window
 */
public class MaxVolumeTest
{

    private final String command1 = "command1";
    private final String command2 = "command2";
    private HystrixCluster cluster;

    @Before
    public void before()
    {
        cluster = new HystrixCluster("name", 1);
    }

    @Test
    public void correctlySumsUpTwoDifferentCommands()
    {
        cluster.addEvent(HystrixEvent.newBuilder().name(command1).totalRequestCount(10).build());
        cluster.addEvent(HystrixEvent.newBuilder().name(command2).totalRequestCount(3).build());
        assertEquals(13, cluster.getMaxValue());

        //Last value of this command is now 0, so current volume is 3. Max should still be 13
        cluster.addEvent(HystrixEvent.newBuilder().name(command1).totalRequestCount(0).build());
        assertEquals(13, cluster.getMaxValue());

        //More traffic for this command, max should now be 14
        cluster.addEvent(HystrixEvent.newBuilder().name(command1).totalRequestCount(11).build());
        assertEquals(14, cluster.getMaxValue());
    }

    @Test
    public void noTrafficForAwhileReturnsItAsZero() throws InterruptedException
    {
        cluster.addEvent(HystrixEvent.newBuilder().name(command1).totalRequestCount(10).build());
        cluster.addEvent(HystrixEvent.newBuilder().name(command2).totalRequestCount(10).build());
        assertEquals(20, cluster.getMaxValue());
        Thread.sleep(2000);
        assertEquals(0, cluster.getMaxValue());
    }
}
