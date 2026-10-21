#!/usr/bin/env python3
"""Capture live production/rollback references over SSH; never change the server."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time

REMOTE = r'''
import json, pathlib, re, subprocess
from datetime import datetime, timezone
root = pathlib.Path('/opt/server-docker-1')
runtime = root / '.state/validated-deploy/app_custom_fields'
image = 'ghcr.io/' + OWNER + '/nuvemshop-custom-fields'

def read(path):
    return json.loads(path.read_text())

def docker(*args):
    return json.loads(subprocess.check_output(['docker', *args], text=True))

state = read(runtime / 'state.json')
if state.get('attempt'):
    raise RuntimeError('Deploy em andamento; aguarde concluir antes da limpeza.')
approved = read(runtime / 'approved-images.json')
baseline_path = root / '.state/full-rollback/pre-pilot/baseline.json'
baseline = read(baseline_path)
ids = set(state['slots'].values()) | set(approved[-2:])
ids.add(baseline['services']['app_custom_fields']['image'])
for path in runtime.glob('recovery-evidence-*/state.json'):
    snapshot = read(path)
    ids.update(snapshot.get('slots', {}).values())

# Preserved stopped containers are recovery references too.
names = subprocess.check_output(['docker', 'ps', '-a', '--format', '{{.Names}}'], text=True).splitlines()
containers = {name: docker('inspect', name)[0]['Image'] for name in names if 'custom-fields' in name}
ids.update(containers.values())
if not state['slots']:
    raise RuntimeError('Nenhuma instância de produção identificada; limpeza bloqueada.')
refs = {'latest'}
for identity in ids:
    info = docker('image', 'inspect', identity)[0]
    version = (info.get('Config', {}).get('Labels') or {}).get('org.opencontainers.image.version', '')
    if re.fullmatch(r'v[0-9]+(?:\.[0-9]+){0,3}', version):
        refs.add(version)
    else:
        digests = [ref.split('@', 1)[1] for ref in info.get('RepoDigests', []) if ref.startswith(image + '@')]
        if not digests:
            raise RuntimeError('Imagem de recuperação sem tag/digest GHCR identificável; limpeza bloqueada.')
        refs.update(digests)
if read(runtime / 'state.json') != state or read(runtime / 'approved-images.json') != approved or read(baseline_path) != baseline:
    raise RuntimeError('Estado do deploy mudou durante a captura; tente após a estabilização.')
current_names = subprocess.check_output(['docker', 'ps', '-a', '--format', '{{.Names}}'], text=True).splitlines()
current = {name: docker('inspect', name)[0]['Image'] for name in current_names if 'custom-fields' in name}
if current != containers:
    raise RuntimeError('Containers mudaram durante a captura; aguarde a estabilização.')
print(json.dumps({'image': image, 'captured_at': datetime.now(timezone.utc).isoformat(), 'protected_refs': sorted(refs)}))
'''


def capture(owner, host, key, output):
    if not key.is_file():
        raise RuntimeError('Chave SSH não encontrada; configure GHCR_PROTECTION_SSH_KEY.')
    result = subprocess.run(['ssh', '-i', str(key), '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=15',
                             '--', host, 'python3 -'], input='OWNER = ' + repr(owner) + '\n' + REMOTE,
                            text=True, capture_output=True, timeout=90)
    if result.returncode:
        # SSH/remote code never handles secrets; retain diagnostics without dumping image inspect.
        raise RuntimeError('Captura de produção/rollback falhou: ' + result.stderr.strip()[-1200:])
    payload = json.loads(result.stdout)
    if payload.get('image') != 'ghcr.io/' + owner + '/nuvemshop-custom-fields' or not payload.get('protected_refs'):
        raise RuntimeError('Captura de proteção inválida.')
    output.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix='.ghcr-protected-', dir=output.parent)
    try:
        with os.fdopen(fd, 'w') as stream:
            json.dump(payload, stream, indent=2)
            stream.write('\n')
        os.replace(temporary, output)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    print('Proteção atualizada a partir do servidor: ' + str(output))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--owner', default=os.environ.get('GHCR_USER', 'douglaspribeiro'))
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--host', default=os.environ.get('GHCR_PROTECTION_SSH_HOST', 'ubuntu@137.131.163.33'))
    parser.add_argument('--key', type=Path, default=Path(os.environ.get('GHCR_PROTECTION_SSH_KEY',
        str(Path(__file__).resolve().parents[2] / 'server/server-docker-1/keys/ssh-key-2026-05-26.key'))))
    parser.add_argument('--wait-seconds', type=int, default=0)
    args = parser.parse_args()
    if args.wait_seconds < 0:
        parser.error('--wait-seconds deve ser não negativo')
    deadline = time.monotonic() + args.wait_seconds
    while True:
        try:
            capture(args.owner, args.host, args.key, args.output)
            break
        except RuntimeError as error:
            transient = any(message in str(error) for message in (
                'Deploy em andamento', 'Estado do deploy mudou', 'Containers mudaram'))
            remaining = deadline - time.monotonic()
            if not transient or remaining <= 0:
                raise
            print('Aguardando estabilização do deploy para capturar a proteção...', file=sys.stderr)
            time.sleep(min(5, remaining))


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, ValueError, subprocess.SubprocessError) as error:
        print('Erro: ' + str(error), file=sys.stderr)
        sys.exit(1)
