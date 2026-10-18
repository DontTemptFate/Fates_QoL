# Shared dungeon room pack

The pack players download lives on the **`data` branch** (`data/rooms/pack.json` there), written only by the
"Room data" workflow. `main` keeps just this README and `trusted.txt`.

How a contribution gets in:
1. A player opens a "Room data" issue with their `/ofa rooms export` file.
2. Fate adds the **`approved`** label (nothing is read before that).
3. The workflow checks the file and saves it under `contrib/` on the `data` branch (at most 3 files per account
   per day), then rebuilds `pack.json`.

Rules in the pack:
- A secret is published once at least two different players found it at the same spot.
- A room route is published once two different players sent one (the faster wins), or a trusted contributor did.
- `trusted.txt` (here on `main`): `gh:<name>` forwards files by their contributor id, `cid:<id>` counts as confirmed.

To remove bad data: delete the file from `contrib/` on the `data` branch and approve any "[rooms]" issue (or run
`python3 ci/rooms/rooms.py build` with `ROOMS_DATA_DIR` pointing at that folder).
