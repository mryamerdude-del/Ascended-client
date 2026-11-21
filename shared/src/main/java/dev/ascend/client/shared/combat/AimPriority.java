package dev.ascend.client.shared.combat;

public enum AimPriority {
    NEAREST("Nearest"),
    FOV("FOV"),
    HURT_TICK("HurtTick");

    private final String displayName;

    AimPriority(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
