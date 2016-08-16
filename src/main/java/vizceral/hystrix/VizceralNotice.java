package vizceral.hystrix;

/**
 * A notice on a connection.
 */
public class VizceralNotice
{
    private final String title;
    private final String subtitle;
    private final String link;
    private final NoticeSeverity severity;

    private VizceralNotice(Builder builder)
    {
        title = builder.title;
        subtitle = builder.subtitle;
        link = builder.link;
        severity = builder.severity;
    }

    public static Builder newBuilder()
    {
        return new Builder();
    }

    public static Builder newBuilder(VizceralNotice copy)
    {
        Builder builder = new Builder();
        builder.title = copy.title;
        builder.subtitle = copy.subtitle;
        builder.link = copy.link;
        builder.severity = copy.severity;
        return builder;
    }

    /**
     * The title to show.
     *
     * @return Title.
     */
    public String getTitle()
    {
        return title;
    }

    /**
     * The subtitle to show.
     *
     * @return Subtitle.
     */
    public String getSubtitle()
    {
        return subtitle;
    }

    /**
     * Gets the link to show.
     *
     * @return Link.
     */
    public String getLink()
    {
        return link;
    }

    /**
     * Gets the severity.
     *
     * @return Severity.
     */
    public NoticeSeverity getSeverity()
    {
        return severity;
    }

    public static final class Builder
    {
        private String title;
        private String subtitle;
        private String link;
        private NoticeSeverity severity;

        private Builder()
        {
        }

        public Builder title(String title)
        {
            this.title = title;
            return this;
        }

        public Builder subtitle(String subtitle)
        {
            this.subtitle = subtitle;
            return this;
        }

        public Builder link(String link)
        {
            this.link = link;
            return this;
        }

        public Builder severity(NoticeSeverity severity)
        {
            this.severity = severity;
            return this;
        }

        public VizceralNotice build()
        {
            return new VizceralNotice(this);
        }
    }
}
