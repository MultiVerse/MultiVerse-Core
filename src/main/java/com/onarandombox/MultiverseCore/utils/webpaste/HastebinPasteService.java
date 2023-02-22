package com.onarandombox.MultiverseCore.utils.webpaste;

import java.io.IOException;
import java.util.Map;

import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;

/**
 * Pastes to {@code hastebin.com}.
 */
class HastebinPasteService extends PasteService {
    private static final String HASTEBIN_POST_REQUEST = "https://hastebin.com/documents";

    HastebinPasteService() {
        super(HASTEBIN_POST_REQUEST);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    String encodeData(String data) {
        return data;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    String encodeData(Map<String, String> data) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String postData(String data) throws PasteFailedException {
        try {
            String stringJSON = this.exec(encodeData(data), ContentType.PLAINTEXT);
            return "https://hastebin.com/" + ((JSONObject) new JSONParser().parse(stringJSON)).get("key");
        } catch (IOException | ParseException e) {
            throw new PasteFailedException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String postData(Map<String, String> data) throws PasteFailedException {
        try {
            String stringJSON = this.exec(encodeData(data), ContentType.PLAINTEXT);
            return "https://hastebin.com/" + ((JSONObject) new JSONParser().parse(stringJSON)).get("key");
        } catch (IOException | ParseException e) {
            throw new PasteFailedException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean supportsMultiFile() {
        return false;
    }
}
