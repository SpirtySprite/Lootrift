package com.kirugoldzzzz.lootrift.common.log;

import com.kirugoldzzzz.lootrift.common.text.Tr;

public enum LogTopic {

    GENERAL("general", Tr.t("Général")),
    STORAGE("storage", Tr.t("Stockage")),
    CONFIG("config", Tr.t("Configuration")),
    MENUS("menus", Tr.t("Menus")),
    CRATES("crates", Tr.t("Caisses"));

    private final String id;
    private final String label;

    LogTopic(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String permission() {
        return Alerts.ALL + "." + id;
    }
}
