package com.kirugoldzzzz.lootrift;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrateBulkAnimationTest {

    @Test
    void everyPatternRevealsEachSlotExactlyOnce() {
        for (CrateBulkAnimation animation : CrateBulkAnimation.values()) {
            for (int size = 0; size <= 45; size++) {
                List<Integer> slots = new ArrayList<>();
                for (int slot = 0; slot < size; slot++) {
                    slots.add(slot);
                }
                List<Integer> order = animation.order(slots);
                assertEquals(size, order.size(), animation + " avec " + size);
                assertEquals(new HashSet<>(slots), new HashSet<>(order), animation + " avec " + size);
            }
        }
    }
}
