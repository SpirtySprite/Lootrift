package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.text.Numbers;
import com.kirugoldzzzz.lootrift.common.text.Palette;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrateMenuStyleTest {

    @BeforeEach
    void resetFormatting() {
        Numbers.configure(Palette.COIN, "#,##0.00");
    }

    @Test
    void guaranteedAndCommonRewardsDoNotShowMisleadingOdds() {
        assertEquals(" (garanti)", CrateIcons.odds(100.0D));
        assertEquals("", CrateIcons.odds(50.0D));
        assertEquals("", CrateIcons.odds(0.0D));
    }

    @Test
    void rareRewardsShowReadableOneInOdds() {
        assertEquals(" (1 sur 40)", CrateIcons.odds(2.5D));
        assertEquals(" (1 sur 1,000)", CrateIcons.odds(0.1D));
    }

    private static CrateReward reward(String id, CrateRarity rarity) {
        return new CrateReward(id, null, false, 10, 1, 1, 0.0D, List.of(), rarity, null, null, false);
    }

    private static Crate crate(List<CrateReward> rewards) {
        return new Crate("test", "Test", null, Material.CHEST, null, null, rewards, 1, false, null, 0, null,
                false, List.of(), null, 0, 0.0D, false, List.of(), null, null);
    }

    @Test
    void previewFrameTakesTheColourOfTheBestReward() {
        assertEquals(CrateRarity.LEGENDAIRE, CratePreviewMenu.theme(crate(List.of(
                reward("a", CrateRarity.COMMUN), reward("b", CrateRarity.LEGENDAIRE), reward("c", CrateRarity.RARE)))));
        assertEquals(CrateRarity.COMMUN, CratePreviewMenu.theme(crate(List.of(reward("a", CrateRarity.COMMUN)))));
        assertEquals(CrateRarity.RARE, CratePreviewMenu.theme(crate(List.of())));
    }

    @Test
    void previewFrameAlternatesThemeAndBlackPanes() {
        Material accent = Material.ORANGE_STAINED_GLASS_PANE;
        Material black = Material.BLACK_STAINED_GLASS_PANE;
        assertEquals(accent, CratePreviewMenu.framePane(CrateRarity.LEGENDAIRE, 1, 1));
        assertEquals(black, CratePreviewMenu.framePane(CrateRarity.LEGENDAIRE, 1, 2));
        assertEquals(accent, CratePreviewMenu.framePane(CrateRarity.LEGENDAIRE, 1, 9));
        assertEquals(accent, CratePreviewMenu.framePane(CrateRarity.LEGENDAIRE, 6, 1));
        assertEquals(accent, CratePreviewMenu.framePane(CrateRarity.LEGENDAIRE, 6, 9));
        assertEquals(black, CratePreviewMenu.framePane(CrateRarity.LEGENDAIRE, 6, 8));
        assertEquals(black, CratePreviewMenu.framePane(CrateRarity.LEGENDAIRE, 2, 1));
        assertEquals(accent, CratePreviewMenu.framePane(CrateRarity.LEGENDAIRE, 3, 9));
        assertEquals(accent, CratePreviewMenu.framePane(CrateRarity.LEGENDAIRE, 4, 1));
        assertEquals(black, CratePreviewMenu.framePane(CrateRarity.LEGENDAIRE, 5, 9));
    }
}
