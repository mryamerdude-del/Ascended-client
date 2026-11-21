package dev.ascend.client.shared.combat;

public enum AimVector {
    STRAIGHT("Straight"),
    CLOSEST("Closest");

    private final String displayName;

    AimVector(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
