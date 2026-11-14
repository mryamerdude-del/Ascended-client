package dev.ascend.client.shared;

import java.util.Objects;

public final class AscendBootstrap {
    private AscendBootstrap() {
    }

    public static String initializationMessage(String minecraftVersion) {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        return "Ascend Client bootstrap initialized for Minecraft " + minecraftVersion;
    }
}
