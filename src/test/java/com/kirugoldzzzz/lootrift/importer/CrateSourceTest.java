package com.kirugoldzzzz.lootrift.importer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrateSourceTest {

    private static final UUID PLAYER = UUID.fromString("0f0e0d0c-0b0a-0908-0706-050403020100");

    @TempDir
    Path folder;

    private void write(String path, String... lines) throws IOException {
        Path target = folder.resolve(path);
        Files.createDirectories(target.getParent());
        Files.writeString(target, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    }

    @Test
    void crazyCratesCurrentFormat() throws IOException {
        write("crates/Basic-Crate.yml",
                "Crate:",
                "  CrateType: Wheel",
                "  Name: \"<green>Basic Crate\"",
                "  Item: diamond",
                "  PhysicalKey:",
                "    Name: \"&aBasic Key\"",
                "    Item: lime_dye",
                "    Glowing: add_glow",
                "  Prizes:",
                "    \"1\":",
                "      Weight: 15.0",
                "      Items:",
                "        \"1\":",
                "          name: \"<red>Diamond Sword\"",
                "          material: diamond_sword",
                "          amount: 2",
                "          enchantments:",
                "            sharpness: 5",
                "    \"2\":",
                "      DisplayItem: gold_ingot",
                "      DisplayName: \"Money\"",
                "      Weight: 5.0",
                "      Commands:",
                "        - \"eco give %player% 500\"",
                "    \"3\":",
                "      DisplayItem: dirt",
                "      Weight: 0");
        write("data.yml",
                "Players:",
                "  " + PLAYER + ":",
                "    Name: Steve",
                "    Basic-Crate: 4",
                "Offline-Players:",
                "  " + PLAYER + ":",
                "    Basic-Crate: 1");

        Imported.Result result = new CrazyCratesSource().read(folder.toFile());

        assertEquals(1, result.crates().size());
        Imported.Crate crate = result.crates().getFirst();
        assertEquals("basic_crate", crate.id());
        assertEquals("<green>Basic Crate", crate.name());
        assertEquals("roue", crate.animation());
        assertEquals(true, crate.key().spec().get("glow"));
        assertFalse(((String) crate.key().spec().get("name")).contains("&"));
        assertEquals(2, crate.rewards().size());

        Imported.Reward sword = crate.rewards().get(0);
        assertTrue(sword.giveItem());
        assertEquals(15.0D, sword.weight());
        assertEquals(2, sword.item().amount());
        assertEquals("diamond_sword", sword.item().spec().get("material"));
        assertEquals(List.of("sharpness:5"), sword.item().spec().get("enchantments"));

        Imported.Reward money = crate.rewards().get(1);
        assertFalse(money.giveItem());
        assertEquals(List.of("eco give <player> 500"), money.commands());

        assertEquals(Map.of("basic_crate", 5), result.keys().get(PLAYER));
        assertEquals(1, result.warnings().size());
    }

    @Test
    void crazyCratesLegacyPrizes() throws IOException {
        write("crates/Old.yml",
                "Crate:",
                "  CrateType: CSGO",
                "  CrateName: \"&6Old\"",
                "  Prizes:",
                "    Apple:",
                "      Chance: 25",
                "      MaxRange: 100",
                "      Items:",
                "        - \"Item:GOLDEN_APPLE, Amount:3, Name:&6Shiny, Sharpness:2\"",
                "        - \"Item:STONE, Amount:1\"");

        Imported.Result result = new CrazyCratesSource().read(folder.toFile());
        Imported.Reward apple = result.crates().getFirst().rewards().getFirst();

        assertEquals(25.0D, apple.weight(), 1.0E-9);
        assertEquals("GOLDEN_APPLE", apple.item().spec().get("material"));
        assertEquals(3, apple.item().amount());
        assertEquals(List.of("sharpness:2"), apple.item().spec().get("enchantments"));
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("old.Apple")));
    }

    @Test
    void excellentCratesVanillaItemsAndCommands() throws IOException {
        write("crates/vote.yml",
                "Name: \"<gold>Vote\"",
                "ItemProvider:",
                "  Provider: vanilla",
                "  Data:",
                "    Value: '{count:1,id:\"minecraft:ender_chest\"}'",
                "    DataVersion: 4189",
                "CostOptions:",
                "  key_vote:",
                "    Entries:",
                "      \"0\":",
                "        Key: vote_key",
                "        Amount: 1",
                "Rewards:",
                "  List:",
                "    sword:",
                "      Type: ITEM",
                "      Weight: 40.0",
                "      Rarity: epic",
                "      Broadcast: true",
                "      ItemsData:",
                "        \"0\":",
                "          Provider: vanilla",
                "          Data:",
                "            Value: '{components:{\"minecraft:enchantments\":{levels:{\"minecraft:sharpness\":3}}},count:1,id:\"minecraft:iron_sword\"}'",
                "    money:",
                "      Type: COMMAND",
                "      Weight: 10.0",
                "      Name: Money",
                "      Commands:",
                "        - \"eco give %player_name% 100\"",
                "    custom:",
                "      Type: ITEM",
                "      Weight: 5.0",
                "      ItemsData:",
                "        \"0\":",
                "          Provider: itemsadder",
                "          Data: ruby");

        Imported.Result result = new ExcellentCratesSource().read(folder.toFile());
        Imported.Crate crate = result.crates().getFirst();

        assertEquals("vote", crate.id());
        assertEquals("minecraft:ender_chest", crate.icon().argument());
        assertEquals(2, crate.rewards().size());
        Imported.Reward sword = crate.rewards().get(0);
        assertEquals("epique", sword.rarity());
        assertTrue(sword.announce());
        assertEquals("minecraft:iron_sword[minecraft:enchantments={levels:{\"minecraft:sharpness\":3}}]",
                sword.item().argument());
        Imported.Reward money = crate.rewards().get(1);
        assertFalse(money.giveItem());
        assertEquals(List.of("eco give <player> 100"), money.commands());
        assertNull(money.rarity());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("itemsadder")));
        assertEquals(List.of("vote_key"), ExcellentCratesSource.keyIds(
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(folder.resolve("crates/vote.yml").toFile())));
    }

    @Test
    void excellentCratesKeyBalancesParse() {
        assertEquals(Map.of("vote_key", 3, "rare", 12), ExcellentCratesSource.balances("{\"vote_key\":3,\"rare\": 12}"));
        assertTrue(ExcellentCratesSource.balances(null).isEmpty());
    }

    @Test
    void missingFolderIsReportedNotThrown() {
        Imported.Result result = new ExcellentCratesSource().read(folder.resolve("nothing").toFile());
        assertTrue(result.crates().isEmpty());
        assertEquals(1, result.warnings().size());
    }
}
