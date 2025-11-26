package com.arcana.cloud.plugin.web;

/**
 * Represents a web resource (CSS, JavaScript, etc.) provided by a plugin.
 */
public class WebResource {

    private final String key;
    private final ResourceType type;
    private final String location;
    private final String[] dependencies;
    private final int weight;

    private WebResource(Builder builder) {
        this.key = builder.key;
        this.type = builder.type;
        this.location = builder.location;
        this.dependencies = builder.dependencies;
        this.weight = builder.weight;
    }

    /**
     * Returns the resource key.
     *
     * @return the key
     */
    public String getKey() {
        return key;
    }

    /**
     * Returns the resource type.
     *
     * @return the type
     */
    public ResourceType getType() {
        return type;
    }

    /**
     * Returns the resource location within the plugin bundle.
     *
     * @return the location
     */
    public String getLocation() {
        return location;
    }

    /**
     * Returns resource keys this resource depends on.
     *
     * @return array of dependency keys
     */
    public String[] getDependencies() {
        return dependencies;
    }

    /**
     * Returns the weight for ordering.
     *
     * @return the weight
     */
    public int getWeight() {
        return weight;
    }

    /**
     * Creates a new builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Resource types.
     */
    public enum ResourceType {
        /**
         * CSS stylesheet.
         */
        CSS("text/css"),

        /**
         * JavaScript file.
         */
        JAVASCRIPT("application/javascript"),

        /**
         * Image resource.
         */
        IMAGE("image/*"),

        /**
         * Font resource.
         */
        FONT("font/*"),

        /**
         * Other resource type.
         */
        OTHER("application/octet-stream");

        private final String mimeType;

        ResourceType(String mimeType) {
            this.mimeType = mimeType;
        }

        public String getMimeType() {
            return mimeType;
        }
    }

    /**
     * Builder for WebResource.
     */
    public static class Builder {
        private String key;
        private ResourceType type = ResourceType.OTHER;
        private String location;
        private String[] dependencies = new String[0];
        private int weight = 100;

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder type(ResourceType type) {
            this.type = type;
            return this;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder dependencies(String... dependencies) {
            this.dependencies = dependencies;
            return this;
        }

        public Builder weight(int weight) {
            this.weight = weight;
            return this;
        }

        public WebResource build() {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Resource key is required");
            }
            if (location == null || location.isBlank()) {
                throw new IllegalArgumentException("Resource location is required");
            }
            return new WebResource(this);
        }
    }
}
