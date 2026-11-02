package io.github.jazzybeen.androiddynamicisland.core;

/** Time-based shared motion: emerge, expand at 70%, a soft squeeze, reversed dismissal. */
public final class IslandMotion {
    public static final double SLIDE = 220, EXPAND = 280, SETTLE = 200, EXPOSURE = 32;
    public static final double EXPAND_START = expandStart(24, 8);
    public static final double OPEN = EXPAND_START + EXPAND;
    public record Frame(float width, float height, float top, float expansion) {}
    public static double expandStart(float smallHeight, float top) {
        double visibleTravel = smallHeight * .7 / (smallHeight + top);
        return SLIDE * (.5 - Math.sin(Math.asin(1 - 2 * visibleTravel) / 3));
    }
    public static double duration(float smallHeight, float top) { return expandStart(smallHeight, top) + EXPAND; }
    public static double ease(double t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }
    public static double settle(double elapsed) {
        double t = elapsed / SETTLE;
        if (t <= 0 || t >= 1) return 0;
        return t < .35 ? ease(t / .35) : 1 - ease((t - .35) / .65);
    }
    public static Frame frame(double phase, float smallWidth, float smallHeight, float width, float height, float top, boolean closing) {
        double start = expandStart(smallHeight, top);
        double expand = ease((phase - start) / EXPAND);
        double pulse = closing ? 0 : settle(phase - start - EXPAND);
        float w = (float)((smallWidth + (width - smallWidth) * expand) * (1 - .02 * pulse));
        float h = (float)((smallHeight + (height - smallHeight) * expand) * (1 + .05 * pulse));
        float y = (float)(top - (1 - ease(phase / SLIDE)) * (h + top));
        return new Frame(w, h, y, (float)expand);
    }
}
