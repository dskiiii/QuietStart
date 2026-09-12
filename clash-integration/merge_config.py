"""Local-only config merge. Never fetches or prints subscription credentials."""
import argparse
import copy
from pathlib import Path
import yaml

PROVIDER = "quietstart-antiad"
URL = "https://raw.githubusercontent.com/privacy-protection-tools/anti-AD/master/anti-ad-clash.yaml"

class UniqueLoader(yaml.SafeLoader):
    pass

def mapping(loader, node, deep=False):
    loader.flatten_mapping(node)
    result = {}
    for k, v in node.value:
        key = loader.construct_object(k, deep=deep)
        if key in result:
            raise ValueError("Duplicate YAML key: merge aborted")
        result[key] = loader.construct_object(v, deep=deep)
    return result

UniqueLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, mapping)

def domains():
    root = Path(__file__).resolve().parents[1]
    values = set()
    for filename in ("anti-ad-full.txt", "default-domains.txt"):
        for row in (root / "app/src/main/assets" / filename).read_text(encoding="utf-8-sig").splitlines():
            row = row.split("#", 1)[0].strip()
            if row:
                values.add(row)
    return sorted(values)

def merge(config, offline=False):
    if not isinstance(config, dict):
        raise ValueError("Expected full Clash YAML configuration")
    result = copy.deepcopy(config)
    old_rules = result.get("rules", [])
    if not isinstance(old_rules, list) or any(not isinstance(r, str) for r in old_rules):
        raise ValueError("Unsupported rules format")
    if offline:
        additions = [f"DOMAIN-SUFFIX,{d},REJECT" for d in domains()]
    else:
        providers = result.setdefault("rule-providers", {})
        entry = {"type": "http", "behavior": "domain", "format": "yaml", "path": "./rule-providers/quietstart-antiad.yaml", "url": URL, "interval": 86400}
        if not isinstance(providers, dict) or (PROVIDER in providers and providers[PROVIDER] != entry):
            raise ValueError("Provider name conflict: merge aborted")
        providers[PROVIDER] = entry
        additions = [f"RULE-SET,{PROVIDER},REJECT"]
        # Keep the previously tested supplemental rules as well.
        root = Path(__file__).resolve().parents[1]
        for row in (root / "app/src/main/assets/default-domains.txt").read_text(encoding="utf-8-sig").splitlines():
            row = row.split("#", 1)[0].strip()
            if row:
                additions.append(f"DOMAIN-SUFFIX,{row},REJECT")
    seen = set(additions)
    result["rules"] = additions + [r for r in old_rules if r not in seen]
    result["mode"] = "rule"
    return result

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--offline", action="store_true", help="Embed full snapshot; no rule download required")
    args = parser.parse_args()
    try:
        if args.input.resolve() == args.output.resolve() or args.output.exists():
            raise ValueError("Use a new output filename; originals are never overwritten")
        config = yaml.load(args.input.read_text(encoding="utf-8-sig"), Loader=UniqueLoader)
        merged = merge(config, args.offline)
        with args.output.open("x", encoding="utf-8") as out:
            yaml.safe_dump(merged, out, allow_unicode=True, sort_keys=False)
        print("Merged locally; original file preserved. Proxy credentials were not printed.")
    except Exception as error:
        # Parser errors may quote source lines containing credentials.
        raise SystemExit("Merge failed (" + type(error).__name__ + "). No configuration content was printed.")
