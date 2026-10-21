import importlib.util
import contextlib
import io
import json
from pathlib import Path
import tempfile
from datetime import datetime, timedelta, timezone
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("cleanup_ghcr", Path(__file__).resolve().parents[1] / "cleanup-ghcr.py")
cleanup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cleanup)
NOW = datetime(2026, 10, 20, tzinfo=timezone.utc)


def row(i, tags=(), days=60):
    created = (NOW - timedelta(days=days)).isoformat()
    return cleanup.version({"id": i, "name": "sha256:" + f"{i:064x}", "created_at": created,
        "updated_at": created, "metadata": {"container": {"tags": list(tags)}}})


def image():
    return {"schemaVersion": 2, "config": {"digest": "sha256:" + "a" * 64}, "layers": []}


def index(*children):
    return {"schemaVersion": 2, "manifests": [{"digest": child["digest"]} for child in children]}


class RetentionTests(unittest.TestCase):
    def planned(self, rows, keep=1, refs=(), manifests=None):
        return cleanup.plan(rows, keep, 0, set(refs), lambda d: manifests[d] if manifests else image(), NOW)

    def test_keeps_only_newest_tagged_image_and_latest_without_age_or_custom_tag_exceptions(self):
        versions = [row(1, ["v1.0.0"]), row(2, ["v1.1.0"], 45), row(3, ["latest"], 60),
                    row(4, ["rollback"], 90), row(5, [], 2)]
        self.assertEqual(sorted(v["id"] for v in self.planned(versions)), [1, 4, 5])

    def test_default_keeps_twenty_images_even_when_all_uploaded_today(self):
        versions = [row(i, [f"v1.0.{i}"], 0) for i in range(1, 31)]
        self.assertEqual([v["id"] for v in self.planned(versions, keep=20)], list(range(1, 11)))

    def test_optional_age_protection_is_still_explicit(self):
        versions = [row(1, ["v1.0.0"], 2), row(2, ["v2.0.0"], 1)]
        self.assertEqual(cleanup.plan(versions, 1, 30, set(), lambda _: image(), NOW), [])

    def test_old_production_and_rollback_are_preserved(self):
        versions = [row(1, ["v1.0.0"], 100), row(2, ["v1.1.0"], 90), row(3, ["v1.2.0"], 70), row(4, ["v1.3.0"], 50)]
        self.assertEqual([v["id"] for v in self.planned(versions, refs=["v1.0.0", versions[1]["digest"]])], [3])

    def test_preserves_untagged_platform_and_attestation_manifests_of_kept_index(self):
        root, platform, attestation, obsolete = row(1, ["latest"]), row(2), row(3), row(4)
        manifests = {root["digest"]: index(platform, attestation), platform["digest"]: image(), attestation["digest"]: image()}
        self.assertEqual([v["id"] for v in self.planned([root, platform, attestation, obsolete], manifests=manifests)], [4])

    def test_recently_retagged_old_version_is_not_protected_by_age(self):
        old = row(1, ["v1.0.0"]); old["updated"] = NOW - timedelta(days=1)
        self.assertEqual([v["id"] for v in self.planned([old, row(2, ["v2.0.0"], 40)])], [1])

    def test_shared_child_of_old_and_new_index_is_kept(self):
        old, new, shared = row(1, ["v1.0.0"], 100), row(2, ["v2.0.0"], 60), row(3, [], 100)
        manifests = {new["digest"]: index(shared), shared["digest"]: image()}
        self.assertEqual([v["id"] for v in self.planned([old, new, shared], manifests=manifests)], [1])

    def test_missing_protected_ref_unknown_manifest_and_duplicate_inventory_fail_closed(self):
        root = row(1, ["latest"])
        for kwargs in ({"refs": ["v99.0.0"]}, {"manifests": {root["digest"]: {}}}):
            with self.assertRaises(cleanup.RetentionError): self.planned([root], **kwargs)
        with self.assertRaises(cleanup.RetentionError): self.planned([root, root])
        with self.assertRaises(cleanup.RetentionError): self.planned([root, row(2, ["latest"])])

    def test_protection_file_is_scoped_and_must_be_recent(self):
        with tempfile.TemporaryDirectory() as folder:
            file = Path(folder) / "protected.json"
            payload = {"image": "ghcr.io/owner/nuvemshop-custom-fields", "captured_at": NOW.isoformat(), "protected_refs": ["v1.0.0"]}
            file.write_text(json.dumps(payload))
            self.assertEqual(cleanup.protected_refs(file, payload["image"], NOW), {"v1.0.0"})
            with self.assertRaises(cleanup.RetentionError): cleanup.protected_refs(file, "ghcr.io/other/package", NOW)
            with self.assertRaises(cleanup.RetentionError): cleanup.protected_refs(file, payload["image"], NOW + timedelta(days=2))

    def test_lists_all_pages_before_computing_retention(self):
        client = cleanup.Client("owner", "fixture-not-a-token")
        raw = [{"id": n, "name": "sha256:" + f"{n:064x}", "created_at": NOW.isoformat(), "updated_at": NOW.isoformat(), "metadata": {"container": {"tags": []}}} for n in range(1, 102)]
        with patch.object(client, "api", side_effect=[raw[:100], raw[100:]]) as api:
            self.assertEqual(len(client.versions()), 101)
            self.assertIn("page=2", api.call_args.args[0])

    def test_changed_version_cannot_be_deleted(self):
        client = cleanup.Client("owner", "fixture-not-a-token")
        original = row(1, ["v1.0.0"])
        changed = {"id": 1, "name": original["digest"], "created_at": original["created"].isoformat(),
                   "updated_at": NOW.isoformat(), "metadata": {"container": {"tags": ["latest"]}}}
        with patch.object(client, "api", return_value=changed) as api:
            with self.assertRaises(cleanup.RetentionError): client.delete(original)
            self.assertEqual(api.call_count, 1)

    def test_apply_without_protection_file_stops_before_any_network_access(self):
        with patch.object(cleanup, "Client") as client, contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit): cleanup.main(["--apply"])
            client.assert_not_called()

    def test_dry_run_never_calls_delete(self):
        with patch.dict(cleanup.os.environ, {"GHCR_TOKEN": "fixture-not-a-token"}, clear=True), patch.object(cleanup, "Client") as factory, contextlib.redirect_stdout(io.StringIO()):
            client = factory.return_value; client.versions.return_value = [row(1, ["v1.0.0"], 90), row(2, ["v2.0.0"], 60)]
            client.manifest.return_value = image()
            self.assertEqual(cleanup.main(["--keep", "1"]), 0)
            client.delete.assert_not_called()


if __name__ == "__main__":
    unittest.main()
