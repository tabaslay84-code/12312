package pl.hypemakro;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MakroConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final int MIN_CPS = 1;
    public static final int MAX_CPS = 20;
    public static final int MAX_WATER_DELAY = 5;

    public static boolean enabled = true;
    public static int cps = 20;
    public static boolean onlyBlocks = true;

    // NOWE: tryb "woda + blok" i opoznienie miedzy krokami (w tickach)
    public static boolean waterPlace = false;
    public static int waterDelay = 1;

    // woda + blok dziala tylko gdy na klikanym bloku jest guzik / dzwignia
    public static boolean switchOnly = true;
    // J: automatyczne zbieranie wody pustym wiaderkiem
    public static boolean autoCollect = false;

    private MakroConfig() {}

    static class Data {
        boolean enabled = true;
        int cps = 20;
        int interval = 0;
        boolean onlyBlocks = true;
        boolean waterPlace = false;
        int waterDelay = 1;
        boolean switchOnly = true;
        boolean autoCollect = false;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("hype_makro.json");
    }

    public static void load() {
        Path file = file();
        if (!Files.exists(file)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data == null) return;
            enabled = data.enabled;
            int c = data.cps;
            if (c <= 0 && data.interval > 0) {
                c = Math.round(20f / data.interval);
            }
            cps = clampCps(c);
            onlyBlocks = data.onlyBlocks;
            waterPlace = data.waterPlace;
            waterDelay = clampWaterDelay(data.waterDelay);
            switchOnly = data.switchOnly;
            autoCollect = data.autoCollect;
        } catch (Exception e) {
            HypeMakro.LOGGER.warn("Nie udało się wczytać ustawień z {}", file, e);
        }
    }

    public static void save() {
        Data data = new Data();
        data.enabled = enabled;
        data.cps = cps;
        data.interval = 0;
        data.onlyBlocks = onlyBlocks;
        data.waterPlace = waterPlace;
        data.waterDelay = waterDelay;
        data.switchOnly = switchOnly;
        data.autoCollect = autoCollect;
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            HypeMakro.LOGGER.warn("Nie udało się zapisać ustawień do {}", file, e);
        }
    }

    public static int clampCps(int value) {
        return Math.max(MIN_CPS, Math.min(MAX_CPS, value));
    }

    public static int clampWaterDelay(int value) {
        return Math.max(0, Math.min(MAX_WATER_DELAY, value));
    }
}
