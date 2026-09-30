"""Unit tests for the `am start` recipe parser, run with:  python -m unittest scripts/test_build_emulator_catalog.py

Every command below is copied verbatim from the Daijishō catalogue (platforms/*.json).
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_emulator_catalog import data_type, extensions_from_regex, parse_am_start

RETROARCH_ATARI5200 = (
    "-n com.retroarch.aarch64/com.retroarch.browser.retroactivity.RetroActivityFuture\n"
    " -e ROM {file.path}\n"
    " -e LIBRETRO atari800\n"
    " -e CONFIGFILE /storage/emulated/0/Android/data/com.retroarch.aarch64/files/retroarch.cfg"
)
AX360E = "-n aenu.ax360e/aenu.ax360e.EmulatorActivity\n -a aenu.intent.action.AX360E\n -e game_uri {file.uri}"
DOLPHIN = "-n org.dolphinemu.dolphinemu/.ui.main.MainActivity\n -a android.intent.action.MAIN\n -e AutoStartFile {file.uri}"
EDEN = "-n dev.eden.eden_emulator/org.yuzu.yuzu_emu.activities.EmulationActivity\n -a android.nfc.action.TECH_DISCOVERED\n -d {file.uri}"
PPSSPP = (
    "-n org.ppsspp.ppsspp/.PpssppActivity\n -a android.intent.action.VIEW\n -c android.intent.category.DEFAULT\n"
    " -d {file.uri}\n -t application/octet-stream\n --activity-clear-task  --activity-clear-top  --activity-no-history"
)
DUCKSTATION = (
    "-n com.github.stenzek.duckstation/.EmulationActivity\n-e bootPath {file.path}\n --ez resumeState 0\n"
    " --activity-clear-task\n --activity-clear-top"
)
X360MOBILE = "-n emu.x360mobile.com/.MainActivity\n -f 0x10008000\n -d {tags.x360url}"


class ParseAmStartTest(unittest.TestCase):

    def test_retroarch_extras_and_file_path(self):
        p = parse_am_start(RETROARCH_ATARI5200)
        self.assertEqual(p.package, "com.retroarch.aarch64")
        self.assertEqual(p.activity, "com.retroarch.browser.retroactivity.RetroActivityFuture")
        self.assertIsNone(p.action)
        self.assertEqual(
            [(e["key"], e["type"], e["value"]) for e in p.extras],
            [("ROM", "string", "{file.path}"), ("LIBRETRO", "string", "atari800"),
             ("CONFIGFILE", "string", "/storage/emulated/0/Android/data/com.retroarch.aarch64/files/retroarch.cfg")],
        )
        self.assertEqual(p.placeholders, ["{file.path}"])
        self.assertEqual(data_type(p), "path")
        self.assertEqual(p.warnings, [])

    def test_custom_action_with_uri_extra(self):
        p = parse_am_start(AX360E)
        self.assertEqual((p.package, p.activity), ("aenu.ax360e", "aenu.ax360e.EmulatorActivity"))
        self.assertEqual(p.action, "aenu.intent.action.AX360E")
        self.assertEqual(p.extras, [{"key": "game_uri", "type": "string", "value": "{file.uri}"}])
        self.assertEqual(data_type(p), "uri")
        self.assertEqual(p.warnings, [])

    def test_relative_activity_is_expanded(self):
        p = parse_am_start(DOLPHIN)
        self.assertEqual(p.activity, "org.dolphinemu.dolphinemu.ui.main.MainActivity")
        self.assertEqual(p.action, "android.intent.action.MAIN")
        self.assertEqual(p.extras[0]["value"], "{file.uri}")

    def test_data_uri(self):
        p = parse_am_start(EDEN)
        self.assertEqual(p.data, "{file.uri}")
        self.assertEqual(p.action, "android.nfc.action.TECH_DISCOVERED")
        self.assertEqual(data_type(p), "uri")
        self.assertEqual(p.extras, [])

    def test_category_mime_and_flags_on_one_line(self):
        p = parse_am_start(PPSSPP)
        self.assertEqual(p.categories, ["android.intent.category.DEFAULT"])
        self.assertEqual(p.mimeType, "application/octet-stream")
        self.assertEqual(p.flags, ["FLAG_ACTIVITY_CLEAR_TASK", "FLAG_ACTIVITY_CLEAR_TOP", "FLAG_ACTIVITY_NO_HISTORY"])
        self.assertEqual(p.warnings, [])

    def test_boolean_extra_and_flags(self):
        p = parse_am_start(DUCKSTATION)
        self.assertEqual(p.extras, [
            {"key": "bootPath", "type": "string", "value": "{file.path}"},
            {"key": "resumeState", "type": "boolean", "value": "0"},
        ])
        self.assertEqual(p.flags, ["FLAG_ACTIVITY_CLEAR_TASK", "FLAG_ACTIVITY_CLEAR_TOP"])
        self.assertEqual(data_type(p), "path")

    def test_numeric_flags_and_custom_placeholder(self):
        p = parse_am_start(X360MOBILE)
        self.assertEqual(p.rawFlags, 0x10008000)
        self.assertEqual(p.flags, ["FLAG_ACTIVITY_MULTIPLE_TASK", "FLAG_ACTIVITY_NEW_TASK"])
        self.assertEqual(p.data, "{tags.x360url}")
        self.assertEqual(p.placeholders, ["{tags.x360url}"])
        self.assertEqual(data_type(p), "none")

    def test_unknown_tokens_are_kept_and_reported(self):
        p = parse_am_start("-n a.b/.C --frobnicate 3 -e K V")
        self.assertEqual(p.package, "a.b")
        self.assertIn("--frobnicate", p.unparsed)
        self.assertIn("3", p.unparsed)
        self.assertTrue(any("unknown token" in w for w in p.warnings))
        self.assertEqual(p.extras, [{"key": "K", "type": "string", "value": "V"}])

    def test_quoted_values_and_missing_value(self):
        p = parse_am_start('-n a.b/.C --es TITLE "Two words" -e ONLYKEY')
        self.assertEqual(p.extras[0], {"key": "TITLE", "type": "string", "value": "Two words"})
        self.assertTrue(any("missing" in w for w in p.warnings))

    def test_missing_package_is_a_warning(self):
        p = parse_am_start("-a android.intent.action.VIEW -d {file.uri}")
        self.assertIsNone(p.package)
        self.assertIn("no package (-n or -p)", p.warnings)


class ExtensionsTest(unittest.TestCase):

    def test_escaped_and_unescaped_dot(self):
        self.assertEqual(extensions_from_regex(r"^(.*)\.(?:a52|zip|7z)$"), (["7z", "a52", "zip"], None))
        self.assertEqual(extensions_from_regex(r"^(.*).(?:java|jar)$"), (["jar", "java"], None))

    def test_generic_filter_yields_nothing(self):
        exts, warning = extensions_from_regex(r"^(?!(?:\._|\.).*).*$")
        self.assertEqual(exts, [])
        self.assertIsNotNone(warning)


if __name__ == "__main__":
    unittest.main()
