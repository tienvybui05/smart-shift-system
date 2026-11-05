"""Create local monitoring secrets once. Never print or overwrite passwords."""
from pathlib import Path
import secrets

ROOT = Path(__file__).resolve().parents[2]


def main():
    folder = ROOT / '.secrets'
    folder.mkdir(mode=0o700, exist_ok=True)
    for name in ('metrics_password', 'grafana_admin_password'):
        target = folder / name
        if target.exists():
            if len(target.read_text(encoding='utf-8').strip()) < 24:
                raise ValueError(f'{name} must contain at least 24 characters; existing file was not changed')
        else:
            with target.open('x', encoding='utf-8') as handle:
                handle.write(secrets.token_urlsafe(36))
            # Containers run under different UIDs. Parent directory is owner-only on Linux.
            target.chmod(0o444)
        print(f'Ready: .secrets/{name} (value hidden)')


if __name__ == '__main__':
    main()
