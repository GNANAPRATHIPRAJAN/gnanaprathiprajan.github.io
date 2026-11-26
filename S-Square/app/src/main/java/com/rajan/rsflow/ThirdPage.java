package com.rajan.rsflow;

import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class ThirdPage {
    private final MainActivity a;
    private final FlowUi ui;

    ThirdPage(MainActivity activity) {
        a = activity;
        ui = a.ui;
    }

    void choose() {
        Sheet sheet = new Sheet(a, "Third page");
        sheet.add("−", "Off", () -> save(""));
        sheet.add("♫", "Music", () -> save("music"));
        sheet.add("☎", "Dialer", () -> save("dialer"));
        sheet.add(
                "▦", "Choose app", () -> a.chooseApp("Third page app", app -> save(app.key), null));
        sheet.show();
    }

    private void save(String choice) {
        a.store.set("thirdPage", choice);
        a.refresh();
    }

    View build() {
        String choice = a.store.option("thirdPage", "");
        LinearLayout page = ui.column();
        page.setBackgroundColor(ui.base);
        page.setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(16));
        LinearLayout head = ui.row();
        String name =
                choice.equals("dialer")
                        ? "Dialer"
                        : choice.equals("music") ? "Music" : a.appName(choice);
        head.addView(ui.label(name, 40, ui.text), new LinearLayout.LayoutParams(0, -2, 1));
        TextView back = ui.command("←", () -> a.setScreen(MainActivity.Screen.APPS));
        back.setContentDescription("Return to All apps");
        head.addView(back);
        page.addView(head);
        ScrollView scroll = new ScrollView(a);
        LinearLayout body = ui.column();
        scroll.addView(body);
        page.addView(scroll);
        ui.gap(body, 18);
        if (choice.equals("dialer")) dialer(body);
        else if (choice.equals("music")) {
            body.addView(a.media.deck.view());
        } else if (a.apps.find(choice) != null) {
            body.addView(a.appIcon(choice, 68, ui.base));
            ui.gap(body, 18);
            body.addView(ui.button("Open " + name, true, () -> a.launch(choice)));
        } else body.addView(ui.button("Choose another app", true, this::choose));
        return page;
    }

    private void dialer(LinearLayout body) {
        TextView number = ui.label("", 32, ui.text);
        number.setHint("Phone number");
        number.setHintTextColor(ui.dim);
        number.setSingleLine(true);
        number.setGravity(Gravity.CENTER);
        body.addView(number);
        ui.gap(body, 14);
        for (String digits : new String[] {"123", "456", "789", "*0#"}) {
            LinearLayout row = ui.row();
            for (char digit : digits.toCharArray()) {
                if (row.getChildCount() > 0) ui.gapHorizontal(row, 8);
                LinearLayout key = ui.column();
                key.setGravity(Gravity.CENTER);
                key.setFocusable(true);
                key.setClickable(true);
                key.setContentDescription(String.valueOf(digit));
                key.setBackground(ui.surface(FlowUi.blend(ui.panel, ui.accent, .12f), 28, 0));
                key.setOnClickListener(
                        v -> {
                            if (number.length() < 50) number.append(String.valueOf(digit));
                        });
                String[] letters = {
                    "+", "", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ"
                };
                TextView numeral = ui.label(String.valueOf(digit), 27, ui.text);
                numeral.setGravity(Gravity.CENTER);
                numeral.setIncludeFontPadding(false);
                key.addView(numeral);
                if (Character.isDigit(digit)) {
                    TextView abc = ui.label(letters[digit - '0'], 10, ui.dim);
                    abc.setLetterSpacing(.14f);
                    abc.setIncludeFontPadding(false);
                    key.addView(abc);
                }
                if (digit == '0')
                    key.setOnLongClickListener(
                            v -> {
                                if (number.length() == 0) number.setText("+");
                                return true;
                            });
                row.addView(key, new LinearLayout.LayoutParams(0, ui.dp(62), 1));
            }
            body.addView(row);
            ui.gap(body, 8);
        }
        LinearLayout controls = ui.row();
        controls.addView(
                ui.button(
                        "☎   Open dialer",
                        true,
                        () ->
                                a.startSafely(
                                        new Intent(
                                                Intent.ACTION_DIAL,
                                                Uri.fromParts(
                                                        "tel",
                                                        number.getText().toString(),
                                                        null)))),
                new LinearLayout.LayoutParams(0, ui.dp(52), 1));
        controls.getChildAt(0).setContentDescription("Open dialer");
        ui.gapHorizontal(controls, 8);
        TextView erase =
                ui.command(
                        "⌫",
                        () -> {
                            if (number.length() > 0)
                                number.setText(
                                        number.getText()
                                                .subSequence(0, number.length() - 1)
                                                .toString());
                        });
        erase.setContentDescription("Delete last digit");
        controls.addView(erase);
        body.addView(controls);
    }
}
