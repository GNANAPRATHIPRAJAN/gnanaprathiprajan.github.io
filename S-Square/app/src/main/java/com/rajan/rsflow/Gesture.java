package com.rajan.rsflow;

final class Gesture {
    private Gesture() {}

    static String direction(float dx, float dy, float threshold) {
        float x = Math.abs(dx), y = Math.abs(dy);
        if (x >= threshold && x > 1.18f * y) return dx > 0 ? "right" : "left";
        if (y >= threshold && y > 1.18f * x) return dy > 0 ? "down" : "up";
        return "";
    }

    enum Route {
        NONE,
        DRAWER,
        TILES,
        HOME,
        CENTER,
        TILE
    }

    static Route homeRoute(
            String direction,
            Route nextPage,
            boolean hasTileBinding,
            boolean reachedTileThreshold,
            boolean drawerLeft,
            boolean drawerUp,
            boolean centerDown) {
        if (hasTileBinding) return reachedTileThreshold ? Route.TILE : Route.NONE;
        return switch (direction) {
            case "left" -> drawerLeft ? nextPage : Route.NONE;
            case "up" -> drawerUp ? Route.DRAWER : Route.NONE;
            case "down" -> centerDown ? Route.CENTER : Route.NONE;
            default -> Route.NONE;
        };
    }
}
