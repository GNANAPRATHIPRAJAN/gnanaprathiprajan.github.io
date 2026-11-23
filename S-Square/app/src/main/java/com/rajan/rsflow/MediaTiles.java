package com.rajan.rsflow;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.*;
import android.media.*;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.util.AtomicFile;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

final class MediaTiles {
    static final int GALLERY = 201, CONTACT = 202, MUSIC = 203;
    private final MainActivity a;
    private final AtomicFile album;
    private Bitmap strip;
    private MediaPlayer player;
    private String target = "";
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final android.os.Handler main =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Map<String, Bitmap> portraits = new HashMap<>();
    private boolean watching, active;
    private boolean running, updatePending;
    private AlertDialog galleryDialog;
    private Runnable updateGallery;
    private int photoIndex;
    private String recentFingerprint;
    private Bitmap recent;
    private final ContentObserver watch =
            new ContentObserver(main) {
                @Override
                public void onChange(boolean selfChange) {
                    schedule();
                }
            };
    private final Runnable reload = () -> updateLibrary();
    private static final int PHOTOS_PERMISSION = 210,
            CONTACT_PERMISSION = 211,
            AUDIO_PERMISSION = 212;
    private AudioFocusRequest audioFocus;

    MediaTiles(MainActivity a, android.os.Bundle state) {
        this.a = a;
        album = new AtomicFile(new File(a.getFilesDir(), "album.webp"));
        if (state != null) target = state.getString("contactTarget", "");
    }

    void save(android.os.Bundle b) {
        b.putString("contactTarget", target);
    }

    void close() {
        stop();
        pauseUpdates();
        io.shutdown();
    }

    void stop() {
        if (audioFocus != null) {
            a.getSystemService(AudioManager.class).abandonAudioFocusRequest(audioFocus);
            audioFocus = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
    }

    private boolean granted(String permission) {
        return a.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean recentAllowed() {
        if (Build.VERSION.SDK_INT >= 33)
            return granted(android.Manifest.permission.READ_MEDIA_IMAGES)
                    || (Build.VERSION.SDK_INT >= 34
                            && granted(
                                    android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED));
        return granted(android.Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    void untrackPortrait(String id) {
        people().edit().remove(id).apply();
        portraits.remove(id);
        schedule();
    }

    private SharedPreferences people() {
        return a.getSharedPreferences("contact_portraits", Context.MODE_PRIVATE);
    }

    Bitmap portrait(String id) {
        return granted(android.Manifest.permission.READ_CONTACTS) ? portraits.get(id) : null;
    }

    void startUpdates() {
        active = true;
        if (watching) a.getContentResolver().unregisterContentObserver(watch);
        watching = false;
        if (recentAllowed() && a.store.flag("livePhotos", false)) {
            observe(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        } else {
            recent = null;
            recentFingerprint = null;
        }
        if (granted(android.Manifest.permission.READ_CONTACTS)) {
            observe(ContactsContract.Contacts.CONTENT_URI);
        } else portraits.clear();
        schedule();
    }

    private void observe(Uri uri) {
        try {
            a.getContentResolver().registerContentObserver(uri, true, watch);
            watching = true;
        } catch (RuntimeException ignored) {
        }
    }

    void pauseUpdates() {
        active = false;
        main.removeCallbacks(reload);
        if (watching) {
            a.getContentResolver().unregisterContentObserver(watch);
            watching = false;
        }
    }

    private void schedule() {
        if (active) {
            main.removeCallbacks(reload);
            main.postDelayed(reload, 230);
        }
    }

    private void updateLibrary() {
        if (!active) return;
        if (running) {
            updatePending = true;
            return;
        }
        boolean photos = a.store.flag("livePhotos", false) && recentAllowed();
        List<String> visibleIds = new ArrayList<>();
        if (granted(android.Manifest.permission.READ_CONTACTS))
            for (FlowStore.Tile tile : a.page().tiles) {
                if (!tile.imageId.isEmpty() && people().contains(tile.imageId))
                    visibleIds.add(tile.imageId);
            }
        if (!photos && visibleIds.isEmpty()) return;
        running = true;
        String previousFingerprint = recentFingerprint;
        io.execute(
                () -> {
                    Bitmap found = null;
                    String fingerprint = previousFingerprint;
                    if (photos) {
                        String next = "";
                        List<Uri> images = new ArrayList<>();
                        try (Cursor c =
                                a.getContentResolver()
                                        .query(
                                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                                new String[] {"_id", "date_modified"},
                                                null,
                                                null,
                                                "date_added DESC, _id DESC")) {
                            StringBuilder keys = new StringBuilder();
                            while (c != null && c.moveToNext() && images.size() < 8) {
                                images.add(
                                        ContentUris.withAppendedId(
                                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                                c.getLong(0)));
                                keys.append(c.getLong(0))
                                        .append(':')
                                        .append(c.getLong(1))
                                        .append(',');
                            }
                            next = keys.toString();
                        } catch (RuntimeException ignored) {
                        }
                        if (!next.equals(previousFingerprint)) {
                            fingerprint = next;
                            found = photoStrip(images);
                        }
                    }
                    Map<String, Bitmap> photosByContact = new HashMap<>();
                    for (String id : visibleIds) {
                        Uri contact = Uri.parse(people().getString(id, ""));
                        if (!ContactsContract.AUTHORITY.equals(contact.getAuthority())) continue;
                        try (InputStream in =
                                ContactsContract.Contacts.openContactPhotoInputStream(
                                        a.getContentResolver(), contact, false)) {
                            Bitmap bitmap = in == null ? null : BitmapFactory.decodeStream(in);
                            if (bitmap != null) photosByContact.put(id, bitmap);
                        } catch (RuntimeException | IOException ignored) {
                        }
                    }
                    Bitmap stripResult = found;
                    String observed = fingerprint;
                    done(
                            () -> {
                                running = false;
                                if (updatePending) {
                                    updatePending = false;
                                    schedule();
                                }
                                if (!active) return;
                                boolean changed = false;
                                if (photos
                                        && recentAllowed()
                                        && a.store.flag("livePhotos", false)
                                        && !Objects.equals(recentFingerprint, observed)) {
                                    recent = stripResult;
                                    recentFingerprint = observed;
                                    changed = true;
                                }
                                if (granted(android.Manifest.permission.READ_CONTACTS)) {
                                    changed |= !portraits.keySet().equals(photosByContact.keySet());
                                    for (Map.Entry<String, Bitmap> item :
                                            photosByContact.entrySet()) {
                                        Bitmap current = portraits.get(item.getKey());
                                        if (!people().contains(item.getKey())) continue;
                                        if (current == null || !item.getValue().sameAs(current)) {
                                            changed = true;
                                            break;
                                        }
                                    }
                                    if (changed) {
                                        portraits.clear();
                                        for (Map.Entry<String, Bitmap> item :
                                                photosByContact.entrySet())
                                            if (people().contains(item.getKey()))
                                                portraits.put(item.getKey(), item.getValue());
                                    }
                                }
                                if (changed) {
                                    if (updateGallery != null) updateGallery.run();
                                    if (a.canRefreshLiveBoard()) a.refresh();
                                }
                            });
                });
    }

    void permissionsResult(int code) {
        if (code == AUDIO_PERMISSION) {
            if (granted(audioPermission())) musicLibrary();
            else a.toast("Music access is needed to show songs on this device");
            return;
        }
        if (code == PHOTOS_PERMISSION) {
            if (recentAllowed()) {
                a.store.set("livePhotos", true);
                startUpdates();
            }
            if (updateGallery != null) updateGallery.run();
        }
        if (code == CONTACT_PERMISSION) {
            if (granted(android.Manifest.permission.READ_CONTACTS)) startUpdates();
            else a.toast("The selected portrait stays as a local image");
        }
    }

    private void recentPhotos() {
        if (recentAllowed()) {
            a.store.set("livePhotos", !a.store.flag("livePhotos", false));
            startUpdates();
            if (updateGallery != null) updateGallery.run();
            a.refresh();
            return;
        }
        String[] permissions =
                Build.VERSION.SDK_INT >= 34
                        ? new String[] {
                            android.Manifest.permission.READ_MEDIA_IMAGES,
                            android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                        }
                        : new String[] {
                            Build.VERSION.SDK_INT >= 33
                                    ? android.Manifest.permission.READ_MEDIA_IMAGES
                                    : android.Manifest.permission.READ_EXTERNAL_STORAGE
                        };
        a.requestPermissions(permissions, PHOTOS_PERMISSION);
    }

    void pick(int code, FlowStore.Tile tile) {
        Intent i;
        if (code == CONTACT) {
            if (tile.imageId.isEmpty()) {
                tile.imageId = UUID.randomUUID().toString().replace("-", "");
                a.store.save();
            }
            target = tile.imageId;
            i =
                    new Intent(
                            Intent.ACTION_PICK,
                            android.provider.ContactsContract.Contacts.CONTENT_URI);
        } else if (code == GALLERY) {
            if (Build.VERSION.SDK_INT >= 33
                    || (Build.VERSION.SDK_INT >= 30
                            && android.os.ext.SdkExtensions.getExtensionVersion(
                                            Build.VERSION_CODES.R)
                                    >= 2)) {
                Intent photos =
                        new Intent(MediaStore.ACTION_PICK_IMAGES)
                                .setType("image/*")
                                .putExtra(
                                        MediaStore.EXTRA_PICK_IMAGES_MAX,
                                        Math.min(8, MediaStore.getPickImagesMaxLimit()));
                try {
                    a.startActivityForResult(photos, code);
                    return;
                } catch (ActivityNotFoundException ignored) {
                }
            }
            Intent photos =
                    new Intent(Intent.ACTION_PICK)
                            .setType("image/*")
                            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            try {
                a.startActivityForResult(photos, code);
                return;
            } catch (ActivityNotFoundException ignored) {
            }
            i =
                    new Intent(Intent.ACTION_OPEN_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("image/*")
                            .putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        } else {
            musicLibrary();
            return;
        }
        try {
            a.startActivityForResult(i, code);
        } catch (ActivityNotFoundException e) {
            a.toast("Picker unavailable");
        }
    }

    void result(int code, Intent data) {
        ArrayList<Uri> list = new ArrayList<>();
        if (data.getData() != null) list.add(data.getData());
        ClipData clip = data.getClipData();
        if (clip != null)
            for (int i = 0; i < clip.getItemCount() && list.size() < 8; i++) {
                Uri u = clip.getItemAt(i).getUri();
                if (!list.contains(u)) list.add(u);
            }
        list.removeIf(u -> u == null || !"content".equals(u.getScheme()));
        if (list.isEmpty()) return;
        Uri first = list.get(0);
        if (code == MUSIC) {
            try {
                a.getContentResolver()
                        .takePersistableUriPermission(first, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                String old = musicPrefs().getString("uri", "");
                String name = "Local track";
                try (android.database.Cursor c =
                        a.getContentResolver()
                                .query(
                                        first,
                                        new String[] {
                                            android.provider.OpenableColumns.DISPLAY_NAME
                                        },
                                        null,
                                        null,
                                        null)) {
                    if (c != null && c.moveToFirst()) name = c.getString(0);
                }
                musicPrefs()
                        .edit()
                        .putString("uri", first.toString())
                        .putString("name", name)
                        .apply();
                if (!old.isEmpty() && !old.equals(first.toString()))
                    try {
                        a.getContentResolver()
                                .releasePersistableUriPermission(
                                        Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (RuntimeException ignored) {
                    }
                music();
            } catch (Exception e) {
                a.toast("Choose a local audio file");
            }
            return;
        }
        final String id = target;
        if (code == CONTACT && !ContactsContract.AUTHORITY.equals(first.getAuthority())) return;
        io.execute(
                () -> {
                    try {
                        if (code == CONTACT) {
                            if (!FlowStore.validImageId(id)) return;
                            try (InputStream in =
                                    android.provider.ContactsContract.Contacts
                                            .openContactPhotoInputStream(
                                                    a.getContentResolver(), first, false)) {
                                if (in == null) throw new IOException();
                                Bitmap b = BitmapFactory.decodeStream(in);
                                if (b == null) throw new IOException();
                                AtomicFile f =
                                        new AtomicFile(
                                                new File(
                                                        new File(a.getFilesDir(), "tile-icons"),
                                                        id + ".png"));
                                write(f, b, Bitmap.CompressFormat.PNG);
                                b.recycle();
                            }
                            Uri lookup = first;
                            try {
                                Uri stable =
                                        ContactsContract.Contacts.getLookupUri(
                                                a.getContentResolver(), first);
                                if (stable != null) lookup = stable;
                            } catch (RuntimeException ignored) {
                            }
                            final Uri selected = lookup;
                            done(
                                    () -> {
                                        people().edit().putString(id, selected.toString()).apply();
                                        a.files.forgetIcon(id);
                                        a.refresh();
                                        if (granted(android.Manifest.permission.READ_CONTACTS)) {
                                            startUpdates();
                                            return;
                                        }
                                        a.dialog()
                                                .setTitle("Keep the portrait up to date?")
                                                .setMessage(
                                                        "Allow Contacts access to refresh this"
                                                            + " selected portrait when it changes.")
                                                .setNegativeButton("Keep this photo", null)
                                                .setPositiveButton(
                                                        "Allow updates",
                                                        (dialog, button) ->
                                                                a.requestPermissions(
                                                                        new String[] {
                                                                            android.Manifest
                                                                                    .permission
                                                                                    .READ_CONTACTS
                                                                        },
                                                                        CONTACT_PERMISSION))
                                                .show();
                                    });
                        } else {
                            Bitmap b = photoStrip(list);
                            if (b == null) throw new IOException();
                            write(album, b, Bitmap.CompressFormat.WEBP);
                            done(
                                    () -> {
                                        strip = b;
                                        photoIndex = 0;
                                        a.store.set("livePhotos", false);
                                        startUpdates();
                                        if (updateGallery != null) updateGallery.run();
                                        a.refresh();
                                    });
                        }
                    } catch (Exception e) {
                        done(
                                () ->
                                        a.toast(
                                                code == CONTACT
                                                        ? "Contact photo unavailable. Choose an"
                                                                + " image instead."
                                                        : "Could not import photos"));
                    }
                });
    }

    private void done(Runnable r) {
        a.runOnUiThread(
                () -> {
                    if (!a.isDestroyed() && !a.isFinishing()) r.run();
                });
    }

    private static void write(AtomicFile file, Bitmap b, Bitmap.CompressFormat format)
            throws IOException {
        FileOutputStream out = file.startWrite();
        try {
            if (!b.compress(format, 92, out)) throw new IOException();
            file.finishWrite(out);
        } catch (Exception e) {
            file.failWrite(out);
            throw e;
        }
    }

    private Bitmap photoStrip(List<Uri> images) {
        List<Bitmap> usable = new ArrayList<>();
        for (Uri uri : images)
            try {
                Bitmap photo = a.files.decodePhoto(uri, 768);
                if (photo != null) usable.add(photo);
            } catch (Exception ignored) {
            }
        if (usable.isEmpty()) return null;
        int side = 384;
        Bitmap atlas = Bitmap.createBitmap(side * usable.size(), side, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(atlas);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        for (int i = 0; i < usable.size(); i++) {
            Bitmap photo = usable.get(i);
            int dim = Math.min(photo.getWidth(), photo.getHeight()),
                    x = (photo.getWidth() - dim) / 2,
                    y = (photo.getHeight() - dim) / 2;
            canvas.drawBitmap(
                    photo,
                    new Rect(x, y, x + dim, y + dim),
                    new Rect(i * side, 0, (i + 1) * side, side),
                    paint);
            photo.recycle();
        }
        return atlas;
    }

    private Bitmap galleryStrip() {
        if (a.store.flag("livePhotos", false) && recentAllowed()) return recent;
        if (strip == null && album.getBaseFile().isFile())
            try (InputStream in = album.openRead()) {
                strip = BitmapFactory.decodeStream(in);
            } catch (IOException ignored) {
            }
        return strip;
    }

    void openGallery() {
        if (galleryDialog != null && galleryDialog.isShowing()) return;
        FlowUi ui = a.ui;
        LinearLayout panel = ui.column();
        panel.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(12));
        android.widget.ScrollView content = new android.widget.ScrollView(a);
        content.setClipToPadding(false);
        content.addView(panel);
        ImageView image = new ImageView(a);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackground(ui.rounded(ui.base, 16, ui.outline));
        image.setClipToOutline(true);
        int height =
                Math.min(
                        330,
                        Math.max(
                                122,
                                a.getResources().getConfiguration().screenHeightDp * 40 / 100));
        panel.addView(image, new LinearLayout.LayoutParams(-1, ui.dp(height)));
        LinearLayout nav = ui.row();
        TextView position = ui.label("", 13, ui.dim);
        position.setGravity(Gravity.CENTER);
        TextView prev =
                ui.command(
                        "‹",
                        () -> {
                            photoIndex--;
                            updateGallery.run();
                        });
        TextView next =
                ui.command(
                        "›",
                        () -> {
                            photoIndex++;
                            updateGallery.run();
                        });
        prev.setContentDescription("Previous photo");
        next.setContentDescription("Next photo");
        nav.addView(prev);
        nav.addView(position, new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(next);
        panel.addView(nav);
        ui.gap(panel, 8);
        TextView recentButton = ui.button("Use recent photos", false, this::recentPhotos);
        panel.addView(recentButton);
        ui.gap(panel, 7);
        panel.addView(ui.button("Choose photos", true, () -> pick(GALLERY, null)));
        ui.gap(panel, 7);
        panel.addView(
                ui.button(
                        "Open device gallery",
                        false,
                        () ->
                                a.startSafely(
                                        Intent.makeMainSelectorActivity(
                                                Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY))));
        updateGallery =
                () -> {
                    Bitmap data = galleryStrip();
                    int count = data == null ? 0 : data.getWidth() / data.getHeight();
                    position.setText(
                            count == 0
                                    ? (a.store.flag("livePhotos", false) && recentAllowed()
                                            ? "No recent photos available"
                                            : "Choose photos to start")
                                    : (Math.floorMod(photoIndex, count) + 1) + " / " + count);
                    prev.setEnabled(count > 1);
                    next.setEnabled(count > 1);
                    boolean on = a.store.flag("livePhotos", false) && recentAllowed();
                    recentButton.setText(
                            on ? "Stop updating from recent photos" : "Use recent photos");
                    if (count == 0) image.setImageResource(R.drawable.flow_desktop_gallery);
                    else {
                        int side = data.getHeight();
                        image.setImageBitmap(
                                Bitmap.createBitmap(
                                        data,
                                        Math.floorMod(photoIndex, count) * side,
                                        0,
                                        side,
                                        side));
                    }
                };
        galleryDialog =
                a.dialog()
                        .setTitle("Gallery")
                        .setView(content)
                        .setNegativeButton("Done", null)
                        .create();
        galleryDialog.setOnDismissListener(
                dialog -> {
                    galleryDialog = null;
                    updateGallery = null;
                });
        updateGallery.run();
        galleryDialog.show();
    }

    View gallery() {
        final Bitmap atlas = galleryStrip();
        if (atlas == null) return null;
        return new View(a) {

            final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            final Rect src = new Rect(), dst = new Rect();

            protected void onDraw(Canvas c) {
                int side = atlas.getHeight(),
                        count = atlas.getWidth() / side,
                        w = getWidth(),
                        h = getHeight();
                if (w == 0 || h == 0 || count == 0) return;
                long ms = android.os.SystemClock.uptimeMillis();
                int idx = (int) (ms / 7000 % count);
                float scale = Math.max(w / (float) side, h / (float) side);
                int sw = Math.max(1, Math.round(w / scale)),
                        sh = Math.max(1, Math.round(h / scale)),
                        x = (side - sw) / 2,
                        y = (side - sh) / 2;
                dst.set(0, 0, w, h);
                src.set(idx * side + x, y, idx * side + x + sw, y + sh);
                paint.setAlpha(255);
                c.drawBitmap(atlas, src, dst, paint);
                boolean blend =
                        a.store.number("motion", 1) != 0
                                && android.animation.ValueAnimator.areAnimatorsEnabled();
                long t = ms % 7000;
                if (count > 1 && isShown()) {
                    if (blend && t >= 6500) {
                        src.offset((((idx + 1) % count) - idx) * side, 0);
                        paint.setAlpha((int) ((t - 6500) * 255 / 500));
                        c.drawBitmap(atlas, src, dst, paint);
                        postInvalidateOnAnimation();
                    } else postInvalidateDelayed((blend ? 6500 : 7000) - t);
                }
            }
        };
    }

    private static String duration(int millis) {
        int seconds = Math.max(0, millis / 1000);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private SharedPreferences musicPrefs() {
        return a.getSharedPreferences("local_music", Context.MODE_PRIVATE);
    }

    String musicName() {
        return musicPrefs().getString("name", "Choose track");
    }

    private String audioPermission() {
        return Build.VERSION.SDK_INT >= 33
                ? android.Manifest.permission.READ_MEDIA_AUDIO
                : android.Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private static final class Song {
        final Uri uri;
        final String title, artist;

        Song(Uri uri, String title, String artist) {
            this.uri = uri;
            this.title = title;
            this.artist = artist;
        }

        @Override
        public String toString() {
            return artist.isEmpty() ? title : title + "   ·   " + artist;
        }
    }

    private void selectSong(Song song) {
        String previous = musicPrefs().getString("uri", "");
        musicPrefs()
                .edit()
                .putString("uri", song.uri.toString())
                .putString("name", song.title)
                .apply();
        if (!previous.isEmpty() && !previous.equals(song.uri.toString())) {
            for (android.content.UriPermission grant :
                    a.getContentResolver().getPersistedUriPermissions()) {
                if (grant.getUri().toString().equals(previous)) {
                    try {
                        a.getContentResolver()
                                .releasePersistableUriPermission(
                                        grant.getUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (SecurityException ignored) {
                    }
                    break;
                }
            }
        }
        music();
    }

    private void musicLibrary() {
        if (!granted(audioPermission())) {
            a.requestPermissions(new String[] {audioPermission()}, AUDIO_PERMISSION);
            return;
        }
        LinearLayout panel = a.ui.column();
        int pad = a.ui.dp(16);
        panel.setPadding(pad, 0, pad, pad);
        EditText search = a.input("Search songs or artists", "", 100, false);
        panel.addView(search);
        TextView status = a.ui.label("Loading songs…", 14, a.ui.dim);
        status.setGravity(Gravity.CENTER);
        FrameLayout area = new FrameLayout(a);
        ListView songs = new ListView(a);
        songs.setDivider(null);
        area.addView(songs, new FrameLayout.LayoutParams(-1, -1));
        area.addView(status, new FrameLayout.LayoutParams(-1, -1));
        panel.addView(
                area,
                new LinearLayout.LayoutParams(
                        -1,
                        a.ui.dp(
                                Math.min(
                                        390,
                                        a.getResources().getConfiguration().screenHeightDp / 2))));
        panel.setFocusableInTouchMode(true);
        panel.requestFocus();
        AlertDialog dialog =
                a.dialog()
                        .setTitle("Music library")
                        .setView(panel)
                        .setNegativeButton("Close", null)
                        .create();
        dialog.getWindow()
                .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        dialog.show();
        io.execute(
                () -> {
                    ArrayList<Song> found = new ArrayList<>();
                    Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                    String[] fields = {
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST
                    };
                    boolean failed = false;
                    try (Cursor c =
                            a.getContentResolver()
                                    .query(
                                            collection,
                                            fields,
                                            MediaStore.Audio.Media.IS_MUSIC + " != 0",
                                            null,
                                            MediaStore.Audio.Media.TITLE + " ASC")) {
                        if (c == null) failed = true;
                        else
                            while (c.moveToNext()) {
                                String title = c.getString(1), artist = c.getString(2);
                                found.add(
                                        new Song(
                                                ContentUris.withAppendedId(
                                                        collection, c.getLong(0)),
                                                title == null || title.isEmpty()
                                                        ? "Untitled"
                                                        : title,
                                                artist == null || "<unknown>".equals(artist)
                                                        ? ""
                                                        : artist));
                            }
                    } catch (RuntimeException unavailable) {
                        failed = true;
                    }
                    final boolean queryFailed = failed;
                    main.post(
                            () -> {
                                if (a.isDestroyed() || a.isFinishing() || !dialog.isShowing())
                                    return;
                                ArrayList<Song> visible = new ArrayList<>(found);
                                BaseAdapter adapter =
                                        new BaseAdapter() {
                                            public int getCount() {
                                                return visible.size();
                                            }

                                            public Song getItem(int pos) {
                                                return visible.get(pos);
                                            }

                                            public long getItemId(int pos) {
                                                return pos;
                                            }

                                            public View getView(
                                                    int pos, View convert, ViewGroup parent) {
                                                TextView name =
                                                        convert instanceof TextView
                                                                ? (TextView) convert
                                                                : a.ui.label("", 16, a.ui.text);
                                                Song song = getItem(pos);
                                                name.setText(
                                                        song.artist.isEmpty()
                                                                ? song.title
                                                                : song.title + "\n" + song.artist);
                                                name.setGravity(Gravity.CENTER_VERTICAL);
                                                name.setPadding(pad / 2, pad / 2, pad / 2, pad / 2);
                                                name.setMinHeight(a.ui.dp(52));
                                                return name;
                                            }
                                        };
                                songs.setAdapter(adapter);
                                songs.setEmptyView(status);
                                java.util.function.Consumer<String> filter =
                                        value -> {
                                            String term = value.trim().toLowerCase(Locale.ROOT);
                                            visible.clear();
                                            for (Song song : found)
                                                if (term.isEmpty()
                                                        || song.toString()
                                                                .toLowerCase(Locale.ROOT)
                                                                .contains(term)) visible.add(song);
                                            status.setText(
                                                    queryFailed
                                                            ? "Music library unavailable"
                                                            : found.isEmpty()
                                                                    ? "No local songs found. Add"
                                                                          + " music to your"
                                                                          + " device’s Music"
                                                                          + " folder."
                                                                    : "No matching songs");
                                            adapter.notifyDataSetChanged();
                                        };
                                MainActivity.watch(search, filter);
                                filter.accept(search.getText().toString());
                                songs.setOnItemClickListener(
                                        (parent, view, pos, id) -> {
                                            Song song = visible.get(pos);
                                            dialog.dismiss();
                                            selectSong(song);
                                        });
                            });
                });
    }

    void music() {
        stop();
        String uri = musicPrefs().getString("uri", "");
        if (uri.isEmpty()
                || ("media".equals(Uri.parse(uri).getAuthority()) && !granted(audioPermission()))) {
            musicLibrary();
            return;
        }

        LinearLayout panel = a.ui.column();
        int pad = a.ui.dp(16);
        panel.setPadding(pad, pad, pad, pad);
        int artHeight =
                Math.min(150, Math.max(64, a.getResources().getConfiguration().screenHeightDp / 5));
        FrameLayout cover = new FrameLayout(a);
        cover.setBackground(a.ui.rounded(0xff292c51, 20, a.ui.outline));
        cover.setClipToOutline(true);
        cover.addView(new FlowUi.Banner(a, 0xff9b99ff), new FrameLayout.LayoutParams(-1, -1));
        ImageView musicArt = new ImageView(a);
        musicArt.setImageResource(R.drawable.flow_desktop_music);
        cover.addView(
                musicArt, new FrameLayout.LayoutParams(a.ui.dp(62), a.ui.dp(62), Gravity.CENTER));
        panel.addView(cover, new LinearLayout.LayoutParams(-1, a.ui.dp(artHeight)));
        a.ui.gap(panel, 12);
        TextView track = a.ui.label(musicName(), 19, a.ui.text);
        track.setGravity(Gravity.CENTER);
        track.setMaxLines(2);
        FlowUi.weight(track, 650);
        panel.addView(track);
        a.ui.gap(panel, 8);
        TextView play = a.ui.button("Loading…", true, () -> {});
        play.setEnabled(false);

        SeekBar seek = new SeekBar(a);
        seek.setContentDescription("Seek track");
        panel.addView(seek);
        TextView time = a.ui.label("0:00 / 0:00", 12, a.ui.dim);
        time.setGravity(Gravity.CENTER);
        panel.addView(time);
        a.ui.gap(panel, 8);
        panel.addView(play);
        AlertDialog d =
                a.dialog()
                        .setTitle("Flow Music")
                        .setView(panel)
                        .setNegativeButton("Close", null)
                        .setNeutralButton("Music library", (v, w) -> musicLibrary())
                        .create();
        d.setOnDismissListener(v -> stop());
        d.show();
        try {
            MediaPlayer p = new MediaPlayer();
            player = p;
            p.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build());
            p.setDataSource(a, Uri.parse(uri));
            p.setOnPreparedListener(
                    mp -> {
                        if (player != mp) return;
                        seek.setMax(Math.max(1, mp.getDuration()));
                        play.setEnabled(true);
                        play.setText("▶  Play");
                        play.setOnClickListener(
                                v -> {
                                    if (player != mp) return;
                                    if (mp.isPlaying()) {
                                        mp.pause();
                                        play.setText("▶  Play");
                                    } else {
                                        if (audioFocus == null)
                                            audioFocus =
                                                    new AudioFocusRequest.Builder(
                                                                    AudioManager.AUDIOFOCUS_GAIN)
                                                            .setAudioAttributes(
                                                                    new AudioAttributes.Builder()
                                                                            .setUsage(
                                                                                    AudioAttributes
                                                                                            .USAGE_MEDIA)
                                                                            .build())
                                                            .setOnAudioFocusChangeListener(
                                                                    change -> {
                                                                        if (change < 0
                                                                                && player == mp
                                                                                && mp.isPlaying()) {
                                                                            mp.pause();
                                                                            play.setText("▶  Play");
                                                                        }
                                                                    })
                                                            .build();
                                        if (a.getSystemService(AudioManager.class)
                                                        .requestAudioFocus(audioFocus)
                                                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                                            mp.start();
                                            play.setText("Ⅱ  Pause");
                                        }
                                    }
                                });
                        seek.setOnSeekBarChangeListener(
                                new SeekBar.OnSeekBarChangeListener() {
                                    public void onProgressChanged(
                                            SeekBar b, int val, boolean user) {
                                        if (user && player == mp) mp.seekTo(val);
                                    }

                                    public void onStartTrackingTouch(SeekBar b) {}

                                    public void onStopTrackingTouch(SeekBar b) {}
                                });
                        seek.post(
                                new Runnable() {
                                    public void run() {
                                        if (player != mp || !d.isShowing()) return;
                                        seek.setProgress(mp.getCurrentPosition());
                                        time.setText(
                                                duration(mp.getCurrentPosition())
                                                        + " / "
                                                        + duration(mp.getDuration()));
                                        seek.postDelayed(this, 500);
                                    }
                                });
                    });
            p.setOnCompletionListener(mp -> play.setText("▶  Replay"));
            p.setOnErrorListener(
                    (mp, w, e) -> {
                        stop();
                        play.setEnabled(false);
                        play.setText("Track unavailable");
                        return true;
                    });
            p.prepareAsync();
        } catch (Exception e) {
            stop();
            play.setText("Choose another track");
        }
    }
}
