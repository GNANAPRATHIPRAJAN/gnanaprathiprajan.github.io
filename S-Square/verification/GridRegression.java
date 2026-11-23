package com.rajan.rsflow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

/** Run with TileLayout.java using JDK 17 or later. */
public final class GridRegression {
    public static void main(String[] args) {
        Random random = new Random(220);
        for (int test = 0; test < 1000; test++) {
            int columns = 2 + 2 * random.nextInt(5);
            List<TileLayout.Span> spans = new ArrayList<>();
            for (int i = 0; i < 40; i++)
                spans.add(TileLayout.span(1 + random.nextInt(6), columns));
            List<TileLayout.Cell> start = TileLayout.place(columns, spans, test % 2 == 0);
            int picked = random.nextInt(spans.size());
            TileLayout.Span size = spans.get(picked);
            TileLayout.Cell target = new TileLayout.Cell(random.nextInt(columns - size.columns() + 1),
                    random.nextInt(32), size.columns(), size.rows());
            List<TileLayout.Cell> drop = TileLayout.resolve(columns, spans, start, picked, target);
            require(target.equals(drop.get(picked)), "Incorrect drop cell");
            HashSet<Integer> occupied = new HashSet<>();
            for (int i = 0; i < spans.size(); i++) {
                TileLayout.Cell c = drop.get(i);
                require(c.width() == spans.get(i).columns() && c.height() == spans.get(i).rows(), "Resized tile");
                require(c.column() >= 0 && c.column() + c.width() <= columns && c.row() >= 0, "Outside grid");
                for (int y = c.row(); y < c.row() + c.height(); y++)
                    for (int x = c.column(); x < c.column() + c.width(); x++)
                        require(occupied.add(y * columns + x), "Overlapping tiles");
            }
            require(drop.equals(TileLayout.resolve(columns, spans, drop, -1, null)), "Restore shifted tiles");
            List<TileLayout.Cell> plusOne = new ArrayList<>(drop);
            plusOne.add(null);
            spans.add(new TileLayout.Span(1, 1));
            require(drop.equals(TileLayout.resolve(columns, spans, plusOne, -1, null).subList(0, drop.size())),
                    "A new pin shifted saved tiles");
        }
        System.out.println("Passed 1,000 mixed-size drop, restore and pin cases.");
    }

    private static void require(boolean valid, String problem) {
        if (!valid) throw new AssertionError(problem);
    }
}
