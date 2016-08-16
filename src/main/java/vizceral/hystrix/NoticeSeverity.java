package vizceral.hystrix;

/**
 * Severity for a notice.
 */
public enum NoticeSeverity
{
    INFO(0),
    WARNING(1),
    ERROR(2);
    private final int level;

    NoticeSeverity(int level)
    {
        this.level = level;
    }

    public int get()
    {
        return level;
    }
}
