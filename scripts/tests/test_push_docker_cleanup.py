"""Exercise the release script with fake commands: no build, push or deletion."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "push-docker.sh"


class PushDockerCleanupTests(unittest.TestCase):
    def run_push(self, options, build_exit=0, capture_fail=False):
        with tempfile.TemporaryDirectory() as folder:
            directory = Path(folder)
            commands = {
                "mvn": 'if [ "$1" = "-version" ]; then echo "Java version: 25"; fi\n',
                "git": 'if [ "$1" = rev-parse ]; then echo fixture-sha; elif [ "$1" = log ]; then echo "feat: fixture"; fi\n',
                "docker": 'if [ "$1 $2" = "buildx build" ]; then exit "${BUILD_EXIT:-0}"; fi\n',
                "python3": 'if [ "$1" = "-c" ]; then echo 1.0.0; elif [[ "$1" = *capture-ghcr-protection.py ]]; then echo capture >> "$CAPTURE_TRACE"; if [[ "${CAPTURE_FAIL:-0}" = 1 && $(wc -l < "$CAPTURE_TRACE") = 2 ]]; then exit 1; fi; elif [ "$2" != "--check-credentials" ]; then printf "%s\\n" "$@" > "$CLEANUP_TRACE"; fi\n',
            }
            for name, body in commands.items():
                file = directory / name; file.write_text("#!/bin/bash\n" + body); file.chmod(0o700)
            trace = directory / "cleanup.trace"
            protection = directory / "protected.json"
            protection.write_text('{}')  # The real helper validates contents; this test exercises shell wiring.
            env = dict(os.environ, PATH=str(directory) + os.pathsep + os.environ["PATH"],
                       GHCR_TOKEN="fixture-not-a-token", GHCR_USER="fixture-owner",
                       CLEANUP_TRACE=str(trace), BUILD_EXIT=str(build_exit),
                       CAPTURE_TRACE=str(directory / "capture.trace"), CAPTURE_FAIL="1" if capture_fail else "0")
            env.pop("GHCR_PROTECTED_REFS_FILE", None)
            result = subprocess.run(["bash", str(SCRIPT), "--no-bump", "--skip-precheck", *options],
                                    env=env, capture_output=True, text=True)
            return result, trace.read_text() if trace.exists() else None

    def test_default_publication_applies_cleanup_with_twenty_images(self):
        result, trace = self.run_push([])
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("--apply", trace)
        self.assertIn("--keep\n20", trace)
        self.assertIn("--min-age-days\n0", trace)
        self.assertIn("--protected-refs-file", trace)

    def test_cleanup_runs_after_publication_and_protects_new_version_and_latest(self):
        result, trace = self.run_push(["--cleanup-ghcr", "--ghcr-keep", "10"])
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("scripts/cleanup-ghcr.py\n", trace)
        self.assertIn("--owner\nfixture-owner", trace)
        self.assertIn("--keep\n10", trace)
        self.assertIn("--protect-ref\nv1.0.0", trace)
        self.assertIn("--protect-ref\nlatest", trace)
        self.assertNotIn("--apply", trace)
        self.assertIn("somente simulação; nenhuma versão será apagada", result.stdout)
        self.assertIn("últimas 10 imagens publicadas", result.stdout)
        self.assertIn("--min-age-days\n0", trace)
        self.assertNotIn("Proteção adicional por idade solicitada", result.stdout)

    def test_failed_refresh_after_publication_never_deletes(self):
        result, trace = self.run_push([], capture_fail=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIsNone(trace)
        self.assertIn("A imagem já foi publicada", result.stderr)

    def test_failed_image_build_never_reaches_cleanup(self):
        result, trace = self.run_push(["--cleanup-ghcr"], build_exit=42)
        self.assertNotEqual(result.returncode, 0)
        self.assertIsNone(trace)


if __name__ == "__main__":
    unittest.main()
