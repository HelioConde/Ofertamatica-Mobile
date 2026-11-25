"""Recover only synthetic PNG fixtures from instrumentation log chunks."""
import base64
import hashlib
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
EXPECTED = {f"synthetic-{i}" for i in range(1,9)} | {f"negative-{i}" for i in range(1,5)}
META = {}
CHUNKS = {}
for line in (ROOT / "fixture-transfer.txt").read_text().splitlines():
    m = re.search(r"FIXTURE_META (\S+) (\d+) (\d+) ([a-f0-9]{64})$", line)
    if m:
        name, count, size, sha = m.groups()
        if name not in EXPECTED or name in META:
            raise ValueError(f"Unexpected or duplicate metadata: {name}")
        META[name] = (int(count), int(size), sha)
    m = re.search(r"FIXTURE_CHUNK (\S+) (\d+) ([A-Za-z0-9+/=]+)$", line)
    if m:
        name, index, data = m.groups()
        if name not in EXPECTED:
            raise ValueError(f"Unexpected fixture: {name}")
        parts = CHUNKS.setdefault(name, {})
        index = int(index)
        if index in parts:
            raise ValueError(f"Duplicate chunk: {name}/{index}")
        parts[index] = data
if set(META) != EXPECTED or set(CHUNKS) != EXPECTED:
    raise ValueError(f"Incomplete fixture transfer: missing metadata={EXPECTED-set(META)} missing chunks={EXPECTED-set(CHUNKS)}")
out = ROOT / "benchmark-images"
out.mkdir(exist_ok=True)
for name in sorted(EXPECTED):
    count, size, sha = META[name]
    chunks = CHUNKS[name]
    if set(chunks) != set(range(count)):
        raise ValueError(f"Incomplete chunks: {name}")
    data = base64.b64decode("".join(chunks[i] for i in range(count)), validate=True)
    if len(data) != size or hashlib.sha256(data).hexdigest() != sha or not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError(f"Corrupt PNG: {name}")
    (out / f"{name}.png").write_bytes(data)
    print(f"Verified {name}: {size} bytes, sha256={sha}")
print(f"Verified all {len(EXPECTED)} exact synthetic PNGs")
