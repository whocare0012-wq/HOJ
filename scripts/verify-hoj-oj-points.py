"""Test the real MySQL points views in a disposable database on LOCAL Docker only.

Does not read production credentials, modify HOJ data, or start/stop any containers.
"""
import json
import pathlib
import subprocess
import uuid
from decimal import Decimal

ROOT = pathlib.Path(__file__).resolve().parents[1]


def main():
    context = json.loads(subprocess.check_output(['docker', 'context', 'inspect']))[0]
    assert context['Endpoints']['docker']['Host'].startswith('npipe://'), 'Local Windows Docker required'
    container = json.loads(subprocess.check_output(['docker', 'inspect', 'hojlocal-mysql-1']))[0]
    assert container['Config']['Labels']['com.docker.compose.project'] == 'hojlocal'
    database = 'hoj_points_verify_' + uuid.uuid4().hex[:12]

    def sql(query):
        result = subprocess.run(['docker', 'exec', '-i', container['Id'], 'sh', '-c',
            'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8mb4 -N -B'],
            input=query.encode(), capture_output=True, timeout=60)
        if result.returncode:
            raise RuntimeError(result.stderr.decode('utf-8', errors='replace'))
        return result.stdout.decode('utf-8').strip()

    def query(text):
        return sql('USE ' + database + ';\n' + text)

    def points(uid='student', pid=1, gid='IS NULL'):
        return Decimal(query(f"SELECT COALESCE(SUM(points),0) FROM oj_problem_points WHERE uid='{uid}' AND pid={pid} AND gid {gid}"))

    def submit(pid, status, score='NULL', max_score=100, score_type=1, uid='student', cid=0, gid='NULL', estimated=0):
        query(f"INSERT INTO judge(uid,pid,status,score,cid,gid,score_type,score_max,score_snapshot_estimated) VALUES ('{uid}',{pid},{status},{score},{cid},{gid},{score_type},{max_score},{estimated})")

    checks = []
    try:
        sql(f'CREATE DATABASE {database} CHARACTER SET utf8mb4')
        query('''CREATE TABLE problem_difficulty_config(difficulty_value INT PRIMARY KEY,display_text VARCHAR(20),border_color VARCHAR(7),sort_order INT);
CREATE TABLE problem(id BIGINT PRIMARY KEY,difficulty INT,type INT,io_score INT);
CREATE TABLE judge(id BIGINT AUTO_INCREMENT PRIMARY KEY,uid VARCHAR(32),pid BIGINT,gid BIGINT NULL,cid BIGINT,status INT,score INT,gmt_modified DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP);
INSERT INTO problem_difficulty_config VALUES (0,'Easy','#19BE6B',0),(1,'Medium','#2D8CF0',1),(2,'Hard','#ED3F14',2);
INSERT INTO problem VALUES (1,0,0,100),(2,1,1,100),(3,2,1,100),(4,1,1,3);
INSERT INTO judge(uid,pid,cid,status,score) VALUES ('legacy',2,0,8,60);''')
        migration = (ROOT / 'sqlAndsetting/hoj-oj-points-update.sql').read_text(encoding='utf-8')
        query(migration)
        assert points('legacy',2) == 12
        assert query("SELECT estimated FROM oj_problem_points WHERE uid='legacy'") == '1'
        checks.append('legacy partial result is explicitly estimated')
        submit(1,-1,100,score_type=0)
        assert points()==0
        submit(1,0,score_type=0);submit(1,0,score_type=0)
        assert points()==10
        checks.append('ACM WA earns zero; repeated AC earns once')
        submit(2,8,60);submit(2,8,30)
        assert points(pid=2)==12
        checks.append('OI uses best ratio rather than sum or latest result')
        for status in [-10,-5,-4,-2,4,5,6,7,9,10,15]: submit(2,status,100)
        assert points(pid=2)==12
        checks.append('cancelled, pending, system/compile errors and unknown results excluded')
        query('UPDATE problem SET io_score=200 WHERE id=2')
        query(migration)
        assert points(pid=2)==12 and points('legacy',2)==12
        checks.append('raw maximum edits and repeat migration preserve captured denominators')
        query('UPDATE problem SET difficulty=2 WHERE id=2')
        assert points(pid=2)==24
        query('UPDATE problem_difficulty_config SET base_points=30 WHERE difficulty_value=2')
        assert points(pid=2)==18
        checks.append('problem difficulty changes and point decreases revalue historical results')
        submit(2,0,cid=1338)
        assert points(pid=2)==18
        submit(2,0,gid=42)
        assert points(pid=2)==18 and points(pid=2,gid='=42')==30
        checks.append('contest excluded; group and global points isolated')
        submit(3,8,-10,uid='bounds');assert points('bounds',3)==0
        submit(3,8,200,uid='bounds');assert points('bounds',3)==30
        submit(3,8,100,max_score=0,uid='zero');assert points('zero',3)==0
        checks.append('negative scores, overshoot and zero denominators bounded')
        submit(4,8,1,max_score=3)
        assert points(pid=4)==Decimal('6.67')
        checks.append('per-problem rounding to two decimals')
        submit(2,8,60,uid='legacy',estimated=0)
        assert query("SELECT estimated FROM oj_problem_points WHERE uid='legacy'")=='0'
        checks.append('confirmed equal best result supersedes legacy estimate')
        before=points()
        query("UPDATE problem_difficulty_config SET display_text='Renamed',sort_order=10 WHERE difficulty_value=0")
        assert points()==before
        checks.append('names and sort order never affect points')
        query('UPDATE problem_difficulty_config SET base_points=0 WHERE difficulty_value=0')
        assert points()==0
        checks.append('explicit zero-point difficulty supported')
        print(json.dumps({'passed':len(checks),'checks':checks},ensure_ascii=False,indent=2))
    finally:
        assert database.startswith('hoj_points_verify_') and len(database)==30
        sql('DROP DATABASE IF EXISTS '+database)


if __name__ == '__main__': main()
