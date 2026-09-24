"""Validate the title migration in a disposable, network-isolated LOCAL MySQL.

No production credentials, host ports, copied production data or remote Docker
contexts are used. Only the container created by this invocation is removed.
"""
import json
from pathlib import Path
import subprocess
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]


def command(args, **kwargs):
    return subprocess.run(args, text=True, encoding="utf-8", capture_output=True,
                          timeout=kwargs.pop("timeout", 30), **kwargs)


def main():
    context = command(["docker", "context", "show"]).stdout.strip()
    info = command(["docker", "context", "inspect", context])
    if info.returncode:
        raise RuntimeError("Cannot inspect Docker context")
    host = json.loads(info.stdout)[0]["Endpoints"]["docker"]["Host"]
    if not host.startswith(("npipe://", "unix://")):
        raise RuntimeError("Refusing a remote Docker context")
    docker = ["docker", "--context", context]
    name = "codex-hoj-title-test-" + uuid.uuid4().hex[:12]
    created_id = None
    try:
        created = command(docker + ["run", "--detach", "--pull", "never", "--name", name,
                          "--network", "none", "--memory", "512m",
                          "--env", "MYSQL_ALLOW_EMPTY_PASSWORD=yes", "mysql:8.0"], timeout=60)
        if created.returncode:
            raise RuntimeError(created.stderr)
        created_id = created.stdout.strip()

        def sql(text):
            return command(docker + ["exec", "-i", created_id, "mysql", "-uroot",
                           "--protocol=tcp", "--host=127.0.0.1", "--default-character-set=utf8mb4", "-N", "-B"], input=text)

        for _ in range(60):
            if sql("SELECT 1;").returncode == 0:
                break
            time.sleep(1)
        else:
            raise RuntimeError("Isolated MySQL did not become ready")
        fixture = """
CREATE DATABASE hoj;
CREATE TABLE hoj.user_info (uuid varchar(32) PRIMARY KEY,
  title_name varchar(255) NULL DEFAULT NULL COMMENT '头衔、称号') DEFAULT CHARSET=utf8;
INSERT INTO hoj.user_info VALUES ('a','中文称号'), ('b',NULL);
"""
        result = sql(fixture)
        if result.returncode:
            raise RuntimeError(result.stderr)
        rejected = sql("UPDATE hoj.user_info SET title_name='👑' WHERE uuid='a';")
        if rejected.returncode == 0 or "1366" not in rejected.stderr:
            raise AssertionError("The pre-migration fixture must reject four-byte characters")
        migration = (ROOT / "sqlAndsetting/hoj-user-title-utf8mb4-update.sql").read_text(encoding="utf-8")
        for _ in range(2):
            result = sql(migration)
            if result.returncode:
                raise RuntimeError(result.stderr)
        check = sql("""
SELECT CHARACTER_SET_NAME,COLLATION_NAME,IS_NULLABLE,CHARACTER_MAXIMUM_LENGTH
FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='hoj' AND TABLE_NAME='user_info' AND COLUMN_NAME='title_name';
SELECT COUNT(*) FROM hoj.user_info WHERE (uuid='a' AND title_name='中文称号') OR (uuid='b' AND title_name IS NULL);
UPDATE hoj.user_info SET title_name=REPEAT('👑',20) WHERE uuid='a';
SELECT CHAR_LENGTH(title_name),LENGTH(title_name) FROM hoj.user_info WHERE uuid='a';
""")
        if check.returncode:
            raise RuntimeError(check.stderr)
        expected = ["utf8mb4\tutf8mb4_unicode_ci\tYES\t255", "2", "20\t80"]
        if check.stdout.strip().splitlines() != expected:
            raise AssertionError("Unexpected migration validation: " + check.stdout)
        report = {"passed": True, "scope": "isolated-local-mysql", "migrationRuns": 2,
                  "legacyRejectionReproduced": True, "existingValuesAndNullPreserved": True,
                  "twentyEmojiRoundTrip": True}
        output = ROOT / "output/hoj-title-migration-validation.json"
        output.parent.mkdir(exist_ok=True)
        output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(report))
    finally:
        if created_id:
            # Use the returned immutable ID, never a wildcard or another project's name.
            removed = command(docker + ["rm", "-f", "-v", created_id])
            if removed.returncode:
                raise RuntimeError("Failed to clean up our temporary container: " + created_id)


if __name__ == "__main__":
    main()
