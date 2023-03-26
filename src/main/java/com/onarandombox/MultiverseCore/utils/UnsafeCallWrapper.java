package com.onarandombox.MultiverseCore.utils;

import com.dumptruckman.minecraft.util.Logging;
import com.onarandombox.MultiverseCore.config.MVCoreConfigProvider;
import jakarta.inject.Inject;
import org.jvnet.hk2.annotations.Service;

import java.util.concurrent.Callable;

/**
 * Wraps calls that could result in exceptions that are not Multiverse's fault.
 */
@Service
public class UnsafeCallWrapper {
    private final MVCoreConfigProvider configProvider;

    @Inject
    public UnsafeCallWrapper(MVCoreConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    /**
     * Wraps calls that could result in exceptions that are not Multiverse's fault.
     *
     * @param callable The potentially unsafe call.
     * @param plugin The plugin that's probably the culprit.
     * @param action What MV was attempting to do (error message, format string).
     * @param formatArgs The formatting arguments for the error message.
     *                   The exception that was thrown will be appended to these objects.
     * @param <T> The type of the return value.
     * @return The return value or null if the call failed.
     */
    public <T> T wrap(Callable<T> callable, String plugin, String action, Object... formatArgs) {
        try {
            // We're ready to catch you! JUMP!
            return callable.call();
        } catch (Throwable t) {
            Object[] actualFormatArgs = new Object[formatArgs.length + 1];
            System.arraycopy(formatArgs, 0, actualFormatArgs, 0, formatArgs.length);
            actualFormatArgs[formatArgs.length] = t;
                Logging.warning(action, actualFormatArgs);
            Logging.warning("This is a bug in %s, NOT a bug in Multiverse!", plugin);
            if (configProvider.getConfig().getGlobalDebug() >= 1)
                t.printStackTrace();
            return null;
        }
    }
}
