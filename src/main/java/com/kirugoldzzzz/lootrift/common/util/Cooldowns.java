package com.kirugoldzzzz.lootrift.common.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Cooldowns<K> {

    private static final int PRUNE_THRESHOLD = 512;

    private final Map<K, Long> expiries = new ConcurrentHashMap<>();

    public boolean isActive(K key) {
        return remaining(key) > 0L;
    }

    public long remaining(K key) {
        Long expiry = expiries.get(key);
        if (expiry == null) {
            return 0L;
        }
        long remaining = expiry - System.currentTimeMillis();
        if (remaining <= 0L) {
            expiries.remove(key);
            return 0L;
        }
        return remaining;
    }

    public void apply(K key, long millis) {
        if (millis <= 0L) {
            expiries.remove(key);
            return;
        }
        long now = System.currentTimeMillis();
        if (expiries.size() >= PRUNE_THRESHOLD) {
            expiries.values().removeIf(expiry -> expiry <= now);
        }
        expiries.put(key, now + millis);
    }

    public int size() {
        return expiries.size();
    }

    public void clear(K key) {
        expiries.remove(key);
    }
}
