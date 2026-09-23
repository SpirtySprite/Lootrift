package com.kirugoldzzzz.lootrift.importer;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public interface CrateSource {

    List<CrateSource> ALL = List.of(new CrazyCratesSource(), new ExcellentCratesSource());

    String id();

    String plugin();

    Imported.Result read(File pluginFolder);

    static Optional<CrateSource> byId(String id) {
        String wanted = id == null ? "" : id.toLowerCase(Locale.ROOT);
        return ALL.stream().filter(source -> source.id().equals(wanted)).findFirst();
    }

    static String slug(String raw) {
        String slug = raw == null ? "" : raw.toLowerCase(Locale.ROOT).trim().replace(' ', '_').replace('-', '_')
                .replaceAll("[^a-z0-9_]", "");
        return slug.isEmpty() ? "imported" : slug;
    }
}
