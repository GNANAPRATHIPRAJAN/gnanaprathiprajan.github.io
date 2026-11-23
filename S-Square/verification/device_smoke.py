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
    adb("shell", "uiautomator", "dump", "/sdcard/square-test.xml")
    return list(ET.fromstring(adb("shell", "cat", "/sdcard/square-test.xml")).iter("node"))

def find(label):
    for _ in range(4):
        current = nodes()
        for node in current:
            if label in (node.get("text"), node.get("content-desc")):
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
    notice = find("SquareSmoke")
    _, notice_y = center(notice)
    adb("shell", "input", "swipe", w//4, notice_y, 7*w//8, notice_y, 300)
    for attempt in range(4):
        if all(n.get("text") != "SquareSmoke" for n in nodes()):
            break
    else:
        raise AssertionError("Clearable Android notification persisted after swipe")
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
    fatal = adb("shell", "logcat", "-b", "crash", "-d")
    assert "Process: "+PACKAGE not in fatal, "Launcher crashed"
    print("Passed APK install, Start, one targeted pencil, drawer navigation, new live tile, and Action Center")
if __name__ == "__main__":
    main(sys.argv[1])
