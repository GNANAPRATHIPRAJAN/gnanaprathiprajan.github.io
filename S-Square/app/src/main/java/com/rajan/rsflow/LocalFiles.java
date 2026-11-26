package com.rajan.rsflow;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.util.AtomicFile;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class LocalFiles {
    private static final int BACKUP_LIMIT = 3 * 1024 * 1024;
    private static final int PHOTO_LIMIT = 1680;
    private final AtomicFile background, tileImage, lockImage;
    private final MainActivity activity;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Bitmap photo, tilePhoto, lockPhoto;
    private String pendingIcon = "";
    private final android.util.LruCache<String, Bitmap> icons = new android.util.LruCache<>(24);

    LocalFiles(MainActivity activity, android.os.Bundle state) {
        this.activity = activity;
        if (state != null) pendingIcon = state.getString("pendingIcon", "");
        background = new AtomicFile(new File(activity.getFilesDir(), "home-background.jpg"));
        lockImage = new AtomicFile(new File(activity.getFilesDir(), "lock-photo.jpg"));
        tileImage = new AtomicFile(new File(activity.getFilesDir(), "tile-photo.jpg"));
    }

    private boolean applyingLock;

    void applyLock(LockDesign design, int width, int height) {
        if (applyingLock) {
            activity.toast("Applying lock wallpaper…");
            return;
        }
        applyingLock = true;
        io.execute(
                () -> {
                    Bitmap output = null;
                    String result;
                    try {
                        android.app.WallpaperManager wm =
                                android.app.WallpaperManager.getInstance(activity);
                        if (!wm.isWallpaperSupported() || !wm.isSetWallpaperAllowed()) {
                            result = "Wallpaper changes are disabled on this device";
                        } else {
                            output = design.render(width, height);
                            int id =
                                    wm.setBitmap(
                                            output,
                                            null,
                                            false,
                                            android.app.WallpaperManager.FLAG_LOCK);
                            result =
                                    id > 0
                                            ? "Lock-screen wallpaper applied"
                                            : "Android could not apply the lock wallpaper";
                        }
                    } catch (Exception failure) {
                        result = "Android could not apply the lock wallpaper";
                    } finally {
                        if (output != null) output.recycle();
                    }
                    String message = result;
                    finish(
                            () -> {
                                applyingLock = false;
                                activity.toast(message);
                            });
                });
    }

    void close() {
        io.shutdown();
    }

    void savePending(android.os.Bundle state) {
        state.putString("pendingIcon", pendingIcon);
    }

    Bitmap tileIcon(String id) {
        if (!FlowStore.validImageId(id)) return null;
        Bitmap value = icons.get(id);
        if (value == null) {
            value = load(iconFile(id));
            if (value != null) icons.put(id, value);
        }
        return value;
    }

    void chooseTileIcon(FlowStore.Tile tile) {
        if (tile.imageId.isEmpty()) {
            tile.imageId = java.util.UUID.randomUUID().toString().replace("-", "");
            activity.store.save();
        }
        pendingIcon = tile.imageId;
        chooseImage(MainActivity.PICK_ICON);
    }

    void discardTileIcon(FlowStore.Tile tile) {
        String id = tile.imageId;
        if (!FlowStore.validImageId(id)) return;
        tile.imageId = "";
        activity.media.untrackPortrait(id);
        icons.remove(id);
        io.execute(() -> iconFile(id).delete());
    }

    private AtomicFile iconFile(String id) {
        return new AtomicFile(
                new File(new File(activity.getFilesDir(), "tile-icons"), id + ".png"));
    }

    private void importTileIcon(Uri uri, String id) {
        AtomicFile destination = iconFile(id);
        FileOutputStream target = null;
        try {
            Bitmap image = decodePhoto(uri, 256);
            if (image == null) throw new IllegalArgumentException("Invalid icon image");
            target = destination.startWrite();
            if (!image.compress(Bitmap.CompressFormat.PNG, 100, target))
                throw new IllegalStateException("Could not save icon");
            destination.finishWrite(target);
            finish(
                    () -> {
                        activity.media.untrackPortrait(id);
                        icons.put(id, image);
                        activity.refresh();
                        activity.toast("Tile icon updated");
                    });
        } catch (Exception ignored) {
            if (target != null) destination.failWrite(target);
            finish(() -> activity.toast("Could not open this icon image"));
        }
    }

    Bitmap wallpaper() {
        if (photo == null) photo = load(background);
        return photo;
    }

    Bitmap tilePhoto() {
        if (tilePhoto == null) tilePhoto = load(tileImage);
        return tilePhoto;
    }

    private Bitmap load(AtomicFile file) {
        if (!file.getBaseFile().isFile()) return null;
        try (InputStream stream = file.openRead()) {
            return BitmapFactory.decodeStream(stream);
        } catch (Exception ignored) {
            return null;
        }
    }

    Bitmap lockPhoto() {
        if (lockPhoto == null) lockPhoto = load(lockImage);
        return lockPhoto;
    }

    void chooseLockPhoto() {
        chooseImage(MainActivity.PICK_LOCK_PHOTO);
    }

    void choosePhoto() {
        chooseImage(MainActivity.PICK_PHOTO);
    }

    void chooseTilePhoto() {
        chooseImage(MainActivity.PICK_TILE_PHOTO);
    }

    private void chooseImage(int code) {
        if (android.os.Build.VERSION.SDK_INT >= 33
                || (android.os.Build.VERSION.SDK_INT >= 30
                        && android.os.ext.SdkExtensions.getExtensionVersion(
                                        android.os.Build.VERSION_CODES.R)
                                >= 2)) {
            try {
                activity.startActivityForResult(
                        new Intent(android.provider.MediaStore.ACTION_PICK_IMAGES)
                                .setType("image/*"),
                        code);
                return;
            } catch (android.content.ActivityNotFoundException ignored) {
            }
        }
        select(
                new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .setType("image/*")
                        .putExtra(Intent.EXTRA_LOCAL_ONLY, true),
                code);
    }

    void exportLayout() {
        select(
                new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .setType("application/json")
                        .putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                        .putExtra(Intent.EXTRA_TITLE, "S-Square-layout.json"),
                MainActivity.EXPORT);
    }

    void importLayout() {
        activity.dialog()
                .setTitle("Restore your layout?")
                .setMessage(
                        "Import replaces the current layout. You can use an RS Flow or SRLauncher"
                                + " JSON backup. Android widgets can be rebound after importing.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Choose backup",
                        (dialog, index) ->
                                select(
                                        new Intent(Intent.ACTION_OPEN_DOCUMENT)
                                                .setType("application/json")
                                                .putExtra(Intent.EXTRA_LOCAL_ONLY, true),
                                        MainActivity.IMPORT))
                .show();
    }

    void importClipboard() {
        activity.dialog()
                .setTitle("Restore copied layout?")
                .setMessage(
                        "Copy the backup JSON in SRLauncher or RS Flow first. Restoring replaces"
                                + " the current layout.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Restore from clipboard", (dialog, index) -> readClipboard())
                .show();
    }

    private void readClipboard() {
        android.content.ClipboardManager clipboard =
                activity.getSystemService(android.content.ClipboardManager.class);
        android.content.ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
        CharSequence text =
                clip == null || clip.getItemCount() == 0 ? null : clip.getItemAt(0).getText();
        if (text == null || text.length() == 0) {
            activity.toast("Copy the backup JSON first");
        } else if (text.length() > BACKUP_LIMIT) {
            activity.toast("Backup is too large");
        } else {
            String json = text.toString();
            io.execute(
                    () -> {
                        try {
                            parseBackup(json);
                        } catch (Exception ignored) {
                            finish(
                                    () ->
                                            activity.toast(
                                                    "Clipboard does not contain a supported"
                                                            + " backup"));
                        }
                    });
        }
    }

    private void select(Intent intent, int request) {
        try {
            activity.startActivityForResult(intent.addCategory(Intent.CATEGORY_OPENABLE), request);
        } catch (RuntimeException ignored) {
            activity.toast("Your device has no compatible file picker");
        }
    }

    void onResult(int code, int result, Intent data) {
        String iconTarget = code == MainActivity.PICK_ICON ? pendingIcon : "";
        if (code == MainActivity.PICK_ICON) pendingIcon = "";
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (!"content".equals(uri.getScheme())) {
            activity.toast("Choose a file from the Android file picker");
            return;
        }
        if (code == MainActivity.PICK_ICON && FlowStore.validImageId(iconTarget)) {
            io.execute(() -> importTileIcon(uri, iconTarget));
        } else if (code == MainActivity.PICK_PHOTO
                || code == MainActivity.PICK_TILE_PHOTO
                || code == MainActivity.PICK_LOCK_PHOTO) {
            io.execute(() -> importPhoto(uri, code));
        } else if (code == MainActivity.IMPORT) io.execute(() -> importJson(uri));
        else if (code == MainActivity.EXPORT) {
            byte[] bytes = activity.store.json(true).toString().getBytes(StandardCharsets.UTF_8);
            io.execute(() -> exportJson(uri, bytes));
        }
    }

    private void exportJson(Uri uri, byte[] bytes) {
        try (OutputStream stream = activity.getContentResolver().openOutputStream(uri, "wt")) {
            if (stream == null) throw new IllegalStateException("No backup stream");
            stream.write(bytes);
        } catch (Exception ignored) {
            finish(() -> activity.toast("Could not save the backup"));
            return;
        }
        finish(() -> activity.toast("S-Square backup saved"));
    }

    private void importJson(Uri uri) {
        try (InputStream stream = activity.getContentResolver().openInputStream(uri)) {
            if (stream == null) throw new IllegalStateException("No backup stream");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int count; (count = stream.read(buffer)) != -1; ) {
                if (bytes.size() + count > BACKUP_LIMIT)
                    throw new IllegalArgumentException("Backup too large");
                bytes.write(buffer, 0, count);
            }
            parseBackup(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            finish(() -> activity.toast("Could not open this layout backup"));
        }
    }

    private void parseBackup(String json) throws Exception {
        checkDepth(json);
        JSONObject root = new JSONObject(json);
        boolean fromSr = SrBackup.supports(root);
        JSONObject layout = fromSr ? SrBackup.convert(root) : root;
        finish(() -> restore(layout, fromSr));
    }

    private static void checkDepth(String text) {
        int depth = 0;
        boolean quoted = false, escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == 92) escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') {
                quoted = true;
            } else if (c == '{' || c == '[') {
                if (++depth > 16)
                    throw new IllegalArgumentException("Backup nesting exceeds limit");
            } else if (c == '}' || c == ']') {
                if (--depth < 0) throw new IllegalArgumentException("Invalid backup nesting");
            }
        }
        if (quoted || depth != 0) throw new IllegalArgumentException("Incomplete backup");
    }

    private void restore(JSONObject source, boolean fromSr) {
        Set<Integer> previous = activity.store.widgetIds();
        try {
            activity.store.read(source);
            activity.store.clearForeignAssets();
            activity.store.initialized = true;
            if (Wallpapers.choice(activity.store) == Wallpapers.PHOTO && wallpaper() == null) {
                activity.store.set("background", Wallpapers.PATTERN);
            }
            activity.store.save();
            BadgeListener.enable(activity.store.flag("badges", false));
            for (int id : previous) activity.widgets.deleteId(id);
            if (activity.screen == MainActivity.Screen.HOME) activity.refresh();
            activity.goHome();
            activity.toast(fromSr ? "SRLauncher layout imported" : "Layout restored");
        } catch (Exception ignored) {
            activity.toast("Unsupported layout backup");
        }
    }

    private void importPhoto(Uri uri, int targetKind) {
        boolean forTile = targetKind == MainActivity.PICK_TILE_PHOTO;
        boolean forLock = targetKind == MainActivity.PICK_LOCK_PHOTO;
        AtomicFile destination = forTile ? tileImage : forLock ? lockImage : background;
        FileOutputStream target = null;
        try {
            Bitmap image = decodePhoto(uri, PHOTO_LIMIT);
            if (image == null) throw new IllegalArgumentException("Image failed to decode");
            target = destination.startWrite();
            if (!image.compress(Bitmap.CompressFormat.JPEG, 88, target))
                throw new IllegalStateException("Image failed to save");
            destination.finishWrite(target);
            finish(
                    () -> {
                        if (forLock) {
                            lockPhoto = image;
                            activity.store.set("lockImage", Wallpapers.PHOTO);
                            activity.refresh();
                        } else if (forTile) {
                            tilePhoto = image;
                            activity.refresh();
                            activity.toast("Photo tile updated");
                        } else {
                            photo = image;
                            activity.store.set("background", Wallpapers.PHOTO);
                            activity.refresh();
                            activity.toast("Home background updated");
                        }
                    });
        } catch (Exception ignored) {
            if (target != null) destination.failWrite(target);
            finish(() -> activity.toast("Could not open this image"));
        }
    }

    void forgetIcon(String id) {
        icons.remove(id);
    }

    Bitmap decodePhoto(Uri uri, int limit) throws Exception {
        if (uri == null || !"content".equals(uri.getScheme()) || limit <= 0)
            throw new IllegalArgumentException("Invalid image source");
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.Source source =
                    ImageDecoder.createSource(activity.getContentResolver(), uri);
            return ImageDecoder.decodeBitmap(
                    source,
                    (decoder, info, ignored) -> {
                        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                        int width = info.getSize().getWidth(), height = info.getSize().getHeight();
                        int largest = Math.max(width, height);
                        if (largest > limit) {
                            double scale = (double) limit / largest;
                            decoder.setTargetSize(
                                    Math.max(1, (int) (width * scale)),
                                    Math.max(1, (int) (height * scale)));
                        }
                    });
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream stream = activity.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(stream, null, options);
        }
        if (options.outWidth < 1 || options.outHeight < 1)
            throw new IllegalArgumentException("Invalid image");
        int largest = Math.max(options.outWidth, options.outHeight);
        options.inJustDecodeBounds = false;
        options.inSampleSize = 1;
        while (largest / options.inSampleSize > limit) options.inSampleSize *= 2;
        try (InputStream stream = activity.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(stream, null, options);
        }
    }

    private void finish(Runnable action) {
        activity.runOnUiThread(
                () -> {
                    if (!activity.isDestroyed() && !activity.isFinishing()) action.run();
                });
    }
}
