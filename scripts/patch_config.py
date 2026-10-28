"""Patch the generated AntWars config.yml for the local test server.

Keeps the original comments: only server-lobby.*, countdown and
auto-queue.enabled are rewritten (the section is appended if missing).
Usage: py scripts/patch_config.py <path/to/config.yml>
"""
import sys
from pathlib import Path

LOBBY = {
    "world": "world",
    "x": "-142.5",
    "y": "50.0",
    "z": "-45.5",
    "yaw": "0.0",
    "pitch": "0.0",
}


def indent_of(line: str) -> str:
    return line[: len(line) - len(line.lstrip())]


def ensure_auto_queue(lines: list[str]) -> None:
    """Force auto-queue.enabled: true, or append the whole section."""
    for i, line in enumerate(lines):
        if line.strip() == "auto-queue:":
            indent = indent_of(line) + "  "
            j = i + 1
            while j < len(lines):
                stripped = lines[j].strip()
                if stripped and not lines[j].startswith((" ", "\t")):
                    break
                if stripped.startswith("enabled:"):
                    lines[j] = f"{indent}enabled: true"
                    return
                j += 1
            lines.insert(i + 1, f"{indent}enabled: true")
            return
    lines.extend([
        "",
        "auto-queue:",
        "  enabled: true",
        "  arenas:",
        "    - 2x2",
        "    - 2x2x2x2",
        "    - 4x4x4x4",
        "    - 4x4",
    ])


def main() -> None:
    path = Path(sys.argv[1])
    lines = path.read_text(encoding="utf-8").splitlines()
    out = []
    in_lobby = False
    for line in lines:
        stripped = line.strip()
        if stripped.startswith("server-lobby:"):
            in_lobby = True
            out.append(line)
            continue
        if in_lobby:
            if stripped and not line.startswith((" ", "\t")):
                in_lobby = False
            else:
                for key, value in LOBBY.items():
                    if stripped.startswith(key + ":"):
                        line = f"{indent_of(line)}{key}: {value}"
                        break
        if stripped.startswith("countdown:"):
            line = f"{indent_of(line)}countdown: 15"
        out.append(line)
    ensure_auto_queue(out)
    path.write_text("\n".join(out) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
