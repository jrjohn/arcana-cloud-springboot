package com.arcana.cloud.plugin.event;

import com.arcana.cloud.plugin.lifecycle.PluginState;

/**
 * Event fired when a plugin's lifecycle state changes.
 */
public class PluginLifecycleEvent extends PluginEvent {

    private final String pluginKey;
    private final String pluginName;
    private final PluginState previousState;
    private final PluginState newState;
    private final LifecycleAction action;

    /**
     * Creates a new plugin lifecycle event.
     *
     * @param pluginKey the plugin key
     * @param pluginName the plugin name
     * @param previousState the previous state
     * @param newState the new state
     * @param action the lifecycle action
     */
    public PluginLifecycleEvent(String pluginKey, String pluginName,
                                 PluginState previousState, PluginState newState,
                                 LifecycleAction action) {
        super();
        this.pluginKey = pluginKey;
        this.pluginName = pluginName;
        this.previousState = previousState;
        this.newState = newState;
        this.action = action;
    }

    /**
     * Returns the plugin key.
     *
     * @return the plugin key
     */
    public String getPluginKey() {
        return pluginKey;
    }

    /**
     * Returns the plugin name.
     *
     * @return the plugin name
     */
    public String getPluginName() {
        return pluginName;
    }

    /**
     * Returns the previous state.
     *
     * @return the previous state
     */
    public PluginState getPreviousState() {
        return previousState;
    }

    /**
     * Returns the new state.
     *
     * @return the new state
     */
    public PluginState getNewState() {
        return newState;
    }

    /**
     * Returns the lifecycle action.
     *
     * @return the action
     */
    public LifecycleAction getAction() {
        return action;
    }

    /**
     * Plugin lifecycle actions.
     */
    public enum LifecycleAction {
        /**
         * Plugin was installed.
         */
        INSTALLED,

        /**
         * Plugin was enabled (started).
         */
        ENABLED,

        /**
         * Plugin was disabled (stopped).
         */
        DISABLED,

        /**
         * Plugin was uninstalled.
         */
        UNINSTALLED,

        /**
         * Plugin was upgraded.
         */
        UPGRADED
    }
}
