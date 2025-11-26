package com.arcana.cloud.plugin.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a menu item that can be added by plugins.
 */
public class MenuItem {

    private final String key;
    private final String label;
    private final String url;
    private final String icon;
    private final String location;
    private final int weight;
    private final String requiredPermission;
    private final List<MenuItem> children;

    private MenuItem(Builder builder) {
        this.key = builder.key;
        this.label = builder.label;
        this.url = builder.url;
        this.icon = builder.icon;
        this.location = builder.location;
        this.weight = builder.weight;
        this.requiredPermission = builder.requiredPermission;
        this.children = Collections.unmodifiableList(builder.children);
    }

    /**
     * Returns the menu item key.
     *
     * @return the key
     */
    public String getKey() {
        return key;
    }

    /**
     * Returns the display label.
     *
     * @return the label
     */
    public String getLabel() {
        return label;
    }

    /**
     * Returns the URL to navigate to.
     *
     * @return the URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * Returns the icon class or path.
     *
     * @return the icon
     */
    public String getIcon() {
        return icon;
    }

    /**
     * Returns the menu location.
     * Standard locations: main, sidebar, header, footer
     *
     * @return the location
     */
    public String getLocation() {
        return location;
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
     * Returns the required permission.
     *
     * @return the permission or null
     */
    public String getRequiredPermission() {
        return requiredPermission;
    }

    /**
     * Returns child menu items.
     *
     * @return list of children
     */
    public List<MenuItem> getChildren() {
        return children;
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
     * Builder for MenuItem.
     */
    public static class Builder {
        private String key;
        private String label;
        private String url;
        private String icon;
        private String location = "main";
        private int weight = 100;
        private String requiredPermission;
        private final List<MenuItem> children = new ArrayList<>();

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder icon(String icon) {
            this.icon = icon;
            return this;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder weight(int weight) {
            this.weight = weight;
            return this;
        }

        public Builder requiredPermission(String permission) {
            this.requiredPermission = permission;
            return this;
        }

        public Builder addChild(MenuItem child) {
            this.children.add(child);
            return this;
        }

        public MenuItem build() {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Menu item key is required");
            }
            if (label == null || label.isBlank()) {
                throw new IllegalArgumentException("Menu item label is required");
            }
            return new MenuItem(this);
        }
    }
}
