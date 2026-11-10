package com.astralspace;

import java.util.Map;

/** Generated: moving-part descriptors for the 3D Uniqueness items (baked 0..1 model units). */
final class UniqAnim {
    private UniqAnim() {}
    record Part(String name, String type, float[] v, float speed) {}
    static final Map<String, Part[]> PARTS = Map.ofEntries(
        Map.entry("starry_sniper", new Part[]{new Part("bolt","slide",new float[]{-0.14487f,-0.02937f,0.0f},0f)}),
        Map.entry("shadow_sword", new Part[]{}),
        Map.entry("shadow_shield", new Part[]{}),
        Map.entry("spear_of_sun", new Part[]{new Part("sun","spin",new float[]{0.74106f,0.74106f,0.5f},-0.9f)}),
        Map.entry("storm_katana", new Part[]{}),
        Map.entry("bone_greatsword", new Part[]{}),
        Map.entry("snake_rifle", new Part[]{new Part("wheel","spin",new float[]{0.42256f,0.4929f,0.5f},1.2f)}),
        Map.entry("chained_bow", new Part[]{new Part("string","pull",new float[]{-0.16019f,-0.05847f,0.0f},0f)}),
        Map.entry("twilight_longsword", new Part[]{})
    );
}
