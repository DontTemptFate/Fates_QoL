# Shared dungeon room pack

`pack.json` is what Fates OFA downloads (setting "Use the shared room pack"). It is built by
`ci/rooms/rooms.py` from the share files in `contrib/`, which arrive through "[rooms]" issues
(issue form "Room data"). All of it was learned in game by OFA players; nothing comes from other mods.

- A secret is published once at least two different players found it at the same spot.
- Positions are relative to the room cell and its facing; room ids are OFA's own fingerprints.
- To remove bad data: delete the file from `contrib/` and run the "Room data" workflow again
  (or edit any "[rooms]" issue), or rebuild locally with `python3 ci/rooms/rooms.py build`.
