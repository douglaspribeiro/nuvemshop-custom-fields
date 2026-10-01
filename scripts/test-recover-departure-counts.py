import importlib.util
from datetime import date, datetime
from pathlib import Path
import unittest
from zoneinfo import ZoneInfo

spec = importlib.util.spec_from_file_location("recovery", Path(__file__).with_name("recover-departure-counts.py"))
recovery = importlib.util.module_from_spec(spec)
spec.loader.exec_module(recovery)


class RecoveryTest(unittest.TestCase):
    def recover(self, lines):
        return recovery.recover(lines.splitlines(), datetime.fromisoformat("2026-10-01T00:00:00+00:00"), ZoneInfo("UTC"))

    def test_both_orders_and_retries_count_each_erased_installation_once(self):
        counts = self.recover("""
2026-09-30 13:42:27.534 webhook.receive.valid event=app/uninstalled store_id=123
2026-09-30 13:42:27.556 lgpd.store_redact.completed store_id=123 store_record_deleted=true
2026-09-30 13:43:27.556 lgpd.store_redact.completed store_id=123 store_record_deleted=false
2026-09-30 21:13:48.917 lgpd.store_redact.completed store_id=456 store_record_deleted=true
2026-09-30 21:13:49.220 webhook.receive.valid event=app/uninstalled store_id=456
2026-09-30 21:13:50.220 webhook.receive.valid event=app/uninstalled store_id=456
2026-09-30 21:39:22.132 lgpd.store_redact.completed store_id=789 store_record_deleted=true
2026-09-30 21:39:22.276 webhook.receive.valid event=app/uninstalled store_id=789
""")
        self.assertEqual(counts[date(2026, 9, 30), "uninstall"], 1)
        self.assertEqual(counts[date(2026, 9, 30), "erasure"], 2)
        self.assertEqual(sum(counts.values()), 3)

    def test_reinstallation_is_a_new_cycle(self):
        counts = self.recover("""
2026-09-30 13:00:00.000 lgpd.store_redact.completed store_id=123 store_record_deleted=true
2026-09-30 14:00:00.000 integration_log store_id=123 event_type=oauth.installed message=ok
2026-09-30 15:00:00.000 webhook.receive.valid event=app/uninstalled store_id=123
2026-09-30 15:01:00.000 lgpd.store_redact.completed store_id=123 store_record_deleted=true
""")
        self.assertEqual(sum(counts.values()), 2)

    def test_cutoff_and_local_day_and_unerased_stores(self):
        counts = self.recover("""
2026-09-30 01:00:00.000 webhook.receive.valid event=app/uninstalled store_id=123
2026-09-30 01:01:00.000 lgpd.store_redact.completed store_id=123 store_record_deleted=true
2026-09-30 12:00:00.000 webhook.receive.valid event=app/uninstalled store_id=456
2026-10-01 00:00:00.000 lgpd.store_redact.completed store_id=789 store_record_deleted=true
""")
        self.assertEqual(dict(counts), {(date(2026, 9, 29), "uninstall"): 1})


if __name__ == "__main__":
    unittest.main()
