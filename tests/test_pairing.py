import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("credentials", Path(__file__).resolve().parents[1]/"custom_components/bravia_launcher/credentials.py")
credentials = importlib.util.module_from_spec(spec)
spec.loader.exec_module(credentials)

class PairingTests(unittest.TestCase):
    def test_expiration_and_rotation(self):
        now = [1000]
        auth = credentials.Pairing(lambda: now[0])
        first = auth.issue("screen")
        second = auth.issue("screen")
        if first != second:
            self.assertIsNone(auth.redeem(first))
        self.assertEqual(auth.redeem(second), "screen")
        self.assertIsNone(auth.redeem(second))
        stale = auth.issue("other")
        now[0] += 180
        self.assertIsNone(auth.redeem(stale))

    def test_rate_window_and_distinct_screens(self):
        now = [0]
        auth = credentials.Pairing(lambda: now[0])
        for _ in range(8):
            self.assertIsNone(auth.redeem("invalid"))
        with self.assertRaises(PermissionError):
            auth.redeem(auth.issue("a"))
        now[0] += 181
        self.assertEqual(auth.redeem(auth.issue("b")), "b")
