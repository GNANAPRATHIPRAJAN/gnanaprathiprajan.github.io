package com.rajan.rsflow;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

final class TileLayout {
    static final int SMALL = 1, STANDARD = 2, WIDE = 3, LARGE = 4, TALL = 5, FULL = 6;

    record Span(int columns, int rows) {}

    record Cell(int column, int row, int width, int height) {}

    static Span span(int size, int columns) {
        return switch (size) {
            case SMALL -> new Span(1, 1);
            case WIDE -> new Span(Math.min(4, columns), 2);
            case LARGE -> new Span(Math.min(4, columns), 4);
            case TALL -> new Span(Math.min(2, columns), 4);
            case FULL -> new Span(columns, 2);
            default -> new Span(Math.min(2, columns), 2);
        };
    }

    static List<Cell> place(int columns, List<Span> spans) {
        return place(columns, spans, false);
    }

    // Reserve the dragged footprint first, then all existing non-overlapping footprints.
    // Only the displaced tiles need new cells. A missing saved location is a new pin.
    static List<Cell> resolve(
            int columns, List<Span> spans, List<Cell> anchors, int source, Cell drop) {
        BitSet occupied = new BitSet();
        ArrayList<Cell> result = new ArrayList<>(java.util.Collections.nCopies(spans.size(), null));
        if (source >= 0) {
            result.set(source, drop);
            occupy(occupied, columns, drop);
        }
        for (int i = 0; i < spans.size(); i++) {
            if (i == source) continue;
            Cell old = anchors.get(i);
            if (old != null && free(occupied, columns, old)) {
                result.set(i, old);
                occupy(occupied, columns, old);
            }
        }
        for (int i = 0; i < spans.size(); i++) {
            if (result.get(i) != null) continue;
            Span span = spans.get(i);
            Cell old = anchors.get(i), chosen = null;
            // Vacated cells are the first destination for neighbors displaced by a drop.
            if (source >= 0) {
                Cell from = anchors.get(source);
                Cell swap = new Cell(from.column(), from.row(), span.columns(), span.rows());
                if (free(occupied, columns, swap)) chosen = swap;
            }
            int start = old == null ? 0 : old.row();
            for (int distance = 0; chosen == null; distance++) {
                for (int y : new int[] {start + distance, start - distance}) {
                    if (y < 0) continue;
                    int best = columns + 1;
                    for (int x = 0; x + span.columns() <= columns; x++) {
                        Cell c = new Cell(x, y, span.columns(), span.rows());
                        int delta = old == null ? x : Math.abs(x - old.column());
                        if (delta < best && free(occupied, columns, c)) {
                            chosen = c;
                            best = delta;
                        }
                    }
                    if (chosen != null || distance == 0) break;
                }
            }
            result.set(i, chosen);
            occupy(occupied, columns, chosen);
        }
        return result;
    }

    private static boolean free(BitSet occupied, int columns, Cell c) {
        if (c.column() < 0 || c.column() + c.width() > columns || c.row() < 0) return false;
        for (int row = c.row(); row < c.row() + c.height(); row++) {
            int start = row * columns + c.column(), hit = occupied.nextSetBit(start);
            if (hit >= start && hit < start + c.width()) return false;
        }
        return true;
    }

    private static void occupy(BitSet occupied, int columns, Cell c) {
        for (int row = c.row(); row < c.row() + c.height(); row++) {
            int start = row * columns + c.column();
            occupied.set(start, start + c.width());
        }
    }

    // SRLauncher's packMetroTiles scans every row from the top for each tile.
    static List<Cell> place(int columns, List<Span> spans, boolean dense) {
        ArrayList<Cell> cells = new ArrayList<>(spans.size());
        ArrayList<BitSet> rows = new ArrayList<>();
        int row = 0, column = 0;
        for (Span span : spans) {
            if (dense) {
                row = 0;
                column = 0;
            }
            while (true) {
                if (column + span.columns() > columns) {
                    column = 0;
                    row++;
                    continue;
                }
                while (rows.size() < row + span.rows()) rows.add(new BitSet(columns));
                boolean available = true;
                for (int y = row; y < row + span.rows(); y++) {
                    int occupied = rows.get(y).nextSetBit(column);
                    if (occupied >= 0 && occupied < column + span.columns()) {
                        available = false;
                        break;
                    }
                }
                if (available) break;
                column++;
            }
            cells.add(new Cell(column, row, span.columns(), span.rows()));
            for (int y = row; y < row + span.rows(); y++)
                rows.get(y).set(column, column + span.columns());
            column += span.columns();
        }
        return cells;
    }
}
