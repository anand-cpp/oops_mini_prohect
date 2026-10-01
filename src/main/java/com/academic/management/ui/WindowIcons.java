package com.academic.management.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * The window icon, drawn in code rather than loaded from a file.
 *
 * <h2>Why not ship a .png</h2>
 * A binary resource in the repository is one more thing that can go missing:
 * a build that filters resources, a path that differs on Windows, an IDE
 * that does not copy it. Drawing the icon at start-up means the jar contains
 * only classes, and there is no failure mode where the application runs
 * with a broken image icon. It is also fifteen lines of drawing against a
 * hundred lines of encoding a file, and it stays sharp at any size because
 * the same source is rasterised at whatever the platform asks for.
 */
final class WindowIcons {

    private static final int SIZE = 64;

    /** Drawn once, since every window asks for the same icon. */
    private static volatile BufferedImage cached;

    private WindowIcons() {
        // factory holder
    }

    /**
     * The application icon: a rounded navy tile with an open book, which
     * still reads as "academic" at the sixteen pixels the task bar uses.
     *
     * @return the icon, never null
     */
    static java.awt.Image appIcon() {
        BufferedImage image = cached;
        if (image == null) {
            synchronized (WindowIcons.class) {
                image = cached;
                if (image == null) {
                    image = draw();
                    cached = image;
                }
            }
        }
        return image;
    }

    private static BufferedImage draw() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g.setColor(new Color(0x1F, 0x2A, 0x44));
            g.fillRoundRect(0, 0, SIZE, SIZE, 14, 14);

            // Two facing pages, drawn as a single open book.
            g.setColor(Color.WHITE);
            g.fillPolygon(new int[]{12, 31, 31, 12}, new int[]{20, 24, 44, 40}, 4);
            g.fillPolygon(new int[]{33, 52, 52, 33}, new int[]{24, 20, 40, 44}, 4);
            g.setColor(new Color(0x1F, 0x2A, 0x44));
            g.fillRect(31, 23, 2, 21);

            g.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g.setColor(new Color(0x9F, 0xB4, 0xD8));
            g.drawString("A", 22, 57);
        } finally {
            g.dispose();
        }
        return image;
    }
}
