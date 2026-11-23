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
        for node in nodes():
            if label in (node.get("text"), node.get("content-desc")):
                return node
        time.sleep(.3)
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
    adb("shell", "am", "start", "-W", "-n", PACKAGE+"/com.rajan.rsflow.MainActivity")
    find("Start")
    clock = find("Clock")
    long_press(clock)
    pencils = [n for n in nodes() if n.get("content-desc", "").startswith("Edit ")]
    assert len(pencils) == 1 and pencils[0].get("content-desc") == "Edit Clock", [p.attrib for p in pencils]
    tap("Finish arranging")
    tap("All apps")
    find("Search apps")
    adb("shell", "input", "keyevent", "BACK")
    find("Start")
    tap("Add tile to Start")
    tap("Live tile")
    tap("Calendar date")
    find("Calendar date")
    tap("Start options and customization")
    tap("Action Center")
    find("Notifications")
    fatal = adb("shell", "logcat", "-b", "crash", "-d")
    assert "Process: "+PACKAGE not in fatal, "Launcher crashed"
    print("Passed APK install, Start, one targeted pencil, drawer navigation, new live tile, and Action Center")
if __name__ == "__main__":
    main(sys.argv[1])
