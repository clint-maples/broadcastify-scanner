# Desktop scanner (Windows / local Python)

Stdlib-only Python server plus the local web UI. No pip packages.

## Start

From this folder:

```bat
python server.py
```

If `python` is not found:

```bat
py server.py
```

Or double-click `start.bat`.

Then open **http://127.0.0.1:3847** and click **Play all**.

Stop the server with **Ctrl+C** in the terminal.

## Portable zip folder (Windows)

1. Copy the whole `desktop/` folder (or unzip a zip of this folder) anywhere — Downloads, a USB drive, the desktop.
2. You only need **Python 3.10+** installed (the “py launcher” from python.org is enough).
3. Double-click `start.bat`, or run `python server.py` / `py server.py` from this folder.
4. Browse to http://127.0.0.1:3847

No installer, no Node, no virtualenv. PyInstaller packaging is optional later if you want a single `.exe`.

## Default feeds

| ID | Name |
|----|------|
| 14826 | East Placer / Nevada CAL FIRE NEU (Kings Beach / Truckee) |
| 47365 | CAL FIRE NEU West |
| 47367 | Tahoe National Forest West |

Use **＋** in the header to add another feed by Broadcastify feed ID.

See the repo root README for HLS/JWT details, preroll, and scanner-awareness notes.
