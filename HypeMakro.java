package pl.hypemakro;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class HypeMakro implements ClientModInitializer {
    public static final String MOD_ID = "hype_makro";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Stale klawisze (nie ma ich w Opcje -> Sterowanie)
    private static final int KEY_FAST = GLFW.GLFW_KEY_MINUS;      // -  szybkie stawianie
    private static final int KEY_MENU = GLFW.GLFW_KEY_BACKSLASH;  // \  menu
    private static final int KEY_WATER = GLFW.GLFW_KEY_H;         // H  woda + blok
    private static final int KEY_COLLECT = GLFW.GLFW_KEY_J;       // J  zbieranie wody

    private static final Map<Integer, Boolean> held = new HashMap<>();

    @Override
    public void onInitializeClient() {
        MakroConfig.load();
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
    }

    /** true tylko w momencie wcisniecia klawisza (bez czatu i innych okien). */
    private static boolean pressed(MinecraftClient client, int key) {
        boolean now = client.currentScreen == null
            && InputUtil.isKeyPressed(client.getWindow().getHandle(), key);
        boolean was = held.getOrDefault(key, false);
        held.put(key, now);
        return now && !was;
    }

    private void onEndTick(MinecraftClient client) {
        if (!client.options.useKey.isPressed()) {
            FastPlace.resetTiming();
        }

        boolean fast = pressed(client, KEY_FAST);
        boolean menu = pressed(client, KEY_MENU);
        boolean water = pressed(client, KEY_WATER);
        boolean collect = pressed(client, KEY_COLLECT);

        if (fast) {
            MakroConfig.enabled = !MakroConfig.enabled;
            MakroConfig.save();
        }
        if (water) {
            MakroConfig.waterPlace = !MakroConfig.waterPlace;
            MakroConfig.save();
        }
        if (collect) {
            MakroConfig.autoCollect = !MakroConfig.autoCollect;
            MakroConfig.save();
        }
        if (menu && client.currentScreen == null) {
            client.setScreen(new MakroScreen(null));
        }

        WaterPlace.tick(client);
        WaterPlace.collectTick(client);
    }
}
