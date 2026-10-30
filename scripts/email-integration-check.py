"""Verify actual SMTP delivery using Mailpit; never contacts a real recipient.

Run after integration-check.py on the isolated CI stack, not the user's live demo.
"""
import importlib.util
import json
from pathlib import Path
import urllib.request

spec = importlib.util.spec_from_file_location("checks", Path(__file__).with_name("integration-check.py"))
checks = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checks)
checks.COMPOSE += ["-f", "compose.email-test.yaml"]
compose, sql, wait_for, Client = checks.compose, checks.sql, checks.wait_for, checks.Client

def inbox():
    return json.load(urllib.request.urlopen("http://127.0.0.1:8025/api/v1/messages", timeout=5))

def email_for(case_id, subject):
    for message in inbox()["messages"]:
        if message["Subject"] == f"TrustFlow - Case #{case_id} {subject}":
            return json.load(urllib.request.urlopen("http://127.0.0.1:8025/api/v1/message/" + message["ID"], timeout=5))
    return None

def main():
    employee, reviewer = Client("employee"), Client("reviewer")
    wait_for("Earlier email jobs completed", lambda: sql("SELECT COUNT(*) FROM email_delivery WHERE status = 'PENDING'") == '0')
    pending = employee.expense("Email review demonstration", 1200)
    case_id = pending['id']
    wait_for("Reviewer email received over SMTP", lambda: email_for(case_id, "requires review"))
    message = email_for(case_id, "requires review")
    assert "PENDING_REVIEW" in message["Text"] and "Model score:" in message["Text"]
    assert message["To"][0]["Address"] == "inbox@trustflow.test"
    reason = "Department budget is insufficient"
    assert reviewer.call(f"/api/cases/{case_id}/review", 'POST', {'version':pending['version'], 'decision':'REJECTED', 'reason':reason})[0] == 200
    wait_for("Employee rejection email includes reviewer reason", lambda: email_for(case_id, "rejected") and reason in email_for(case_id, "rejected")["Text"])
    approved = employee.expense("Business travel expense", 5)
    assert approved['status'] == 'APPROVED'
    wait_for("Automatic approval email received", lambda: email_for(approved['id'], 'approved'))
    other = employee.expense("Human approval email", 1200)
    assert reviewer.call(f"/api/cases/{other['id']}/review", 'POST', {'version':other['version'], 'decision':'APPROVED', 'reason':'Exception reviewed and approved'})[0] == 200
    wait_for("Human approval email received", lambda: email_for(other['id'], 'approved'))
    wait_for("Email queue drained before SMTP outage", lambda: sql("SELECT COUNT(*) FROM email_delivery WHERE status = 'PENDING'") == '0')
    compose('stop', 'mailpit')
    try:
        outage = employee.expense("SMTP outage demonstration", 1200)
        cid = outage['id']
        wait_for("SMTP failure retained with retry tracking", lambda: int(sql(f"SELECT COUNT(*) FROM email_delivery WHERE case_id = {cid} AND status = 'PENDING' AND attempts > 0")) == 1)
        wait_for("In-app notification succeeds despite SMTP outage", lambda: any(n['caseId'] == cid for n in employee.call('/api/notifications')[1]))
    finally:
        compose('start', 'mailpit')
    wait_for("Queued email arrives after SMTP recovery", lambda: email_for(cid, 'requires review'))
    wait_for("Delivery marked SENT after SMTP acceptance", lambda: sql(f"SELECT status FROM email_delivery WHERE case_id = {cid}") == 'SENT')
    assert int(sql(f"SELECT attempts FROM email_delivery WHERE case_id = {cid}")) >= 2
    payload = sql(f"SELECT o.payload FROM outbox_event o JOIN email_delivery e ON e.event_id = o.event_id WHERE e.case_id = {cid}")
    before = len([m for m in inbox()['messages'] if m['Subject'] == f'TrustFlow - Case #{cid} requires review'])
    compose('exec', '-T', 'kafka', '/opt/kafka/bin/kafka-console-producer.sh', '--bootstrap-server', 'localhost:9092', '--topic', 'trustflow-notifications', data=(payload+'\n'+payload+'\n').encode())
    # Wait for the separate email group to consume all records before checking duplicates.
    def caught_up():
        output = compose('exec', '-T', 'kafka', '/opt/kafka/bin/kafka-consumer-groups.sh', '--bootstrap-server', 'localhost:9092', '--describe', '--group', 'trustflow-email').decode()
        rows = [line.split() for line in output.splitlines() if line.startswith('trustflow-email ')]
        return bool(rows) and all(row[5] == '0' for row in rows)
    wait_for("Independent email consumer group caught up", caught_up)
    assert sql(f"SELECT COUNT(*) FROM email_delivery WHERE case_id = {cid}") == '1'
    assert len([m for m in inbox()['messages'] if m['Subject'] == f'TrustFlow - Case #{cid} requires review']) == before
    checks.results.append('Replayed events create no duplicate email delivery')
    Path('test-results/email-integration.json').write_text(json.dumps({'passed': checks.results}, indent=2))
    print(json.dumps({'passed': checks.results}, indent=2))

if __name__ == '__main__': main()
