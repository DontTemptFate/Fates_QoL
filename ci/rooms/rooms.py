#!/usr/bin/env python3
"""Shared dungeon room pack for Fates OFA.

Players export what OFA learned (/ofa rooms export) and attach the file to a "[rooms]" GitHub issue.
  ingest  reads the issue event, downloads the attached share files, checks them, saves each one under
          data/rooms/contrib/ and writes a reply for the issue (summary.md)
  build   merges every saved contribution into data/rooms/pack.json, which every OFA install downloads

A secret goes into the pack only once at least MIN_SEEN different people found it at the same spot, so
one person can't put fake secrets into everyone's game. "People" are GitHub accounts, except for files
forwarded by an account listed as "gh:<name>" in data/rooms/trusted.txt (e.g. files sent on Discord),
which count by the contributor id inside the file. A contributor listed as "cid:<id>" counts as confirmed
on their own.

Standard library only; nothing from the issue is ever run or passed to a shell.
"""
import json
import math
import os
import re
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "data" / "rooms"
CONTRIB = DATA / "contrib"
PACK = DATA / "pack.json"
TRUSTED = DATA / "trusted.txt"

TYPE = "ofa-rooms"
MIN_SEEN = 2
SAME = 1.6
GUESS = 2.6
MAX_ROOMS, MAX_SECRETS, MAX_POINTS, MAX_FILE = 4000, 60, 600, 5 * 1024 * 1024
KINDS = {"CHEST", "ITEM", "BAT", "ESSENCE", "LEVER", "OTHER"}
ROOM_ID = re.compile(r"^[0-9a-f]{16}$")
CID = re.compile(r"^[a-f0-9]{12}$")
WHO = re.compile(r"^[A-Za-z0-9:_-]{1,48}$")
LOGIN = re.compile(r"^[A-Za-z0-9-]{1,39}$")
ATTACHMENT = re.compile(r"https://github\.com/user-attachments/files/\d+/[A-Za-z0-9._-]{1,120}")


# ---- reading share files (same rules as RoomStore.fromJson in the mod) ----

def num(v):
    if isinstance(v, bool) or not isinstance(v, (int, float)):
        return None
    v = float(v)
    return v if math.isfinite(v) else None


def in_room(x, y, z):
    return abs(x) <= 16 and abs(z) <= 16 and 0 <= y <= 255


def in_route(x, y, z):
    return abs(x) <= 130 and abs(z) <= 130 and 0 <= y <= 255


def parse_store(obj):
    """A cleaned share file: {"contributor": id|None, "rooms": {id: {"s": [[x,y,z,kind]], "r": {...}|None}}}.
    Raises ValueError when it isn't an OFA rooms file at all."""
    if not isinstance(obj, dict) or obj.get("type") != TYPE or not isinstance(obj.get("rooms"), dict):
        raise ValueError("not an OFA room file (make it with /ofa rooms export)")
    cid = obj.get("contributor")
    cid = cid if isinstance(cid, str) and CID.match(cid) else None
    rooms = {}
    for rid, r in obj["rooms"].items():
        if len(rooms) >= MAX_ROOMS:
            break
        if not isinstance(rid, str) or not ROOM_ID.match(rid) or not isinstance(r, dict):
            continue
        secrets = []
        for s in r.get("s", []) if isinstance(r.get("s"), list) else []:
            if len(secrets) >= MAX_SECRETS:
                break
            if not isinstance(s, list) or len(s) < 4:
                continue
            x, y, z = num(s[0]), num(s[1]), num(s[2])
            kind = s[3] if isinstance(s[3], str) and s[3].upper() in KINDS else None
            if None in (x, y, z) or kind is None or not in_room(x, y, z):
                continue
            secrets.append([round(x, 1), round(y, 1), round(z, 1), kind.upper()])
        route = None
        ro = r.get("r")
        if isinstance(ro, dict):
            ms = num(ro.get("ms"))
            pts = []
            for p in ro.get("p", []) if isinstance(ro.get("p"), list) else []:
                if len(pts) >= MAX_POINTS:
                    break
                if isinstance(p, list) and len(p) >= 3:
                    x, y, z = num(p[0]), num(p[1]), num(p[2])
                    if None not in (x, y, z) and in_route(x, y, z):
                        pts.append([round(x, 1), round(y, 1), round(z, 1)])
            if ms is not None and 1000 < ms < 3_600_000 and len(pts) >= 2:
                route = {"ms": int(ms), "p": pts}
        if secrets or route:
            rooms[rid] = {"s": secrets, "r": route}
    return {"contributor": cid, "rooms": rooms}


# ---- ingest ----

def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "FatesOFA-room-pack"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = resp.read(MAX_FILE + 1)
    if len(data) > MAX_FILE:
        raise ValueError("file is larger than 5 MB")
    return data


def ingest(summary_path):
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    issue = event["issue"]
    author = issue["user"]["login"]
    number = int(issue["number"])
    body = issue.get("body") or ""
    lines = []
    if not LOGIN.match(author):
        lines.append("Rejected: unexpected account name.")
    urls = list(dict.fromkeys(ATTACHMENT.findall(body)))[:5]
    if not urls:
        lines.append("No share file found. In game run `/ofa rooms export`, then drag the file from "
                     "`config/fatesofa/rooms/export/` into the issue (edit the issue to add it).")
    accepted = 0
    CONTRIB.mkdir(parents=True, exist_ok=True)
    for i, url in enumerate(urls):
        name = url.rsplit("/", 1)[-1]
        try:
            store = parse_store(json.loads(fetch(url).decode("utf-8")))
            n_secrets = sum(len(r["s"]) for r in store["rooms"].values())
            n_routes = sum(1 for r in store["rooms"].values() if r["r"])
            if not store["rooms"]:
                raise ValueError("the file has no rooms in it")
            out = {"type": TYPE, "v": 1, "contributor": store["contributor"], "submitted_by": author, "issue": number,
                   "rooms": store["rooms"]}
            (CONTRIB / f"{author}-{number}-{i}.json").write_text(json.dumps(out, sort_keys=True, separators=(",", ":")), encoding="utf-8")
            accepted += 1
            lines.append(f"- `{name}`: {len(store['rooms'])} rooms, {n_secrets} secrets, {n_routes} routes")
        except Exception as e:  # noqa: BLE001 — every file gets a plain answer
            lines.append(f"- `{name}`: not used ({e})")
    head = (f"Accepted {accepted} file(s), thank you! Secrets join the shared pack once another player has found them too; "
            "OFA downloads the pack about once an hour.") if accepted else "Nothing was added."
    Path(summary_path).write_text(head + "\n\n" + "\n".join(lines) + "\n", encoding="utf-8")
    return 0


# ---- build ----

def trusted():
    gh, cid = set(), set()
    if TRUSTED.exists():
        for line in TRUSTED.read_text(encoding="utf-8").splitlines():
            line = line.split("#", 1)[0].strip()
            if line.startswith("gh:") and LOGIN.match(line[3:]):
                gh.add(line[3:].lower())
            elif line.startswith("cid:") and CID.match(line[4:]):
                cid.add(line[4:])
    return gh, cid


def matches(c, f):
    dx, dy, dz = c["x"] - f[0], c["y"] - f[1], c["z"] - f[2]
    if dx * dx + dy * dy + dz * dz <= SAME * SAME:
        return True
    if c["kind"] != "OTHER" and f[3] != "OTHER":
        return False
    return dx * dx + dz * dz <= GUESS * GUESS and abs(dy) <= 4.5


def median(vals):
    v = sorted(vals)
    n = len(v)
    return v[n // 2] if n % 2 else (v[n // 2 - 1] + v[n // 2]) / 2


def build():
    trusted_gh, trusted_cid = trusted()
    finds = {}   # room -> [(x, y, z, kind, key)]
    routes = {}  # room -> best route
    people = set()
    for f in sorted(CONTRIB.glob("*.json")) if CONTRIB.exists() else []:
        try:
            raw = json.loads(f.read_text(encoding="utf-8"))
            store = parse_store(raw)
        except Exception as e:  # noqa: BLE001
            print(f"skipping {f.name}: {e}", file=sys.stderr)
            continue
        author = str(raw.get("submitted_by", "")).lower()
        cid = store["contributor"]
        key = ("cid:" + cid) if (author in trusted_gh and cid) else ("gh:" + author)
        people.add(key)
        for rid, r in store["rooms"].items():
            for s in r["s"]:
                finds.setdefault(rid, []).append((s[0], s[1], s[2], s[3], key))
            rt = r["r"]
            if rt and (rid not in routes or rt["ms"] < routes[rid]["ms"]):
                routes[rid] = rt

    def weight(keys):
        return sum(MIN_SEEN if k[4:] in trusted_cid and k.startswith("cid:") else 1 for k in keys)

    rooms = {}
    for rid in sorted(finds):
        clusters = []
        # precise finds first, so a guess joins the real spot instead of starting its own
        for fd in sorted(finds[rid], key=lambda t: (t[3] == "OTHER", t[0], t[1], t[2], t[4])):
            for c in clusters:
                if matches(c, fd):
                    c["members"].append(fd)
                    break
            else:
                clusters.append({"x": fd[0], "y": fd[1], "z": fd[2], "kind": fd[3], "members": [fd]})
        out = []
        for c in clusters:
            keys = {m[4] for m in c["members"]}
            if weight(keys) < MIN_SEEN:
                continue
            precise = [m for m in c["members"] if m[3] != "OTHER"] or c["members"]
            kinds = {}
            for m in precise:
                kinds[m[3]] = kinds.get(m[3], 0) + 1
            kind = sorted(kinds.items(), key=lambda kv: (-kv[1], kv[0]))[0][0]
            out.append([round(median([m[0] for m in precise]), 1), round(median([m[1] for m in precise]), 1),
                        round(median([m[2] for m in precise]), 1), kind, len(keys)])
        if out:
            out.sort()
            room = {"s": out[:MAX_SECRETS]}
            if rid in routes:
                room["r"] = routes[rid]
            rooms[rid] = room
    pack = {"type": TYPE, "v": 1, "contributors": len(people), "rooms": rooms}
    DATA.mkdir(parents=True, exist_ok=True)
    text = json.dumps(pack, sort_keys=True, separators=(",", ":")) + "\n"
    if not PACK.exists() or PACK.read_text(encoding="utf-8") != text:
        PACK.write_text(text, encoding="utf-8")
    print(f"pack: {len(rooms)} rooms, {sum(len(r['s']) for r in rooms.values())} secrets, {len(people)} contributors")
    return 0


if __name__ == "__main__":
    if len(sys.argv) >= 2 and sys.argv[1] == "ingest":
        sys.exit(ingest(sys.argv[2] if len(sys.argv) > 2 else "summary.md"))
    if len(sys.argv) >= 2 and sys.argv[1] == "build":
        sys.exit(build())
    print("usage: rooms.py ingest [summary.md] | build", file=sys.stderr)
    sys.exit(2)
