package dev.lumora.core.extras;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ready-made pixel crosshairs for the crosshair module. Each one is drawn on the {@value #GRID} x {@value #GRID} grid
 * that vanilla's crosshair uses; patterns with odd sizes are centred, so their middle pixel is the screen centre.
 */
public final class CrosshairPresets {
    /** Width and height of the pixel grid. */
    public static final int GRID = 15;

    /** One preset: id (stored in the settings), German and English name, pixels row by row. */
    public record Preset(String id, String nameDe, String nameEn, boolean[] pixels) {
    }

    private static final List<Preset> ALL;

    static {
        List<Preset> p = new ArrayList<>();
        p.add(of("plus_gap", "Kreuz mit Lücke", "Cross with gap",
                ".....#.....",
                ".....#.....",
                ".....#.....",
                "...........",
                "...........",
                "###.....###",
                "...........",
                "...........",
                ".....#.....",
                ".....#.....",
                ".....#....."));
        p.add(of("plus_dot", "Kreuz mit Punkt", "Cross with dot",
                ".....#.....",
                ".....#.....",
                ".....#.....",
                "...........",
                "...........",
                "###..#..###",
                "...........",
                "...........",
                ".....#.....",
                ".....#.....",
                ".....#....."));
        p.add(of("plus", "Kreuz", "Cross",
                "....#....",
                "....#....",
                "....#....",
                "....#....",
                "#########",
                "....#....",
                "....#....",
                "....#....",
                "....#...."));
        p.add(of("small_plus", "Kleines Kreuz", "Small cross",
                "..#..",
                "..#..",
                "#####",
                "..#..",
                "..#.."));
        p.add(of("scope", "Zielfernrohr", "Scope",
                ".......#.......",
                ".......#.......",
                ".......#.......",
                ".......#.......",
                ".......#.......",
                "...............",
                "...............",
                "#####..#..#####",
                "...............",
                "...............",
                ".......#.......",
                ".......#.......",
                ".......#.......",
                ".......#.......",
                ".......#......."));
        p.add(of("thick_plus", "Dickes Kreuz", "Thick cross",
                "...###...",
                "...###...",
                "...###...",
                "###...###",
                "###.#.###",
                "###...###",
                "...###...",
                "...###...",
                "...###..."));
        p.add(of("dot", "Punkt", "Dot",
                "###",
                "###",
                "###"));
        p.add(of("x", "X mit Lücke", "X with gap",
                "#.......#",
                ".#.....#.",
                "..#...#..",
                ".........",
                "....#....",
                ".........",
                "..#...#..",
                ".#.....#.",
                "#.......#"));
        p.add(of("x_full", "X", "X",
                "#.....#",
                ".#...#.",
                "..#.#..",
                "...#...",
                "..#.#..",
                ".#...#.",
                "#.....#"));
        p.add(of("circle_dot", "Kreis mit Punkt", "Circle with dot",
                "...#####...",
                "..#.....#..",
                ".#.......#.",
                "#.........#",
                "#.........#",
                "#....#....#",
                "#.........#",
                "#.........#",
                ".#.......#.",
                "..#.....#..",
                "...#####..."));
        p.add(of("circle", "Kreis", "Circle",
                "...###...",
                ".##...##.",
                ".#.....#.",
                "#.......#",
                "#.......#",
                "#.......#",
                ".#.....#.",
                ".##...##.",
                "...###..."));
        p.add(of("circle_small", "Kleiner Kreis", "Small circle",
                "..###..",
                ".#...#.",
                "#.....#",
                "#..#..#",
                "#.....#",
                ".#...#.",
                "..###.."));
        p.add(of("ring_cross", "Kreis mit Kreuz", "Circle with cross",
                "......#......",
                "......#......",
                "....#####....",
                "...#.....#...",
                "..#.......#..",
                "..#.......#..",
                "###...#...###",
                "..#.......#..",
                "..#.......#..",
                "...#.....#...",
                "....#####....",
                "......#......",
                "......#......"));
        p.add(of("dotted_circle", "Punktkreis", "Dotted circle",
                "....#....",
                ".#.....#.",
                ".........",
                ".........",
                "#...#...#",
                ".........",
                ".........",
                ".#.....#.",
                "....#...."));
        p.add(of("diamond", "Raute", "Diamond",
                "....#....",
                "...#.#...",
                "..#...#..",
                ".#.....#.",
                "#...#...#",
                ".#.....#.",
                "..#...#..",
                "...#.#...",
                "....#...."));
        p.add(of("small_diamond", "Kleine Raute", "Small diamond",
                "..#..",
                ".#.#.",
                "#.#.#",
                ".#.#.",
                "..#.."));
        p.add(of("brackets", "Ecken", "Corners",
                "###.....###",
                "#.........#",
                "#.........#",
                "...........",
                "...........",
                ".....#.....",
                "...........",
                "...........",
                "#.........#",
                "#.........#",
                "###.....###"));
        p.add(of("brackets_plus", "Ecken mit Kreuz", "Corners with cross",
                "###.....###",
                "#.........#",
                "#....#....#",
                ".....#.....",
                ".....#.....",
                "..#######..",
                ".....#.....",
                ".....#.....",
                "#....#....#",
                "#.........#",
                "###.....###"));
        p.add(of("corners_dot", "Kleine Ecken", "Small corners",
                "##...##",
                "#.....#",
                ".......",
                "...#...",
                ".......",
                "#.....#",
                "##...##"));
        p.add(of("square", "Quadrat", "Square",
                "#########",
                "#.......#",
                "#.......#",
                "#.......#",
                "#...#...#",
                "#.......#",
                "#.......#",
                "#.......#",
                "#########"));
        p.add(of("chevrons", "Pfeile seitlich", "Side arrows",
                "..#.......#..",
                ".#.........#.",
                "#.....#.....#",
                ".#.........#.",
                "..#.......#.."));
        p.add(of("chevrons_up", "Pfeile oben und unten", "Arrows up and down",
                "..#..",
                ".#.#.",
                "#...#",
                ".....",
                ".....",
                ".....",
                "..#..",
                ".....",
                ".....",
                ".....",
                "#...#",
                ".#.#.",
                "..#.."));
        p.add(of("t", "T", "T",
                "...........",
                "...........",
                "...........",
                "...........",
                "...........",
                "###..#..###",
                "...........",
                "...........",
                ".....#.....",
                ".....#.....",
                ".....#....."));
        p.add(of("arrow", "Pfeil", "Arrow",
                ".........",
                ".........",
                ".........",
                ".........",
                "....#....",
                ".........",
                "..#...#..",
                ".#.....#.",
                "#.......#"));
        p.add(of("star", "Stern", "Star",
                "#..#..#",
                ".#.#.#.",
                "..###..",
                "#######",
                "..###..",
                ".#.#.#.",
                "#..#..#"));
        p.add(of("bars", "Balken", "Bars",
                "#.........#",
                "#.........#",
                "#.........#",
                "#....#....#",
                "#.........#",
                "#.........#",
                "#.........#"));
        p.add(of("line", "Strich", "Line",
                "######.#.######"));
        ALL = Collections.unmodifiableList(p);
    }

    private CrosshairPresets() {
    }

    /** All presets in the order of the editor's grid. */
    public static List<Preset> all() {
        return ALL;
    }

    /** Ids of all presets (the options of the "preset" setting). */
    public static String[] ids() {
        return ALL.stream().map(Preset::id).toArray(String[]::new);
    }

    /** The preset with this id, or the first one. */
    public static Preset get(String id) {
        for (Preset p : ALL) {
            if (p.id().equals(id)) {
                return p;
            }
        }
        return ALL.get(0);
    }

    /** Centres a pattern of odd size ('#' = pixel) on the grid. */
    static Preset of(String id, String de, String en, String... rows) {
        int h = rows.length;
        int w = rows[0].length();
        if (h % 2 == 0 || w % 2 == 0 || h > GRID || w > GRID) {
            throw new IllegalArgumentException("preset " + id + ": odd size up to " + GRID + " needed");
        }
        boolean[] px = new boolean[GRID * GRID];
        int ox = (GRID - w) / 2;
        int oy = (GRID - h) / 2;
        for (int y = 0; y < h; y++) {
            if (rows[y].length() != w) {
                throw new IllegalArgumentException("preset " + id + ": row " + y + " has another length");
            }
            for (int x = 0; x < w; x++) {
                px[(oy + y) * GRID + ox + x] = rows[y].charAt(x) == '#';
            }
        }
        return new Preset(id, de, en, px);
    }

    /** Pixels as text: one group of four hex digits per row (bit 14 = left pixel). */
    public static String encode(boolean[] px) {
        StringBuilder b = new StringBuilder(GRID * 4);
        for (int y = 0; y < GRID; y++) {
            int row = 0;
            for (int x = 0; x < GRID; x++) {
                if (px[y * GRID + x]) {
                    row |= 1 << (GRID - 1 - x);
                }
            }
            b.append(String.format("%04x", row));
        }
        return b.toString();
    }

    /** Reads {@link #encode} text; null if it is not valid. */
    public static boolean[] decode(String s) {
        if (s == null || s.length() != GRID * 4) {
            return null;
        }
        boolean[] px = new boolean[GRID * GRID];
        for (int y = 0; y < GRID; y++) {
            int row;
            try {
                row = Integer.parseInt(s.substring(y * 4, y * 4 + 4), 16);
            } catch (NumberFormatException e) {
                return null;
            }
            for (int x = 0; x < GRID; x++) {
                px[y * GRID + x] = (row & (1 << (GRID - 1 - x))) != 0;
            }
        }
        return px;
    }
}
