# S-Square 2.7.0

An offline Android launcher by **gnanaprathiprajan** for Android 8 and later. Install the signed APK over S-Square 2.6.1, 2.6.0, 2.5.0, 2.4.0, 2.3.0, 2.2.0, 2.1.0 or 2.0.0 or RS Flow Studio 1.6.1 and later to keep the same layout, folders and widget bindings.

New installs open in the Windows 10 style with a black canvas, cobalt tiles, a tile-only home and Microsoft Fluent icons. For an existing layout choose **Customize → Windows 10 → Themes → Lumia Cobalt**. Windows 11 uses elevated tiles, centered captions and rounded alphabet headings. The separately signed 2.1.3 reference edition installs alongside this release.

[Download signed APK](release/S-Square-2.7.0.apk?raw=1) · [Download Android Studio source ZIP](release/S-Square-2.7.0-source.zip?raw=1)

## New in 2.7

Pinch inward with two fingers over the tiles and lift to open Start options. Back from a nested tile picker returns to the menu that opened it. The floating cube has a continuous shaded body under its rounded faces, including when you add a black border.

Open **Customize → Home wallpaper** to choose a local image, a bundled scene or a solid color. Applying a wallpaper keeps Customize open. In Music, open **Queue** to see the selected song and jump to another track. In Dialer, **Choose contact** fills the keypad from the system picker. Hold Backspace to clear the number or hold 0 to enter +.

## New in 2.6

Tap **+ → Rotating apps** at the end of Start. Use the plus on its face to add each app. With two or more apps the face turns every two seconds while visible and idle. Swipe across it to turn manually and tap to launch the visible app. The pencil lets you pause or resume rotation and remove a face. Other pinned apps keep their ordinary tap behavior.

Hold the separate floating cube to change corner radius, border width or color, move it or hide it. Restore it under **Customize → Floating shortcut cube**. Tap the gear in All apps to change layout, icons, shape or icon pack. The app drawer has a search icon, inline alphabet index, one-tap pinning, and three icon treatments. Original Prism adds a gradient behind the installed app artwork. Four new landscape images replace the Bokeh collection.

Choose Music for the third page to get the local player, embedded artwork when available, seek, previous and next, shuffle and repeat-one. Select a song from Music library to populate the queue. Playback pauses when the launcher leaves the foreground. Dialer uses large telephone keys with letter groups and opens the device dialer to confirm the call.

## Lumia personalization

Under **Customize → Wallpaper placement**, choose **Backdrop** for a full-screen picture, **Tile panorama** to reveal one continuous picture through tile faces, or **Glass tiles** to see the picture through adjustable color tints. Import a local photo under **Home wallpaper** or choose Amber Wilds, Polar Aurora or Vermilion Mesa. Panorama uses image sources and falls back to Forest Fold when a picture is unavailable. Individual custom colors stay available.

**Themes** includes Lumia Panorama, Amber Dusk and Lumia Vivid. **App drawer layout** switches between an alphabetical list, Lumia accent list and icon grid. **Third page** adds a music panel, a dial pad, or a shortcut to an installed app after All apps. The dial pad passes the number to Android for confirmation. Apps open in their own windows.

Set **Gallery tile layout** to **Live mosaic** to cycle authorized local photos. Hold an app tile, tap its pencil and choose **Icon size** to change that glyph independently of its footprint. Fluent Filled and Bootstrap Silhouettes each add 34 original vectors with fallbacks for other symbols. Both licenses are bundled.

## Start and All apps

Start holds your pinned tiles, folders, clock and Android widgets together. Swipe left to reveal All apps and right to return. You can reverse a short swipe before lifting your finger. Vertical scrolling stays independent. Vertical swipes at the bottom stay on Start. Hold an empty gap for home controls. The arrow in All apps returns to Start.

Hold a tile or widget until the pencil appears. Keep holding and drag to another tile to change their order. A footprint marks the destination and surrounding tiles move aside before you release. Only the selected tile displays a pencil. Drag near the top or bottom to scroll a longer board. Release without moving and tap the pencil to edit. While arranging, you can drag another tile straight away. Press Back to finish.

Use **Hold empty space → Add tile → Multiple apps** to pin several apps at once. Swipe a clearable Action Center notification left or right to dismiss it.

Tap a folder to see its scrollable grid of app icons and names. Use **Hold empty space** to pin an app, add a widget or create a folder.

Photo and Music tiles added in this version turn in place automatically. Set the interval (3–60 seconds) under Customize. Use **Photo and music tile flips** there to enable rotation on an existing layout, or use the pencil on a single tile to choose Off, Manual or Automatic. **Home wallpaper** includes three original Pixel-inspired geometric designs, a local image importer and a system wallpaper option. The Microsoft Fluent pack includes 40 original Microsoft vectors, with Metro and line-art fallbacks. The Microsoft MIT license is in the icon-pack assets.

## Themes, icons and the cube

Open **Hold empty space → Customize**. The Windows 10 and Windows 11 buttons select the shell. Choosing a color or theme keeps that shell. Your pins stay in place in the saved order. **Themes** offers the other presets. The same screen contains wallpaper, lock screen, typography, gesture, tile and app drawer controls. Tap **Astra icons** to browse all 119 designs or apply the pack. The Landscapes collection includes four offline scenic images.

Swipe the floating cube to rotate it. Tap its front face to open the shortcut. The default shortcuts are All apps, Action Center, Google, Music, Customize and Phone. Google opens the installed Google app. Long-press the cube to move it or change its shortcuts. Existing assigned apps are preserved.

## Music and live tiles

Open the Music tile or the cube’s Music shortcut. On first use, allow local audio access to display songs indexed by Android. Search by song or artist and select a track to open the player. Tap **Music library** in the player to switch tracks. Playback pauses when another app takes the foreground.

The Photos and Music tiles support **Off**, **Manual** and **Automatic** flip modes from their pencil menu. Open **Customize → Lock screen** to design the wallpaper, caption and clock preview. Android’s Wallpaper & style settings control the device’s lock-screen clock.

## Action Center and offline operation

Open Action Center from the Home menu, the cube, or a downward swipe on empty space at the top of Start. Grant notification access in Android to see active notifications. If Android reports a restriction, **Android blocked access? → Open app info** opens the system settings. Choose **Allow restricted settings** if your device offers it for this installation, then grant notification access. A local test notification is available to check delivery.

S-Square has no Android Internet permission. Settings use app-private storage and automatic backup is disabled. Local photos and notifications use Android permission controls. Apps, widgets and system settings opened by the launcher have their own permissions. A launcher cannot guarantee protection against every vulnerability or a compromised phone.

## Build

Extract the ZIP and open **RSFlow** in Android Studio. The build uses JDK 17, Android SDK 35, Build-Tools 35.0.0, Gradle 8.11.1, and Android Gradle Plugin 8.9.0. The first Gradle sync may need an internet connection to download build tools. Run the **app** configuration, or build with **gradlew.bat :app:assembleDebug** on Windows and **./gradlew :app:assembleDebug** on macOS or Linux. Debug builds use a separate application ID ending in .debug.

Backups are under **Customize → Data and launcher**. Older RS Flow and supported SRLauncher layouts can still be imported. Portable backups require rebinding widgets and selecting personal media again. The source archive excludes private signing material.
