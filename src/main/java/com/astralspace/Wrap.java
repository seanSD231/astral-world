package com.astralspace;

import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

/** Client only: breaks on-screen text into lines that fit a width, at word boundaries. */
final class Wrap {
    private Wrap() {}

    static List<String> lines(Font font, String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String tryLine = cur.length() == 0 ? word : cur + " " + word;
            if (cur.length() > 0 && font.width(tryLine) > maxWidth) { out.add(cur.toString()); cur = new StringBuilder(word); }
            else cur = new StringBuilder(tryLine);
        }
        if (cur.length() > 0 || out.isEmpty()) out.add(cur.toString());
        return out;
    }
}
