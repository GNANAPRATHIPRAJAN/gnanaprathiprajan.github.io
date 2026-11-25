"""Install the release on an attached Android test emulator and exercise launcher UI."""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.rajan.rsflow.studio"

def adb(*args):
    return subprocess.check_output(["adb", *map(str, args)], text=True, timeout=45)

def nodes():
    path = "/data/local/tmp/square-test.xml"
    for _ in range(5):
        adb("shell", "rm", "-f", path)
        report = adb("shell", "uiautomator", "dump", path)
        if "dumped to" in report.lower():
            xml = adb("shell", "cat", path)
            if xml.strip():
                return list(ET.fromstring(xml).iter("node"))
        time.sleep(.6)
    raise AssertionError("Android did not return an accessibility tree: " + report)

def find(label):
    for _ in range(4):
        current = nodes()
        for node in current:
            if any(label.casefold() == node.get(key, "").casefold() for key in ("text", "content-desc")):
                return node
        for node in current:
            if node.get("resource-id") == "android:id/ok" and node.get("package") == "com.android.systemui":
                adb("shell", "input", "tap", *center(node))
                break
        time.sleep(.3)
    print([(n.get("package"), n.get("text"), n.get("content-desc")) for n in current], flush=True)
    print(adb("shell", "logcat", "-b", "crash", "-d"), flush=True)
    raise AssertionError("Missing control: " + label)

def center(node):
    x, y, r, b = map(int, re.findall(r"\d+", node.get("bounds")))
    return [(x+r)//2, (y+b)//2]

def tap(label):
    adb("shell", "input", "tap", *center(find(label)))

def long_press(node):
    x, y = center(node)
    adb("shell", "input", "swipe", x, y, x, y, 750)

def main(apk):
    if adb("shell", "getprop", "ro.kernel.qemu").strip() != "1":
        raise RuntimeError("This test requires a disposable Android emulator")
    assert "Success" in adb("install", "-r", apk)
    adb("shell", "logcat", "-c")
    adb("shell", "settings", "put", "secure", "immersive_mode_confirmations", "confirmed")
    adb("shell", "wm", "dismiss-keyguard")
    adb("shell", "am", "start", "-W", "-n", PACKAGE+"/com.rajan.rsflow.MainActivity")
    find("All apps, shortcut cube")
    size = re.findall(r"(\d+)x(\d+)", adb("shell", "wm", "size"))[-1]
    w, h = map(int, size)
    def swipe_page(left):
        x1, x2 = (3*w//4, w//4) if left else (w//4, 3*w//4)
        adb("shell", "input", "swipe", x1, h//2, x2, h//2, 320)
    adb("shell", "input", "keyevent", "MENU")
    tap("Customize")
    tap("Microsoft Fluent icons")
    find("Microsoft Fluent")
    tap("Apply pack")
    def scroll_to(label):
        for _ in range(18):
            for n in nodes():
                if label in (n.get("text"), n.get("content-desc")):
                    return n
            adb("shell", "input", "swipe", w//2, 3*h//4, w//2, h//3, 370)
        raise AssertionError("Could not scroll to " + label)
    def tap_visible(label):
        adb("shell", "input", "tap", *center(scroll_to(label)))
    tap_visible("Home wallpaper")
    find("Import wallpaper image")
    tap_visible("Pixel · Coral dunes")
    find("All apps, shortcut cube")
    faces = set()
    for _ in range(10):
        faces.update(n.get("text") for n in nodes() if n.get("text") in ("Open gallery", "Photos"))
        if faces == {"Open gallery", "Photos"}:
            break
        time.sleep(.6)
    assert faces == {"Open gallery", "Photos"}, faces
    print("Passed Fluent icon pack selection, Pixel-inspired wallpaper selection and automatic Photo tile rotation")
    swipe_page(True)
    find("Search apps")
    swipe_page(False)
    find("All apps, shortcut cube")
    print("Passed drawer swipes in both directions")
    for _ in range(6):
        adb("shell", "input", "swipe", w//5, 3*h//4, w//5, h//3, 230)
    find("All apps, shortcut cube")
    assert all(n.get("text") != "Start" and n.get("content-desc") != "Add tile to Start" for n in nodes())
    for _ in range(6):
        if any(n.get("content-desc") == "Clock" for n in nodes()):
            break
        adb("shell", "input", "swipe", w//5, h//3, w//5, 3*h//4, 230)
    print("Passed repeated bottom-edge scrolling with a toolbar-free home")
    clock = find("Clock")
    print("Clock target", clock.attrib, flush=True)
    long_press(clock)
    find("Edit Clock")
    pencils = [n for n in nodes() if n.get("content-desc", "").startswith("Edit ")]
    assert len(pencils) == 1 and pencils[0].get("content-desc") == "Edit Clock", [p.attrib for p in pencils]
    adb("shell", "input", "keyevent", "BACK")
    swipe_page(True)
    find("Search apps")
    adb("shell", "input", "keyevent", "BACK")
    find("All apps, shortcut cube")
    clock = find("Clock")
    left = int(re.findall(r"\d+", clock.get("bounds"))[0])
    x, y = max(1, left//2), center(clock)[1]
    adb("shell", "input", "swipe", x, y, x, y, 1000)
    tap("Add tile")
    tap("Live tile")
    tap("Calendar date")
    find("Calendar date")
    adb("shell", "input", "keyevent", "MENU")
    tap("Action Center")
    find("Notifications")
    adb("shell", "cmd", "notification", "allow_listener", PACKAGE+"/com.rajan.rsflow.BadgeListener")
    adb("shell", "cmd", "notification", "post", "-t", "SquareSmoke", "square-test", "TestDelivery")
    find("SquareSmoke")
    # Android posts its own listener-access notification during this test.
    # Wait for a stable launcher row before basing the finger position on it.
    stable = None
    for _ in range(8):
        cards = [n for n in nodes() if n.get("package") == PACKAGE and n.get("text") == "SquareSmoke"]
        if cards:
            bounds = cards[0].get("bounds")
            if bounds == stable:
                break
            stable = bounds
        time.sleep(.4)
    else:
        raise AssertionError("No stable launcher notification row: " + str(stable))
    left, top, right, bottom = map(int, re.findall(r"\d+", stable))
    notice_y = (top + bottom)//2
    print("Swiping notification row", stable, flush=True)
    adb("shell", "input", "swipe", left + (right-left)//5, notice_y, right, notice_y, 340)
    for attempt in range(5):
        current = nodes()
        if all(n.get("text") != "SquareSmoke" or n.get("package") != PACKAGE for n in current):
            break
    else:
        raise AssertionError("Notification still on screen: " + str([(n.get("text"), n.get("bounds"), n.get("package")) for n in current if n.get("text")]))
    print("Passed notification delivery and swipe dismissal")
    adb("shell", "input", "keyevent", "BACK")
    adb("shell", "input", "keyevent", "MENU")
    tap("Customize")
    assert find("Windows 10").get("selected") == "true"
    tap("Windows 11")
    tap("Themes")
    tap("Lumia Cobalt")
    assert find("Windows 11").get("selected") == "true"
    adb("shell", "input", "keyevent", "BACK")
    find("All apps, shortcut cube")
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-W", "-n", PACKAGE+"/com.rajan.rsflow.MainActivity")
    adb("shell", "input", "keyevent", "MENU")
    tap("Customize")
    assert find("Windows 11").get("selected") == "true"
    tap("Windows 10")
    assert find("Windows 10").get("selected") == "true"
    print("Passed Windows 10/11 switching, palette independence and restart persistence")
    tap("Themes")
    tap("Lumia Panorama")
    tap_visible("Wallpaper placement")
    panorama = find("Tile panorama")
    assert panorama.get("checked") == "true", panorama.attrib
    adb("shell", "input", "keyevent", "BACK")
    tap_visible("App drawer layout")
    tap("Icon grid")
    tap_visible("Third page")
    tap("Dialer")
    find("Third page, Dialer")
    tap("Back to Start")
    find("All apps, shortcut cube")
    swipe_page(True)
    find("Search apps")
    assert any(n.get("class") == "android.widget.GridView" for n in nodes())
    swipe_page(True)
    find("Open dialer")
    tap("2")
    tap("7")
    find("27")
    tap("Delete last digit")
    swipe_page(False)
    find("Search apps")
    swipe_page(False)
    find("All apps, shortcut cube")
    print("Passed Lumia Panorama, tile placement, grid drawer, third-page dial pad and continuous navigation")
    # Exercise the platform picker with a deterministic local picture. A red
    # gutter distinguishes a backdrop from the black gaps of a tile panorama.
    import tempfile
    from pathlib import Path
    with tempfile.TemporaryDirectory() as temp:
        photo = Path(temp) / "SquareTest.png"
        subprocess.run(["convert", "-size", "600x1000", "xc:#ec321b", str(photo)], check=True)
        adb("push", str(photo), "/sdcard/Pictures/SquareTest.png")
        adb("shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
            "-d", "file:///sdcard/Pictures/SquareTest.png")
        adb("shell", "input", "keyevent", "MENU")
        tap("Customize")
        tap_visible("Home wallpaper")
        tap("Import wallpaper image")
        for _ in range(10):
            current = nodes()
            thumbs = [n for n in current if "thumbnail" in n.get("resource-id", "").lower()
                      or "photo taken" in n.get("content-desc", "").lower()]
            if thumbs:
                adb("shell", "input", "tap", *center(thumbs[0]))
                break
            time.sleep(.4)
        else:
            raise AssertionError("Missing test photo in Android picker: " + str([(n.get("resource-id"),n.get("content-desc")) for n in current]))
        find("All apps, shortcut cube")
        def screenshot_pixel(x, y):
            png = subprocess.check_output(["adb", "exec-out", "screencap", "-p"])
            rgb = subprocess.check_output(["convert", "png:-", "-crop", f"1x1+{x}+{y}", "+repage", "-depth", "8", "rgb:-"], input=png)
            return tuple(rgb[:3])
        clock = find("Clock")
        left, top, right, bottom = map(int, re.findall(r"\d+", clock.get("bounds")))
        y = (top+bottom)//2
        gutter = screenshot_pixel(max(1, left//2), y)
        tile = screenshot_pixel(right-(right-left)//6, y)
        assert max(gutter) < 15 and tile[0] > tile[1]*2, (gutter, tile)
        for placement in ("Glass tiles", "Backdrop"):
            adb("shell", "input", "keyevent", "MENU")
            tap("Customize")
            tap_visible("Wallpaper placement")
            tap(placement)
            find("Wallpaper placement, " + placement)
            tap("Back to Start")
            find("All apps, shortcut cube")
            gutter = screenshot_pixel(max(1, left//2), y)
            tile = screenshot_pixel(right-(right-left)//6, y)
            assert gutter[0] > gutter[1]*2, (placement, gutter)
            if placement == "Glass tiles": glass = tile
            else: assert glass != tile, (glass, tile)
    print("Passed photo import and pixel checks for Tile panorama, Glass tiles and Backdrop")
    fatal = adb("shell", "logcat", "-b", "crash", "-d")
    assert "Process: "+PACKAGE not in fatal, "Launcher crashed"
    print("Passed APK install, Start, one targeted pencil, drawer navigation, new live tile, and Action Center")
if __name__ == "__main__":
    main(sys.argv[1])
