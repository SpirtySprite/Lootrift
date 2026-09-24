package com.kirugoldzzzz.lootrift;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrateRewardTypesTest {

    @Test
    void moneyAcceptsNumbersAndRanges() {
        assertArrayEquals(new double[]{250.0D, 250.0D}, CrateService.moneyRange(250));
        assertArrayEquals(new double[]{100.0D, 500.0D}, CrateService.moneyRange("100-500"));
        assertArrayEquals(new double[]{100.0D, 500.0D}, CrateService.moneyRange("500 - 100"));
        assertArrayEquals(new double[]{12.5D, 12.5D}, CrateService.moneyRange("12.5"));
        assertArrayEquals(new double[]{0.0D, 0.0D}, CrateService.moneyRange("lots"));
        assertArrayEquals(new double[]{0.0D, 0.0D}, CrateService.moneyRange(null));
    }

    @Test
    void rolledMoneyStaysInsideTheRange() {
        CrateReward reward = new CrateReward("pouch", null, false, 10, 1, 1, 100.0D, List.of(), CrateRarity.RARE,
                null, null, false, 500.0D, 30);
        assertTrue(reward.hasMoney());
        assertTrue(reward.hasXp());
        for (int round = 0; round < 500; round++) {
            double paid = reward.rollMoney();
            assertTrue(paid >= 100.0D && paid <= 500.0D, String.valueOf(paid));
        }
        CrateReward fixed = new CrateReward("coin", null, false, 10, 1, 1, 40.0D, List.of(), CrateRarity.COMMUN,
                null, null, false);
        assertEquals(40.0D, fixed.rollMoney());
        assertEquals(40.0D, fixed.moneyMax());
        assertFalse(fixed.hasXp());
    }

    @Test
    void customItemReferencesAreRecognised() {
        assertTrue(ExternalItems.isReference("itemsadder:ruby"));
        assertTrue(ExternalItems.isReference("MMOItems:SWORD:EXCALIBUR"));
        assertTrue(ExternalItems.isReference("nexo:crystal_key"));
        assertFalse(ExternalItems.isReference("minecraft:diamond"));
        assertFalse(ExternalItems.isReference("diamond"));
        assertFalse(ExternalItems.isReference(null));
        assertTrue(ExternalItems.resolve("oraxen:missing").isEmpty());
    }
}
