import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('capture', Path(__file__).resolve().parents[1] / 'capture-ghcr-protection.py')
capture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(capture)


class CaptureProtectionTests(unittest.TestCase):
    def remote(self, pending=False, changed=False):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            runtime = root / '.state/validated-deploy/app_custom_fields'
            runtime.mkdir(parents=True)
            baseline = root / '.state/full-rollback/pre-pilot'
            baseline.mkdir(parents=True)
            state = {'slots': {'app-custom-fields':'prod'}, 'attempt': 'pending' if pending else None}
            (runtime / 'state.json').write_text(json.dumps(state))
            (runtime / 'approved-images.json').write_text(json.dumps(['previous', 'prod']))
            (baseline / 'baseline.json').write_text(json.dumps({'services': {'app_custom_fields': {'image':'baseline'}}}))
            (runtime / 'recovery-evidence-test').mkdir()
            (runtime / 'recovery-evidence-test/state.json').write_text(json.dumps({'slots':{'old':'recovery'}}))
            versions={'prod':'v2.0.0', 'previous':'v1.9.0', 'baseline':'v1.0.0', 'recovery':'v1.2.0', 'retained':'v1.3.0'}
            def docker(command, **kwargs):
                if command[1:3] == ['ps', '-a']:
                    return 'app-custom-fields\nretained-custom-fields-old\n'
                if command[1] == 'inspect':
                    identity = 'prod' if command[2] == 'app-custom-fields' else 'retained'
                    return json.dumps([{'Image':identity}])
                if changed:
                    (runtime / 'state.json').write_text(json.dumps({**state, 'attempt':'new'}))
                return json.dumps([{'Config':{'Labels':{'org.opencontainers.image.version': versions[command[-1]]}}}])
            source=capture.REMOTE.replace("pathlib.Path('/opt/server-docker-1')", 'pathlib.Path(' + repr(directory) + ')')
            output=io.StringIO()
            with patch.object(capture.subprocess, 'check_output', side_effect=docker), contextlib.redirect_stdout(output):
                exec(source, {'OWNER':'owner'})
            return json.loads(output.getvalue())

    def test_includes_production_previous_containers_recovery_and_baseline(self):
        result=self.remote()
        self.assertEqual(result['protected_refs'], ['latest','v1.0.0','v1.2.0','v1.3.0','v1.9.0','v2.0.0'])

    def test_pending_or_changing_deploy_blocks_capture(self):
        for options in ({'pending':True}, {'changed':True}):
            with self.assertRaises(RuntimeError): self.remote(**options)

    def test_ssh_failure_does_not_overwrite_existing_protection(self):
        with tempfile.TemporaryDirectory() as directory:
            key=Path(directory)/'key'; key.touch()
            output=Path(directory)/'protection.json'; output.write_text('previous')
            with patch.object(capture.subprocess,'run') as run:
                run.return_value.returncode=1
                run.return_value.stderr='unreachable'
                with self.assertRaises(RuntimeError): capture.capture('owner','server',key,output)
            self.assertEqual(output.read_text(),'previous')


if __name__ == '__main__': unittest.main()
