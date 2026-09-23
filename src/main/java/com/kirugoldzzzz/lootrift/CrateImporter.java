package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.item.ItemSpec;
import com.kirugoldzzzz.lootrift.common.text.Tr;
import com.kirugoldzzzz.lootrift.importer.CrateSource;
import com.kirugoldzzzz.lootrift.importer.Imported;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class CrateImporter {

    record Summary(int crates, int rewards, int balances, List<String> warnings) {
    }

    private final CrateService service;
    private final CrateEditor editor;

    CrateImporter(CrateService service, CrateEditor editor) {
        this.service = service;
        this.editor = editor;
    }

    static File folder(CrateSource source) {
        return new File(Bukkit.getPluginsFolder(), source.plugin());
    }

    Summary write(Imported.Result result) {
        List<String> warnings = new ArrayList<>(result.warnings());
        Map<String, String> written = editor.importCrates(result.crates(), item -> build(item, warnings), warnings);
        int rewards = 0;
        for (Imported.Crate crate : result.crates()) {
            if (written.containsKey(crate.id())) {
                rewards += crate.rewards().size();
            }
        }
        int balances = 0;
        for (Map.Entry<UUID, Map<String, Integer>> player : result.keys().entrySet()) {
            for (Map.Entry<String, Integer> balance : player.getValue().entrySet()) {
                String crate = written.get(balance.getKey());
                if (crate != null) {
                    service.keyRepository().addKeys(player.getKey(), crate, balance.getValue());
                    balances++;
                }
            }
        }
        for (String warning : warnings) {
            CrateLog.warn("[" + result.source() + "] " + warning);
        }
        return new Summary(written.size(), rewards, balances, List.copyOf(warnings));
    }

    static ItemStack build(Imported.Item item, List<String> warnings) {
        ItemStack stack;
        if (item.argument() != null) {
            try {
                stack = Bukkit.getItemFactory().createItemStack(item.argument());
            } catch (IllegalArgumentException invalid) {
                warnings.add(Tr.t("Objet non reconnu, remplacé par de la pierre : ") + item.argument());
                stack = new ItemStack(Material.STONE);
            }
        } else {
            Object material = item.spec().get("material");
            if (material == null || Material.matchMaterial(material.toString()) == null) {
                warnings.add(Tr.t("Matériau inconnu, remplacé par de la pierre : ") + material);
            }
            YamlConfiguration spec = new YamlConfiguration();
            item.spec().forEach(spec::set);
            stack = ItemSpec.read(spec, Material.STONE);
        }
        stack.setAmount(1);
        return stack;
    }
}
