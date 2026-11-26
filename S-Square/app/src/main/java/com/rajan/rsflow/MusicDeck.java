package com.rajan.rsflow;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.media.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;

import java.util.ArrayList;
import java.util.Locale;

/** One foreground transport shared between the music page and the tile player. */
final class MusicDeck {
    final MainActivity a;
    final MediaTiles library;
    final SharedPreferences prefs;
    final ArrayList<MediaTiles.Song> queue = new ArrayList<>();
    private final ArrayList<Panel> panels = new ArrayList<>();
    private MediaPlayer player;
    private AudioFocusRequest focus;
    private boolean ready, waiting, failed;
    private String source = "";
    private Bitmap art;
    private int generation;
    private final java.util.Random random = new java.util.Random();

    MusicDeck(MainActivity a, MediaTiles library) {
        this.a = a;
        this.library = library;
        prefs = a.getSharedPreferences("local_music", 0);
    }

    void select(MediaTiles.Song song, boolean autoplay) {
        prefs.edit()
                .putString("uri", song.uri.toString())
                .putString("name", song.title)
                .putString("artist", song.artist)
                .apply();
        prepare(autoplay);
    }

    private void release() {
        ready = waiting = false;
        if (player != null) {
            player.release();
            player = null;
        }
        if (focus != null) {
            a.getSystemService(AudioManager.class).abandonAudioFocusRequest(focus);
            focus = null;
        }
    }

    void close() {
        generation++;
        release();
    }

    void pause() {
        waiting = false;
        if (ready && player.isPlaying()) player.pause();
        update();
    }

    private void prepare(boolean play) {
        release();
        failed = false;
        art = null;
        String uri = prefs.getString("uri", "");
        source = uri;
        int request = ++generation;
        if (uri.isEmpty()) {
            update();
            return;
        }
        Uri location = Uri.parse(uri);
        if (!"content".equals(location.getScheme())) {
            failed = true;
            update();
            return;
        }
        waiting = play;
        library.readCover(
                location,
                bitmap -> {
                    if (request == generation) {
                        art = bitmap;
                        update();
                    }
                });
        try {
            MediaPlayer next = new MediaPlayer();
            player = next;
            next.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build());
            next.setDataSource(a, location);
            next.setOnPreparedListener(
                    mp -> {
                        if (mp != player) return;
                        ready = true;
                        if (waiting) start();
                        update();
                    });
            next.setOnCompletionListener(
                    mp -> {
                        if (mp != player) return;
                        if (prefs.getBoolean("repeat", false)) {
                            mp.seekTo(0);
                            start();
                        } else if (queue.size() > 1) skip(1);
                        else {
                            mp.seekTo(0);
                            update();
                        }
                    });
            next.setOnErrorListener(
                    (mp, w, e) -> {
                        if (mp == player) {
                            release();
                            failed = true;
                            update();
                        }
                        return true;
                    });
            next.prepareAsync();
        } catch (Exception e) {
            release();
            failed = true;
        }
        update();
    }

    private void start() {
        if (!ready) return;
        if (focus == null)
            focus =
                    new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(
                                    new AudioAttributes.Builder()
                                            .setUsage(AudioAttributes.USAGE_MEDIA)
                                            .build())
                            .setOnAudioFocusChangeListener(
                                    change -> {
                                        if (change < 0) pause();
                                    })
                            .build();
        if (a.getSystemService(AudioManager.class).requestAudioFocus(focus)
                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) player.start();
        waiting = false;
        update();
    }

    private void toggle() {
        if (prefs.getString("uri", "").isEmpty() || failed) {
            library.musicLibrary();
            return;
        }
        if (player == null || !source.equals(prefs.getString("uri", ""))) {
            prepare(true);
            return;
        }
        if (!ready) {
            waiting = !waiting;
            return;
        }
        if (player.isPlaying()) pause();
        else start();
    }

    private void skip(int step) {
        if (queue.isEmpty()) {
            library.musicLibrary();
            return;
        }
        int current = -1;
        for (int i = 0; i < queue.size(); i++)
            if (queue.get(i).uri.toString().equals(source)) {
                current = i;
                break;
            }
        if (step > 0 && prefs.getBoolean("shuffle", false) && queue.size() > 1)
            step = 1 + random.nextInt(queue.size() - 1);
        select(queue.get(Math.floorMod(current + step, queue.size())), true);
    }

    void update() {
        for (Panel panel : new ArrayList<>(panels)) panel.drawState();
    }

    View view() {
        return new Panel();
    }

    private static String time(int ms) {
        return String.format(Locale.ROOT, "%d:%02d", ms / 60000, ms / 1000 % 60);
    }

    final class Panel extends LinearLayout {
        final ImageView image;
        final TextView track, artist, clock, play, shuffle, repeat;
        final SeekBar seek;
        final Runnable timer =
                new Runnable() {
                    public void run() {
                        if (isShown()) progress();
                        if (isAttachedToWindow() && isShown()) postDelayed(this, 500);
                    }
                };

        Panel() {
            super(a);
            setOrientation(VERTICAL);
            FlowUi ui = a.ui;
            int pad = ui.dp(12);
            setPadding(pad, pad, pad, pad);
            FrameLayout cover = new FrameLayout(a);
            cover.setBackground(
                    ui.rounded(
                            FlowUi.blend(ui.panel, ui.accent, .28f),
                            a.getResources().getConfiguration().screenWidthDp < 330 ? 8 : 18,
                            0));
            cover.setClipToOutline(true);
            cover.addView(new FlowUi.Banner(a, ui.accent), new FrameLayout.LayoutParams(-1, -1));
            image = new ImageView(a);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            cover.addView(image, new FrameLayout.LayoutParams(-1, -1));
            addView(
                    cover,
                    new LayoutParams(
                            -1,
                            ui.dp(
                                    Math.max(
                                            90,
                                            Math.min(
                                                    228,
                                                    a.getResources()
                                                                    .getConfiguration()
                                                                    .screenHeightDp
                                                            / 4)))));
            ui.gap(this, 14);
            track = ui.label("", 25, ui.text);
            track.setMaxLines(2);
            FlowUi.weight(track, 650);
            addView(track);
            artist = ui.label("", 14, ui.dim);
            addView(artist);
            ui.gap(this, 8);
            seek = new SeekBar(a);
            seek.setContentDescription("Seek track");
            seek.setProgressTintList(ColorStateList.valueOf(ui.accent));
            seek.setThumbTintList(ColorStateList.valueOf(ui.accent));
            addView(seek);
            clock = ui.label("", 12, ui.dim);
            clock.setGravity(Gravity.END);
            addView(clock);
            LinearLayout keys = ui.row();
            keys.setGravity(Gravity.CENTER);
            addView(keys);
            button(keys, "◀◀", "Previous track", () -> skip(-1), false);
            play = button(keys, "▶", "Play", MusicDeck.this::toggle, true);
            button(keys, "▶▶", "Next track", () -> skip(1), false);
            ui.gap(this, 12);
            LinearLayout modes = ui.row();
            addView(modes);
            shuffle = button(modes, "Shuffle", "Shuffle", () -> mode("shuffle"), false);
            repeat = button(modes, "Repeat", "Repeat one", () -> mode("repeat"), false);
            ui.gap(this, 10);
            addView(ui.button("Music library", false, library::musicLibrary));
            seek.setOnSeekBarChangeListener(
                    new SeekBar.OnSeekBarChangeListener() {
                        public void onStartTrackingTouch(SeekBar b) {}

                        public void onStopTrackingTouch(SeekBar b) {}

                        public void onProgressChanged(SeekBar b, int p, boolean user) {
                            if (user && ready) player.seekTo(p);
                        }
                    });
        }

        private TextView button(
                LinearLayout row, String glyph, String label, Runnable action, boolean active) {
            TextView b = a.ui.button(glyph, active, action);
            b.setContentDescription(label);
            if (row.getChildCount() > 0) a.ui.gapHorizontal(row, 7);
            row.addView(b, new LayoutParams(0, a.ui.dp(50), 1));
            return b;
        }

        private void mode(String key) {
            prefs.edit().putBoolean(key, !prefs.getBoolean(key, false)).apply();
            update();
        }

        private void modeState(TextView button, String key) {
            boolean on = prefs.getBoolean(key, false);
            button.setSelected(on);
            button.setBackground(a.ui.surface(on ? a.ui.accent : a.ui.panel, 14, 0));
            button.setTextColor(on ? a.ui.ink : a.ui.text);
        }

        void drawState() {
            track.setText(prefs.getString("name", "Choose a track"));
            artist.setText(failed ? "Track unavailable" : prefs.getString("artist", ""));
            if (art != null) {
                image.setPadding(0, 0, 0, 0);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setImageBitmap(art);
            } else {
                int p = a.ui.dp(26);
                image.setPadding(p, p, p, p);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                image.setImageResource(R.drawable.bold_music);
            }
            boolean playing = ready && player.isPlaying();
            play.setText(playing ? "Ⅱ" : "▶");
            play.setContentDescription(playing ? "Pause" : "Play");
            modeState(shuffle, "shuffle");
            modeState(repeat, "repeat");
            seek.setEnabled(ready);
            progress();
        }

        void progress() {
            int total = ready ? Math.max(0, player.getDuration()) : 0,
                    pos = ready ? Math.max(0, player.getCurrentPosition()) : 0;
            seek.setMax(Math.max(1, total));
            if (!seek.isPressed()) seek.setProgress(pos);
            clock.setText(time(pos) + " / " + time(total));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            panels.add(this);
            drawState();
            removeCallbacks(timer);
            post(timer);
        }

        @Override
        protected void onDetachedFromWindow() {
            removeCallbacks(timer);
            panels.remove(this);
            super.onDetachedFromWindow();
        }

        @Override
        protected void onVisibilityChanged(View v, int visibility) {
            super.onVisibilityChanged(v, visibility);
            if (timer != null) {
                removeCallbacks(timer);
                if (isAttachedToWindow() && isShown()) post(timer);
            }
        }
    }
}
