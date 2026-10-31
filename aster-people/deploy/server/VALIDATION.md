# Local preparation checks — 2026-10-31

No server commands were executed. These checks used only disposable local
containers, synthetic data and network-disabled containers:

```text
bash -n backup.sh restore-drill.sh docker-firewall.sh
PASS

git diff --check
PASS

Restic 0.16.4:
created local encrypted fixture repository
created independent encrypted fixture repository
copied snapshot between repositories
restored database.dump and database.sha256 using the runbook's include patterns
database.dump: OK

PostgreSQL 17:
PASS: PostgreSQL 17 custom-format dump restored in network-isolated local fixture;
row count verified (one synthetic row)
```

The commands for dump/restore and encrypted repository copy were exercised
separately. The complete root/systemd/SFTP server workflow, firewall, SSH policy,
real credentials, capacity and future production backup are not tested by these
local fixtures. Run the documented staging restore drill and external port checks
before approving production. No actual deployment or production migration occurred.
