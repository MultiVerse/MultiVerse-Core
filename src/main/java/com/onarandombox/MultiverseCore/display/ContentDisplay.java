package com.onarandombox.MultiverseCore.display;

import com.onarandombox.MultiverseCore.display.handlers.DefaultSendHandler;
import com.onarandombox.MultiverseCore.display.handlers.SendHandler;
import com.onarandombox.MultiverseCore.display.parsers.ContentParser;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Helps to display contents such as list and maps in a nicely formatted fashion.
 */
public class ContentDisplay {

    /**
     * Makes a new {@link ContentDisplay} instance to use.
     *
     * @return  New {@link ContentDisplay} instance.
     */
    @NotNull
    public static ContentDisplay create() {
        return new ContentDisplay();
    }

    private final List<ContentParser> contentParsers = new ArrayList<>();
    private SendHandler sendHandler = DefaultSendHandler.getInstance();

    public ContentDisplay() {
    }

    /**
     * Adds content to be displayed.
     *
     * @param parser    The content parser to add.
     * @return Same {@link ContentDisplay} for method chaining.
     */
    @NotNull
    public ContentDisplay addContentParser(@NotNull ContentParser parser) {
        contentParsers.add(parser);
        return this;
    }

    /**
     * Sets the handler for displaying the message to command sender.
     *
     * @param handler   The send handler to use.
     * @return Same {@link ContentDisplay} for method chaining.
     */
    @NotNull
    public ContentDisplay withSendHandler(@NotNull SendHandler handler) {
        sendHandler = handler;
        return this;
    }

    /**
     * Format and display the message to command sender.
     *
     * @param sender    The target command sender to show the display to.
     */
    public void send(@NotNull CommandSender sender) {
        Objects.requireNonNull(sendHandler, "No send handler set for content display");
        List<String> parsedContent = new ArrayList<>();
        contentParsers.forEach(parser -> parser.parse(sender, parsedContent));
        sendHandler.send(sender, parsedContent);
    }
}
