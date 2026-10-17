#!/usr/bin/env python3
"""Checks the room pack builder: agreement rule, trusted forwarding, bad files. Runs in CI (build workflow)."""
import json
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import rooms  # noqa: E402

A = "00000000000000aa"


def share(cid, secrets, route=None):
    r = {"s": secrets}
    if route:
        r["r"] = route
    return {"type": "ofa-rooms", "v": 1, "contributor": cid, "rooms": {A: r}}


def run():
    with tempfile.TemporaryDirectory() as d:
        base = Path(d)
        rooms.DATA = base
        rooms.CONTRIB = base / "contrib"
        rooms.PACK = base / "pack.json"
        rooms.TRUSTED = base / "trusted.txt"
        rooms.CONTRIB.mkdir()

        def put(name, author, store):
            store = dict(store, submitted_by=author, issue=1)
            (rooms.CONTRIB / name).write_text(json.dumps(store))

        # one person alone: nothing published; the same person twice still one person
        put("alice-1-0.json", "alice", share("aaaaaaaaaaaa", [[3, 70, -4, "CHEST", ["x"]]]))
        put("alice-2-0.json", "alice", share("aaaaaaaaaaab", [[3.2, 70, -4, "CHEST"]]))
        rooms.build()
        assert json.loads(rooms.PACK.read_text())["rooms"] == {}, "one person must not publish"

        # a second person at the same spot (and a guess nearby): published with count 2, guess merged in
        put("bob-3-0.json", "bob", share("bbbbbbbbbbbb", [[3.4, 70.2, -4.1, "CHEST"], [-8, 71, 6, "OTHER"]],
                                          {"ms": 20000, "p": [[0, 70, -15], [3, 70, -4]]}))
        put("carl-4-0.json", "carl", share("cccccccccccc", [[-7, 75, 5, "BAT"]]))
        rooms.build()
        pack = json.loads(rooms.PACK.read_text())
        s = pack["rooms"][A]["s"]
        assert len(s) == 2, s
        chest = [x for x in s if x[3] == "CHEST"][0]
        assert chest[4] == 2 and abs(chest[0] - 3.2) < 0.01, chest
        bat = [x for x in s if x[3] == "BAT"][0]
        assert bat[0] == -7 and bat[4] == 2, bat  # the precise find decides the spot
        assert pack["rooms"][A]["r"]["ms"] == 20000
        assert pack["contributors"] == 3

        # files forwarded by a trusted account count by contributor id; a confirmed contributor counts alone
        rooms.TRUSTED.write_text("gh:fate  # forwards Discord files\ncid:ffffffffffff\n")
        put("fate-5-0.json", "fate", share("dddddddddddd", [[10, 80, 10, "ITEM"]]))
        put("fate-6-0.json", "fate", share("eeeeeeeeeeee", [[10.3, 80, 10, "ITEM"]]))
        put("fate-7-0.json", "fate", share("ffffffffffff", [[-12, 69, -12, "ESSENCE"]]))
        rooms.build()
        s = json.loads(rooms.PACK.read_text())["rooms"][A]["s"]
        assert any(x[3] == "ITEM" and x[4] == 2 for x in s), s
        assert any(x[3] == "ESSENCE" and x[4] == 1 for x in s), s

        # bad input is refused or trimmed
        for bad in ({"type": "x"}, [], {"type": "ofa-rooms", "rooms": []}):
            try:
                rooms.parse_store(bad)
                raise AssertionError("accepted a bad file")
            except ValueError:
                pass
        st = rooms.parse_store({"type": "ofa-rooms", "contributor": "../../etc", "rooms": {
            "BAD": {"s": [[1, 70, 1, "CHEST"]]},
            A: {"s": [[999, 70, 1, "CHEST"], [1, 70, 1, "NOPE"], [1, True, 1, "CHEST"], [1, 70, 1, "chest"]],
                "r": {"ms": 5, "p": [[0, 70, 0], [1, 70, 1]]}}}})
        assert st["contributor"] is None
        assert list(st["rooms"]) == [A] and st["rooms"][A]["s"] == [[1, 70, 1, "CHEST"]] and st["rooms"][A]["r"] is None, st
        assert rooms.ATTACHMENT.findall("see https://github.com/user-attachments/files/123/ofa-rooms-1.json ok "
                                        "https://evil.example/x.json") == ["https://github.com/user-attachments/files/123/ofa-rooms-1.json"]
    print("room pack tests OK")


if __name__ == "__main__":
    run()
