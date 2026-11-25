package com.rajan.rsflow;

import android.content.Context;
import android.graphics.drawable.Drawable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class BuiltInIcons {
    static final String LINE = "@flow-line";
    static final String COLOR = "@flow-solid";
    static final String INK = "@flow-ink";
    static final String STICKER = "@flow-sticker", GLASS = "@flow-glass";
    static final String ASTRA = "@flow-astra";
    static final String BOLD = "@square-fluent-filled", BOOTSTRAP = "@square-bootstrap";
    static final String FLUENT = "@square-fluent";
    static final String METRO = "@square-metro";
    static final String DESKTOP = "@flow-desktop";
    static final List<String> PACKS =
            Arrays.asList(LINE, COLOR, INK, STICKER, GLASS, DESKTOP, ASTRA, METRO, FLUENT, BOLD, BOOTSTRAP);

    static boolean colorful(String p) {
        return ASTRA.equals(p) || STICKER.equals(p) || GLASS.equals(p) || DESKTOP.equals(p);
    }

    private static final String CHOICE = "@icon/";
    private final Context context;
    private final Map<String, Symbol> symbols = new HashMap<>();
    private final Map<String, Symbol> packages = new HashMap<>();
    private final Map<String, Symbol> labels = new HashMap<>();
    private final List<Symbol> catalog = new ArrayList<>();

    static final class Symbol {
        final String id;
        final String name;
        final String aliases;

        Symbol(String id, String name, String aliases) {
            this.id = id;
            this.name = name;
            this.aliases = aliases;
        }

        boolean matches(String query) {
            return name.toLowerCase(Locale.ROOT).contains(query) || aliases.contains(query);
        }
    }

    BuiltInIcons(Context context) {
        this.context = context;
        try (BufferedReader index =
                new BufferedReader(
                        new InputStreamReader(
                                context.getAssets().open("icon-packs/index.tsv"),
                                StandardCharsets.UTF_8))) {
            for (String line; (line = index.readLine()) != null; ) {
                String[] fields = line.split("\t", -1);
                Symbol symbol = new Symbol(fields[0], fields[1], fields[3]);
                symbols.put(symbol.id, symbol);
                catalog.add(symbol);
                for (String key : fields[2].split(";")) {
                    if (!key.isEmpty()) packages.put(key.toLowerCase(Locale.ROOT), symbol);
                }
                labels.put(fields[1].toLowerCase(Locale.ROOT), symbol);
                for (String key : symbol.aliases.split(";")) {
                    if (!key.isEmpty()) labels.putIfAbsent(key, symbol);
                }
            }
        } catch (IOException e) {
            android.util.Log.w("RSFlow", "Could not load the built-in icons", e);
        }
        catalog.sort(Comparator.comparing(icon -> icon.name));
    }

    static String name(String pack) {
        return switch (pack) {
            case BOLD -> "Fluent Filled";
            case BOOTSTRAP -> "Bootstrap Silhouettes";
            case FLUENT -> "Microsoft Fluent";
            case METRO -> "Metro Glyphs";
            case ASTRA -> "Astra";
            case STICKER -> "Color Stickers";
            case GLASS -> "Prism Glass";
            case DESKTOP -> "Desktop Color";
            case LINE -> "Flow Line";
            case COLOR -> "Flow Color";
            case INK -> "Flow Ink";
            default -> "Original app icons";
        };
    }

    static String choice(String pack, Symbol symbol) {
        return CHOICE + PACKS.indexOf(pack) + "/" + symbol.id;
    }

    String packOf(String choice) {
        if (PACKS.contains(choice)) return choice;
        if (choice.startsWith(CHOICE)) {
            int end = choice.indexOf('/', CHOICE.length());
            if (end > CHOICE.length())
                try {
                    int index = Integer.parseInt(choice.substring(CHOICE.length(), end));
                    if (index >= 0 && index < PACKS.size()) return PACKS.get(index);
                } catch (NumberFormatException ignored) {}
        }
        return "";
    }

    Symbol forApp(String choice, String component, String label) {
        if (choice.startsWith(CHOICE) && choice.length() > 8)
            return symbols.get(choice.substring(choice.indexOf('/', CHOICE.length()) + 1));
        int slash = component.indexOf('/');
        if (slash >= 0) {
            Symbol match = packages.get(component.substring(0, slash).toLowerCase(Locale.ROOT));
            if (match != null) return match;
        }
        return labels.get(label.toLowerCase(Locale.ROOT).trim());
    }

    List<Symbol> catalog() {
        return catalog;
    }

    Drawable art(String pack, Symbol symbol) {
        if (BOLD.equals(pack) || BOOTSTRAP.equals(pack)) {
            String prefix = BOLD.equals(pack) ? "bold_" : "bootstrap_";
            int resource = context.getResources().getIdentifier(prefix + symbol.id, "drawable", context.getPackageName());
            if (resource != 0) return context.getDrawable(resource).mutate();
            pack = FLUENT;
        }
        if (FLUENT.equals(pack)) {
            int id = context.getResources().getIdentifier("fluent_" + symbol.id,
                    "drawable", context.getPackageName());
            if (id != 0) return context.getDrawable(id).mutate();
            pack = METRO;
        }
        if (METRO.equals(pack)) {
            String name = switch (symbol.id) {
                case "gamepad" -> "games";
                case "fitness" -> "health";
                case "security" -> "lock";
                default -> symbol.id;
            };
            int id = context.getResources().getIdentifier("ic_metro_" + name,
                    "drawable", context.getPackageName());
            if (id != 0) return context.getDrawable(id).mutate();
            pack = LINE;
        }
        String style =
                switch (pack) {
                    case ASTRA -> "astra";
                    case STICKER -> "sticker";
                    case GLASS -> "glass";
                    case LINE -> "line";
                    case DESKTOP -> "desktop";
                    case COLOR -> "color";
                    case INK -> "ink";
                    default -> throw new IllegalArgumentException("Unknown built-in pack");
                };
        int drawable =
                context.getResources()
                        .getIdentifier(
                                "flow_" + style + "_" + symbol.id,
                                "drawable",
                                context.getPackageName());
        if (drawable == 0 && DESKTOP.equals(pack)) {
            int line =
                    context.getResources()
                            .getIdentifier(
                                    "flow_line_" + symbol.id, "drawable", context.getPackageName());
            if (line == 0) return null;
            Drawable art = context.getDrawable(line).mutate();
            int[] colors = {0xff6faae9, 0xff6dd7ae, 0xffc9aafa, 0xfff8bf67, 0xffed97b0};
            art.setTint(colors[Math.floorMod(symbol.id.hashCode(), colors.length)]);
            return art;
        }
        return drawable == 0 ? null : context.getDrawable(drawable).mutate();
    }
}
