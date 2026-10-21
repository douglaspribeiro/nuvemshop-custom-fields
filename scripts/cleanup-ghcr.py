#!/usr/bin/env python3
"""Plan scoped GHCR retention; deleting requires --apply; protected refs are optional."""
import argparse
import base64
from datetime import datetime, timedelta, timezone
import json
import os
from pathlib import Path
import re
import sys
import subprocess
import urllib.error
import urllib.parse
import urllib.request

PACKAGE = "nuvemshop-custom-fields"
DIGEST = re.compile(r"sha256:[a-f0-9]{64}\Z")
MANIFEST_TYPES = ", ".join(("application/vnd.oci.image.index.v1+json",
    "application/vnd.docker.distribution.manifest.list.v2+json",
    "application/vnd.oci.image.manifest.v1+json", "application/vnd.docker.distribution.manifest.v2+json"))


class RetentionError(Exception):
    pass


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise RetentionError("Redirecionamento inesperado; operação interrompida.")


def resolve_token():
    token = os.environ.get("GHCR_TOKEN", "").strip()
    if token:
        return token
    config = Path(os.environ.get("DOCKER_CONFIG", str(Path.home() / ".docker"))) / "config.json"
    try:
        data = json.loads(config.read_text())
        keys = ("ghcr.io", "https://ghcr.io", "https://ghcr.io/v1/")
        helper = next((data.get("credHelpers", {}).get(key) for key in keys
                       if data.get("credHelpers", {}).get(key)), None) or data.get("credsStore")
        if helper:
            if not re.fullmatch(r"[A-Za-z0-9._-]+", helper):
                raise ValueError()
            result = subprocess.run(["docker-credential-" + helper, "get"], input="ghcr.io\n",
                                    capture_output=True, text=True, check=True, timeout=15)
            token = json.loads(result.stdout).get("Secret", "").strip()
        else:
            entry = next((data.get("auths", {}).get(key) for key in keys
                          if data.get("auths", {}).get(key)), {})
            decoded = base64.b64decode(entry.get("auth", ""), validate=True).decode()
            token = decoded.split(":", 1)[1].strip() if ":" in decoded else ""
        if token:
            return token
    except (OSError, ValueError, TypeError, AttributeError, subprocess.SubprocessError):
        pass
    raise RetentionError("Credencial GHCR ausente: execute docker login ghcr.io ou defina GHCR_TOKEN. "
                         "A exclusão exige read:packages e delete:packages.")


def utc(value):
    try:
        result = datetime.fromisoformat(value.replace("Z", "+00:00"))
        if result.tzinfo is None:
            raise ValueError()
        return result.astimezone(timezone.utc)
    except (ValueError, TypeError, AttributeError) as error:
        raise RetentionError("Timestamp inválido na lista ou no arquivo de proteção.") from error


def version(value):
    try:
        identity, digest = value["id"], value["name"]
        tags = value["metadata"]["container"]["tags"]
        if type(identity) is not int or identity <= 0 or not DIGEST.fullmatch(digest):
            raise ValueError()
        if not isinstance(tags, list) or any(not isinstance(t, str) or not t for t in tags):
            raise ValueError()
        return {"id": identity, "digest": digest, "tags": sorted(tags),
                "created": utc(value["created_at"]), "updated": utc(value["updated_at"])}
    except (KeyError, TypeError, ValueError) as error:
        raise RetentionError("Versão de pacote inválida; nenhuma exclusão autorizada.") from error


def protected_refs(path, image, now):
    if path is None:
        return set()
    try:
        payload = json.loads(Path(path).read_text())
        refs = payload["protected_refs"]
        captured = utc(payload["captured_at"])
        if payload["image"] != image or not isinstance(refs, list) or not refs:
            raise ValueError()
        if any(not isinstance(ref, str) or not ref for ref in refs):
            raise ValueError()
        if captured > now + timedelta(minutes=5) or now - captured > timedelta(hours=24):
            raise RetentionError("Atualize o arquivo de proteção: captura deve ter menos de 24 horas.")
        return set(refs)
    except (OSError, ValueError, KeyError, TypeError) as error:
        raise RetentionError("Arquivo de proteção inválido ou de outra imagem.") from error


def plan(versions, keep, min_age_days, refs, manifest, now):
    """Preserve roots and their full OCI graph before selecting package version IDs."""
    by_digest = {v["digest"]: v for v in versions}
    if len(by_digest) != len(versions) or len({v["id"] for v in versions}) != len(versions):
        raise RetentionError("Inventário mudou durante a paginação ou contém versões duplicadas.")
    lookup = {tag: v["digest"] for v in versions for tag in v["tags"]}
    if len(lookup) != sum(len(v["tags"]) for v in versions):
        raise RetentionError("Tags duplicadas no inventário; tente novamente após as publicações terminarem.")
    lookup.update({v["digest"]: v["digest"] for v in versions})
    missing = refs - lookup.keys()
    if missing:
        raise RetentionError("Referência protegida ausente no inventário; confira tags/digests.")
    newest = sorted((v for v in versions if v["tags"]),
                    key=lambda v: (v["created"], v["id"]), reverse=True)[:keep]
    roots = {v["digest"] for v in newest} | {lookup[ref] for ref in refs}
    cutoff = now - timedelta(days=min_age_days)
    for v in versions:
        if "latest" in v["tags"] or (min_age_days > 0 and max(v["created"], v["updated"]) >= cutoff):
            roots.add(v["digest"])
    kept, visiting = set(), set()

    def visit(digest):
        if digest in kept:
            return
        if not DIGEST.fullmatch(digest) or digest in visiting:
            raise RetentionError("Grafo de manifests inválido; limpeza bloqueada.")
        visiting.add(digest)
        data = manifest(digest)
        if not isinstance(data, dict) or data.get("schemaVersion") != 2:
            raise RetentionError("Manifest desconhecido; limpeza bloqueada.")
        if "manifests" in data:
            if not isinstance(data["manifests"], list) or not data["manifests"]:
                raise RetentionError("Índice de imagem inválido.")
            for child in data["manifests"]:
                if not isinstance(child, dict):
                    raise RetentionError("Descritor de manifest inválido.")
                visit(child.get("digest", ""))
        elif not isinstance(data.get("config"), dict) or not isinstance(data.get("layers"), list):
            raise RetentionError("Manifest de imagem inválido.")
        # Subject references are used by OCI artifacts/attestations.
        if "subject" in data:
            if not isinstance(data["subject"], dict):
                raise RetentionError("Referência de artifact inválida.")
            visit(data["subject"].get("digest", ""))
        visiting.remove(digest)
        kept.add(digest)

    for root in sorted(roots):
        visit(root)
    removable = [v for v in versions if v["digest"] not in kept]
    # Remove old root/index records first, then their unreferenced children.
    return sorted(removable, key=lambda v: (not bool(v["tags"]), v["created"], v["id"]))


class Client:
    def __init__(self, owner, token):
        self.owner, self.token = owner, token
        self.opener = urllib.request.build_opener(NoRedirect())
        self.registry_token = None
        self.base = "/users/" + urllib.parse.quote(owner, safe="") + "/packages/container/" + PACKAGE

    def request(self, url, headers, method="GET"):
        try:
            req = urllib.request.Request(url, headers=headers, method=method)
            with self.opener.open(req, timeout=30) as response:
                return json.load(response) if method != "DELETE" else None
        except urllib.error.HTTPError as error:
            raise RetentionError("GHCR/GitHub retornou HTTP " + str(error.code)
                                 + ". Confira token clássico e permissões read:packages/delete:packages.") from None
        except (urllib.error.URLError, TimeoutError, ValueError) as error:
            raise RetentionError("Não foi possível confirmar a resposta do GHCR/GitHub; limpeza interrompida.") from None

    def api(self, path, method="GET"):
        return self.request("https://api.github.com" + path,
            {"Authorization": "Bearer " + self.token, "Accept": "application/vnd.github+json",
             "X-GitHub-Api-Version": "2022-11-28"}, method)

    def versions(self):
        rows = []
        for page in range(1, 1001):
            batch = self.api(self.base + "/versions?per_page=100&page=" + str(page))
            if not isinstance(batch, list):
                raise RetentionError("Inventário de versões inválido.")
            rows.extend(version(v) for v in batch)
            if len(batch) < 100:
                return rows
        raise RetentionError("Inventário excedeu limite de paginação; limpeza bloqueada.")

    def manifest(self, digest):
        if self.registry_token is None:
            scope = "repository:" + self.owner + "/" + PACKAGE + ":pull"
            basic = base64.b64encode((self.owner + ":" + self.token).encode()).decode()
            data = self.request("https://ghcr.io/token?" + urllib.parse.urlencode({"service": "ghcr.io", "scope": scope}),
                                {"Authorization": "Basic " + basic})
            self.registry_token = data.get("token")
            if not isinstance(self.registry_token, str) or not self.registry_token:
                raise RetentionError("Não foi possível autenticar a leitura dos manifests.")
        return self.request("https://ghcr.io/v2/" + self.owner + "/" + PACKAGE + "/manifests/" + digest,
                            {"Authorization": "Bearer " + self.registry_token, "Accept": MANIFEST_TYPES})

    def delete(self, v):
        # The authenticated user must own this personal package (no inferred org deletion).
        current = version(self.api(self.base + "/versions/" + str(v["id"])))
        if current != v:
            raise RetentionError("Uma versão mudou após a simulação; gere um novo plano.")
        self.api("/user/packages/container/" + PACKAGE + "/versions/" + str(v["id"]), "DELETE")


def fingerprint(versions):
    return sorted((v["id"], v["digest"], tuple(v["tags"]), v["updated"]) for v in versions)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--owner", default=os.environ.get("GHCR_USER", "douglaspribeiro"))
    parser.add_argument("--keep", type=int, default=20)
    parser.add_argument("--min-age-days", type=int, default=0)
    parser.add_argument("--protect-ref", action="append", default=[])
    parser.add_argument("--protected-refs-file", default=os.environ.get("GHCR_PROTECTED_REFS_FILE"))
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--check-credentials", action="store_true", help=argparse.SUPPRESS)
    args = parser.parse_args(argv)
    if args.check_credentials:
        resolve_token()
        return 0
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9-]{0,38}", args.owner) or args.keep < 1 or args.min_age_days < 0:
        parser.error("Informe proprietário válido, quantidade positiva e idade não negativa.")
    token = resolve_token()
    now = datetime.now(timezone.utc)
    image = "ghcr.io/" + args.owner + "/" + PACKAGE
    refs = protected_refs(args.protected_refs_file, image, now) | set(args.protect_ref)
    client = Client(args.owner, token)
    versions = client.versions()
    removable = plan(versions, args.keep, args.min_age_days, refs, client.manifest, now)
    print(("APLICAÇÃO" if args.apply else "SIMULAÇÃO") + ": " + image)
    if not args.protected_refs_file:
        print("Retenção sem consulta ao servidor: últimas imagens publicadas, latest e referências explícitas.")
    print(f"Inventário: {len(versions)}; candidatos: {len(removable)}; manter últimas {args.keep} imagens publicadas e seus manifests necessários.")
    if args.min_age_days > 0:
        print(f"Proteção adicional por idade solicitada: últimos {args.min_age_days} dias.")
    for v in removable:
        print(f"  id={v['id']} digest={v['digest']} tags={','.join(v['tags']) or '(sem tag)'}")
    if not args.apply:
        print("Nenhuma versão apagada. Para excluir, revise o plano e use --apply.")
        return 0
    if str(client.api("/user").get("login", "")).lower() != args.owner.lower():
        raise RetentionError("O token não pertence ao proprietário informado; exclusão bloqueada.")
    if fingerprint(client.versions()) != fingerprint(versions):
        raise RetentionError("O inventário mudou; não execute limpeza junto de outras publicações/rollbacks.")
    for v in removable:
        client.delete(v)
        print("Excluída versão de pacote id=" + str(v["id"]))
    print("Limpeza concluída: " + str(len(removable)) + " versões removidas.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except RetentionError as error:
        print("Erro: " + str(error), file=sys.stderr)
        sys.exit(1)
