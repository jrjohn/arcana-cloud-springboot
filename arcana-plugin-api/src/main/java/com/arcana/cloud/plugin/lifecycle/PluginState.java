package com.arcana.cloud.plugin.lifecycle;

/**
 * Represents the possible states of a plugin in its lifecycle.
 *
 * <p>Plugin state transitions follow this pattern:</p>
 * <pre>
 *     UNINSTALLED -> INSTALLED -> RESOLVED -> STARTING -> ACTIVE
 *                                     ^                     |
 *                                     |                     v
 *                                     +-- STOPPING <--------+
 * </pre>
 *
 * <p>These states correspond to OSGi bundle states but are abstracted
 * for plugin developers who may not be familiar with OSGi.</p>
 */
public enum PluginState {

    /**
     * The plugin bundle is not installed in the framework.
     */
    UNINSTALLED(0x00000001),

    /**
     * The plugin is installed but not yet resolved.
     * Dependencies may not be satisfied.
     */
    INSTALLED(0x00000002),

    /**
     * The plugin is resolved and ready to be started.
     * All dependencies are satisfied.
     */
    RESOLVED(0x00000004),

    /**
     * The plugin is in the process of starting.
     */
    STARTING(0x00000008),

    /**
     * The plugin is fully active and operational.
     */
    ACTIVE(0x00000020),

    /**
     * The plugin is in the process of stopping.
     */
    STOPPING(0x00000010);

    private final int osgiState;

    PluginState(int osgiState) {
        this.osgiState = osgiState;
    }

    /**
     * Returns the corresponding OSGi bundle state value.
     *
     * @return the OSGi state constant
     */
    public int getOsgiState() {
        return osgiState;
    }

    /**
     * Converts an OSGi bundle state to a PluginState.
     *
     * @param osgiState the OSGi bundle state
     * @return the corresponding PluginState
     */
    public static PluginState fromOsgiState(int osgiState) {
        for (PluginState state : values()) {
            if (state.osgiState == osgiState) {
                return state;
            }
        }
        return UNINSTALLED;
    }

    /**
     * Checks if the plugin is enabled (active or starting).
     *
     * @return true if the plugin is enabled
     */
    public boolean isEnabled() {
        return this == ACTIVE || this == STARTING;
    }

    /**
     * Checks if the plugin is usable (resolved, starting, or active).
     *
     * @return true if the plugin is usable
     */
    public boolean isUsable() {
        return this == RESOLVED || this == STARTING || this == ACTIVE;
    }
}
