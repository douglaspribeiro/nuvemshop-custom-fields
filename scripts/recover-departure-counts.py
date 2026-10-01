#!/usr/bin/env python3
"""Gera SQL revisável de totais anônimos; não conecta nem altera o banco.

Entrada: logs anteriores ao início da contagem V30, ordenados cronologicamente.
Só recupera saídas com exclusão do cadastro confirmada, para não sobrepor a
importação dos cadastros ainda existentes feita pela migração V30.
"""
import argparse
from collections import Counter
from datetime import datetime
import re
import sys
from zoneinfo import ZoneInfo


def recover(lines, before, log_zone):
    uninstalls = {}
    erased = set()
    counts = Counter()
    for line in lines:
        timestamp = re.match(r"(\d{4}-\d\d-\d\d \d\d:\d\d:\d\d\.\d+)", line)
        store = re.search(r"\bstore_id=(\d+)\b", line)
        if not timestamp or not store:
            continue
        instant = datetime.fromisoformat(timestamp[1]).replace(tzinfo=log_zone)
        if instant >= before:
            continue
        store_id = store[1]
        if "event_type=oauth.installed " in line:
            uninstalls.pop(store_id, None)
            erased.discard(store_id)
        elif "webhook.receive.valid event=app/uninstalled " in line:
            if store_id not in erased:
                uninstalls.setdefault(store_id, instant)
        elif "lgpd.store_redact.completed " in line and "store_record_deleted=true" in line:
            if store_id in erased:
                continue
            occurred = uninstalls.pop(store_id, None)
            source = "uninstall" if occurred else "erasure"
            day = (occurred or instant).astimezone(ZoneInfo("America/Sao_Paulo")).date()
            counts[day, source] += 1
            erased.add(store_id)
    return counts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--before", required=True, help="Instante ISO com fuso do início da migração V30")
    parser.add_argument("--log-timezone", default="UTC", help="Fuso usado pelos timestamps dos logs")
    args = parser.parse_args()
    before = datetime.fromisoformat(args.before.replace("Z", "+00:00"))
    if before.tzinfo is None:
        parser.error("--before precisa incluir o fuso horário")
    counts = recover(sys.stdin, before, ZoneInfo(args.log_timezone))
    print("-- Totais recuperados de cadastros excluídos; nenhum identificador de loja.")
    print("START TRANSACTION;")
    for day in sorted({key[0] for key in counts}):
        print("INSERT INTO store_departure_daily (departure_day, recovered_uninstall_count, recovered_erasure_count) "
              f"VALUES ('{day}', {counts[day, 'uninstall']}, {counts[day, 'erasure']}) "
              "ON DUPLICATE KEY UPDATE recovered_uninstall_count = VALUES(recovered_uninstall_count), "
              "recovered_erasure_count = VALUES(recovered_erasure_count);")
    print("COMMIT;")


if __name__ == "__main__":
    main()
