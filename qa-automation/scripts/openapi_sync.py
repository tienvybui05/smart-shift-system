"""Read-only API access. Validate and export/check the committed OpenAPI snapshot."""
import argparse
from pathlib import Path
import urllib.request
import yaml
from openapi_spec_validator import validate_spec

ROOT = Path(__file__).resolve().parents[2]


def validate(document):
    def local_refs(value):
        if isinstance(value, dict):
            if '$ref' in value and not value['$ref'].startswith('#/'):
                raise ValueError('Only local $ref is allowed; no remote validation requests')
            for item in value.values(): local_refs(item)
        elif isinstance(value, list):
            for item in value: local_refs(item)
    local_refs(document)
    validate_spec(document)


def business_contract(document):
    # Monitoring auth depends on whether the optional monitoring overlay is enabled.
    return dict(paths={path: value for path, value in document['paths'].items() if path.startswith('/api/')},
                schemas=document['components']['schemas'], responses=document['components'].get('responses',{}))


def main(base, write):
    with urllib.request.urlopen(base.rstrip('/') + '/v3/api-docs.yaml', timeout=60) as response:
        text = response.read().decode('utf-8')
    live = yaml.safe_load(text)
    validate(live)
    target = ROOT / 'docs/openapi.yaml'
    if write:
        target.write_text('# Generated from the running backend; update with openapi_sync.py --write.\n' + text, encoding='utf-8')
        print('Updated docs/openapi.yaml')
    else:
        snapshot = yaml.safe_load(target.read_text(encoding='utf-8'))
        validate(snapshot)
        assert business_contract(live) == business_contract(snapshot), 'OpenAPI snapshot is stale. Run openapi_sync.py --write against the updated QA backend.'
        print('OpenAPI snapshot matches all live business routes and schemas')
    count = sum(1 for path in live['paths'].values() for method in path if method in {'get','post','put','patch','delete'})
    print('Validated', count, 'operations and', len(live['components']['schemas']), 'schemas')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--base-url', default='http://127.0.0.1:28080')
    parser.add_argument('--write', action='store_true')
    args = parser.parse_args()
    main(args.base_url, args.write)
