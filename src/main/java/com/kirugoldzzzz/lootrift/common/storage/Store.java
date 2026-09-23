package com.kirugoldzzzz.lootrift.common.storage;

public interface Store {

    void load();

    void flush();

    void flushNow();
}
