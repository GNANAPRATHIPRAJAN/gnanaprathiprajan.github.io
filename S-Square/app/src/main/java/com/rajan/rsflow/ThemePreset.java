package com.rajan.rsflow;

enum ThemePreset {
    ASTRA("Astra", 0, 0xffa5bdff, 8, 0, 19, 4, BuiltInIcons.ASTRA, BuiltInIcons.ASTRA),
    PISTACHIO("Pistachio", 3, 0xffbad98b, 0, 0, 14, 4, BuiltInIcons.DESKTOP, BuiltInIcons.DESKTOP),
    COBALT("Cobalt Metro", 0, 0xff58b9fc, 1, 1, 3, 3, BuiltInIcons.LINE, BuiltInIcons.LINE),
    COPPER("Copper Dusk", 2, 0xfff9b879, 2, 2, 16, 3, BuiltInIcons.STICKER, BuiltInIcons.DESKTOP),
    ORCHID("Orchid Glass", 0, 0xffcfadff, 3, 0, 23, 4, BuiltInIcons.GLASS, BuiltInIcons.INK),
    LAGOON("Lagoon", 3, 0xff99e5de, 4, 1, 11, 4, BuiltInIcons.COLOR, BuiltInIcons.LINE);

    final String label;
    final int palette, accent, paper, font, radius, columns;
    final String tilePack, drawerPack;

    ThemePreset(
            String label,
            int palette,
            int accent,
            int paper,
            int font,
            int radius,
            int columns,
            String tilePack,
            String drawerPack) {
        this.label = label;
        this.palette = palette;
        this.accent = accent;
        this.paper = Wallpapers.FIRST_BOKEH + paper;
        this.font = font;
        this.radius = radius;
        this.columns = columns;
        this.tilePack = tilePack;
        this.drawerPack = drawerPack;
    }

    void apply(FlowStore store) {

        FlowStore.put(store.options, "tileOutline", false);
        FlowStore.put(store.options, "theme", palette);
        FlowStore.put(store.options, "accent", accent);
        FlowStore.put(store.options, "background", paper);
        FlowStore.put(store.options, "font", font);

        FlowStore.put(store.options, "defaultTileColor", 0);
        FlowStore.put(store.options, "appColors", false);
        FlowStore.put(store.options, "icons", 0);
        FlowStore.put(store.options, "iconPack", tilePack);
        FlowStore.put(store.options, "drawerPack", drawerPack);
        store.save();
    }
}
