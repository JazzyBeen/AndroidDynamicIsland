package io.github.jazzybeen.androiddynamicisland.core;

/** Resolves platform corner metadata, including devices that report only one corner. */
public final class IslandShape {
    private IslandShape() {}
    public static float radius(float left, float right, boolean automatic, float fallback) {
        if (!automatic) return Math.max(0, fallback);
        if (left > 0 && right > 0) return Math.min(left, right);
        if (left > 0) return left;
        if (right > 0) return right;
        return Math.max(0, fallback);
    }
}
