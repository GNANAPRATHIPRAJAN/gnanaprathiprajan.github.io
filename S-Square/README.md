# S-Square 2.4.0

An offline Android launcher by **gnanaprathiprajan** for Android 8 and later. Install the signed APK over S-Square 2.3.0, 2.2.0, 2.1.0 or 2.0.0 or RS Flow Studio 1.6.1 and later to keep the same layout, folders and widget bindings.

[Download signed APK](release/S-Square-2.4.0.apk?raw=1) · [Download Android Studio source ZIP](release/S-Square-2.4.0-source.zip?raw=1)

New installs open in the Windows 10 style with a black canvas, cobalt tiles, a tile-only home and Microsoft Fluent icons. For an existing layout choose **Customize → Windows 10 → Themes → Lumia Cobalt**. Windows 11 uses elevated tiles, centered captions and rounded alphabet headings. The separately signed 2.1.3 reference edition installs alongside this release.

## Start and All apps

Start holds your pinned tiles, folders, clock and Android widgets together. Swipe left to reveal All apps and right to return. You can reverse a short swipe before lifting your finger. Vertical scrolling stays independent. Vertical swipes at the bottom stay on Start. Hold an empty gap for home controls. The arrow in All apps returns to Start.

Hold a tile or widget until the pencil appears. Keep holding and drag to another tile to change their order. A footprint marks the destination and surrounding tiles move aside before you release. Only the selected tile displays a pencil. Drag near the top or bottom to scroll a longer board. Release without moving and tap the pencil to edit. While arranging, you can drag another tile straight away. Press Back to finish.

Use **Hold empty space → Add tile → Multiple apps** to pin several apps at once. Swipe a clearable Action Center notification left or right to dismiss it.

Tap a folder to see its scrollable grid of app icons and names. Use **Hold empty space** to pin an app, add a widget or create a folder.

Photo and Music tiles added in this version turn in place automatically. Set the interval (3–60 seconds) under Customize. Use **Photo and music tile flips** there to enable rotation on an existing layout, or use the pencil on a single tile to choose Off, Manual or Automatic. **Home wallpaper** includes three original Pixel-inspired geometric designs, a local image importer and a shortcut to the device wallpaper collection. The Microsoft Fluent pack includes 40 original Microsoft vectors, with Metro and line-art fallbacks. The Microsoft MIT license is in the icon-pack assets.

## Themes, icons and the cube

Open **Hold empty space → Customize**. The Windows 10 and Windows 11 buttons select the shell. Choosing a color or theme keeps that shell. Your pins stay in place in the saved order. **Themes** offers the other presets. The same screen contains wallpaper, lock screen, typography, gesture, tile and app drawer controls. Tap **Astra icons** to browse all 119 designs or apply the pack. The ten Bokeh wallpapers ship with the app.

Swipe the floating cube to rotate it. Tap its front face to open the shortcut. The default shortcuts are All apps, Action Center, Google, Music, Customize and Phone. Google opens the installed Google app. Long-press the cube to move it or change its shortcuts. Existing assigned apps are preserved.

## Music and live tiles

Open the Music tile or the cube’s Music shortcut. On first use, allow local audio access to display songs indexed by Android. Search by song or artist and select a track to open the player. Tap **Music library** in the player to switch tracks. Playback ends when the player closes.

The Photos and Music tiles support **Off**, **Manual** and **Automatic** flip modes from their pencil menu. Open **Customize → Lock screen** to design the wallpaper, caption and clock preview. Android’s Wallpaper & style settings control the device’s lock-screen clock.

## Action Center and offline operation

Open Action Center from the Home menu, the cube, or a downward swipe on empty space at the top of Start. Grant notification access in Android to see active notifications. If Android reports a restriction, **Android blocked access? → Open app info** opens the system settings. Choose **Allow restricted settings** if your device offers it for this installation, then grant notification access. A local test notification is available to check delivery.

S-Square has no Android Internet permission. Settings use app-private storage and automatic backup is disabled. Local photos and notifications use Android permission controls. Apps, widgets and system settings opened by the launcher have their own permissions. A launcher cannot guarantee protection against every vulnerability or a compromised phone.

## Build

Extract the ZIP and open **RSFlow** in Android Studio. The build uses JDK 17, Android SDK 35, Build-Tools 35.0.0, Gradle 8.11.1, and Android Gradle Plugin 8.9.0. The first Gradle sync may need an internet connection to download build tools. Run the **app** configuration, or build with **gradlew.bat :app:assembleDebug** on Windows and **./gradlew :app:assembleDebug** on macOS or Linux. Debug builds use a separate application ID ending in .debug.

Backups are under **Customize → Data and launcher**. Older RS Flow and supported SRLauncher layouts can still be imported. Portable backups require rebinding widgets and selecting personal media again. The source archive excludes private signing material.
