/******************************************************************************
 * Multiverse 2 Copyright (c) the Multiverse Team 2011.                       *
 * Multiverse 2 is licensed under the BSD License.                            *
 * For more information please check the README.md file included              *
 * with this project.                                                         *
 ******************************************************************************/

package com.onarandombox.MultiverseCore.api;

/**
 * This interface is implemented by every official Multiverse-plugin.
 */
public interface MVPlugin {
    /**
     * Gets the reference to MultiverseCore.
     *
     * @return A valid {@link com.onarandombox.MultiverseCore}.
     */
    MVCore getCore();

    /**
     * Allows Multiverse or a plugin to query another Multiverse plugin to see what version its protocol is. This
     * number
     * should change when something will break the code.
     *
     * @return The Integer protocol version.
     */
    int getProtocolVersion();

    /**
     * Parse the Authors Array into a readable String with ',' and 'and'.
     *
     * @return The readable authors-{@link String}
     */
    String getAuthors();
}
