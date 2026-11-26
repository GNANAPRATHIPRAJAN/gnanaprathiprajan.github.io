package com.rajan.rsflow;

import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class ThirdPage {
    static final int PICK_NUMBER = 317;
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

    static void useNumber(MainActivity a, Uri uri) {
        if (uri == null || !"content".equals(uri.getScheme())) return;
        try (android.database.Cursor contact = a.getContentResolver().query(uri,
                new String[] {android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null)) {
            if (contact != null && contact.moveToFirst() && !contact.isNull(0)) {
                String number = android.telephony.PhoneNumberUtils.stripSeparators(contact.getString(0));
                if (number != null && number.length() <= 50 && !number.isEmpty()) {
                    a.dialDigits = number;
                    a.refresh();
                    return;
                }
            }
        } catch (RuntimeException ignored) { }
        a.toast("No phone number available");
    }

    private void dialer(LinearLayout body) {
        TextView number = ui.label(a.dialDigits, 32, ui.text);
        MainActivity.watch(number, text -> a.dialDigits = text);
        number.setHint("Phone number");
        number.setHintTextColor(ui.dim);
        number.setSingleLine(true);
        number.setAutoSizeTextTypeUniformWithConfiguration(14, 32, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        number.setGravity(Gravity.CENTER);
        body.addView(number);
        TextView contacts = ui.button("Choose contact", false, () -> {
            try {
                a.startActivityForResult(new Intent(Intent.ACTION_PICK,
                    android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI), PICK_NUMBER);
            } catch (android.content.ActivityNotFoundException unavailable) {
                a.toast("No contact picker installed");
            }
        });
        ui.gap(body, 8);
        body.addView(contacts);
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
                            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
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
        erase.setOnLongClickListener(v -> { number.setText(""); return true; });
        controls.addView(erase);
        body.addView(controls);
    }
}
