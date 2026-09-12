from pathlib import Path
import yaml
from merge_config import merge, domains, UniqueLoader

root = Path(__file__).resolve().parents[1]
work = root / ".tooling/clash-test"
work.mkdir(exist_ok=True, parents=True)
base = {
    "mode": "global", "mixed-port": 0,
    "proxy-groups": [{"name": "Existing", "type": "select", "proxies": ["DIRECT"]}],
    "dns": {"enable": False},
    "rules": ["DOMAIN,example.org,DIRECT", "MATCH,DIRECT"],
}
online = merge(base)
assert base["mode"] == "global"
assert online["rules"][-2:] == base["rules"]
assert online["proxy-groups"] == base["proxy-groups"]
assert online["dns"] == base["dns"]
assert merge(online) == online
try:
    yaml.load("rules: []\nrules: []", Loader=UniqueLoader)
    raise AssertionError("duplicate accepted")
except ValueError:
    pass
offline = merge(base, offline=True)
assert merge(offline, offline=True) == offline
assert "DOMAIN-SUFFIX,df.tanx.com,REJECT" in offline["rules"]
assert "DOMAIN-SUFFIX,afd.baidu.com,REJECT" in offline["rules"]
assert "DOMAIN-SUFFIX,api2.music.163.com,REJECT" not in offline["rules"]
(work / "offline-test.yaml").write_text(yaml.safe_dump(offline, sort_keys=False), encoding="utf-8")
(work / "providers/quietstart-antiad.yaml").write_text(yaml.safe_dump({"payload": ["+." + d for d in domains()]}), encoding="utf-8")
provider_test = merge(base)
provider_test["rule-providers"]["quietstart-antiad"] = {
    "type": "file", "behavior": "domain", "format": "yaml", "path": "./providers/quietstart-antiad.yaml"
}
(work / "provider-test.yaml").write_text(yaml.safe_dump(provider_test, sort_keys=False), encoding="utf-8")
fragment = {"mode": "rule", "rule-providers": online["rule-providers"], "rules": online["rules"][:-2]}
(root / "clash-integration/合并片段-不能单独导入.yaml").write_text(
    "# MERGE FRAGMENT ONLY. Preserve original proxies, groups, DNS and append original rules.\n" + yaml.safe_dump(fragment, allow_unicode=True, sort_keys=False), encoding="utf-8"
)
print("PASS: preservation, precedence, idempotence, duplicate rejection, observed domains; entries:", len(domains()))
