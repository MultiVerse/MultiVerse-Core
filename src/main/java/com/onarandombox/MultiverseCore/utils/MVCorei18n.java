package com.onarandombox.MultiverseCore.utils;

import co.aikar.locales.MessageKey;
import co.aikar.locales.MessageKeyProvider;

public enum MVCorei18n implements MessageKeyProvider {
    // config status
    CONFIG_SAVE_FAILED,

    // check command
    CHECK_CHECKING,

    // clone command
    CLONE_CLONING,
    CLONE_FAILED,
    CLONE_SUCCESS,

    // create command
    CREATE_PROPERTIES,
    CREATE_PROPERTIES_ENVIRONMENT,
    CREATE_PROPERTIES_SEED,
    CREATE_PROPERTIES_WORLDTYPE,
    CREATE_PROPERTIES_ADJUSTSPAWN,
    CREATE_PROPERTIES_GENERATOR,
    CREATE_PROPERTIES_STRUCTURES,
    CREATE_LOADING,
    CREATE_FAILED,
    CREATE_SUCCESS,

    // delete command
    DELETE_DELETING,
    DELETE_FAILED,
    DELETE_SUCCESS,
    DELETE_PROMPT,

    // gamerule command
    GAMERULE_FAILED,
    GAMERULE_SUCCESS_SINGLE,
    GAMERULE_SUCCESS_MULTIPLE,

    // import command
    IMPORT_IMPORTING,
    IMPORT_FAILED,
    IMPORT_SUCCESS,

    // load command
    LOAD_LOADING,
    LOAD_FAILED,
    LOAD_SUCCESS,

    // regen command
    REGEN_REGENERATING,
    REGEN_FAILED,
    REGEN_SUCCESS,
    REGEN_PROMPT,

    // reload command
    RELOAD_RELOADING,
    RELOAD_SUCCESS,

    // remove command
    REMOVE_FAILED,
    REMOVE_SUCCESS,

    // root MV command
    ROOT_TITLE,
    ROOT_HELP,

    // teleport command
    TELEPORT_SUCCESS,

    // unload command
    UNLOAD_UNLOADING,
    UNLOAD_FAILURE,
    UNLOAD_SUCCESS,

    // debug command
    DEBUG_INFO_OFF,
    DEBUG_INFO_ON;

    private final MessageKey key = MessageKey.of("mv-core." + this.name().replace('_', '.').toLowerCase());

    @Override
    public MessageKey getMessageKey() {
        return this.key;
    }
}
