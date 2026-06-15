#!/usr/bin/env python3
"""
DMARC property probe — derive the values for payments.interac.imap.dmarc.* by
reading real mail your receiving server has already stamped.

Why this exists
---------------
When DMARC enforcement is enabled (payments.interac.imap.dmarc.enabled=true), the
app does NOT re-validate SPF/DKIM. It trusts the Authentication-Results header your
own receiving mail server added, and to find that header it needs to know the
server's authserv-id — the first token of the header it writes (e.g.
"mail.lensbridge.tech"). You can't guess that reliably; it's whatever the mailbox
provider stamps. This script connects to the same IMAP mailbox the listener uses,
finds genuine Interac notification mail, parses the Authentication-Results headers
exactly the way the Java AuthenticationResultsParser does, and prints the property
values to paste.

It is read-only: it never moves, deletes, or marks mail.

The parsing here is deliberately a 1:1 mirror of
  src/main/java/.../payment/etransfer/AuthenticationResultsParser.java
so the authserv-id / verdict / header.from it reports are the ones the running app
will actually key off. If you change one, change the other.

Usage
-----
    # read host/port/user/pass/folder/senders straight from a properties file:
    python scripts/dmarc_probe.py --properties src/main/resources/application.properties

    # or pass them explicitly (password prompted if omitted):
    python scripts/dmarc_probe.py --host imap.example.com --username payments@example.com \
        --sender notify@payments.interac.ca

    # scan more history if recent mail is sparse:
    python scripts/dmarc_probe.py --properties application.properties --limit 50

Stdlib only — no pip install needed.
"""
from __future__ import annotations

import argparse
import getpass
import imaplib
import re
import sys
from collections import Counter
from email import message_from_bytes
from pathlib import Path

# --- regexes mirrored from AuthenticationResultsParser.java -------------------
# "dmarc=pass" / "dmarc=fail" / "dmarc=none" — the method result token.
DMARC_RE = re.compile(r"\bdmarc\s*=\s*(\w+)", re.IGNORECASE)
# "header.from=interac.ca" — the authenticated From domain (optionally quoted).
HEADER_FROM_RE = re.compile(r'header\.from\s*=\s*"?([A-Za-z0-9.\-]+)', re.IGNORECASE)


def authserv_id(header: str) -> str | None:
    """The authserv-id is the first field, before any optional version number or ';'.

    Mirror of AuthenticationResultsParser.authservId(String).
    """
    semicolon = header.find(";")
    first_field = (header[:semicolon] if semicolon >= 0 else header).strip()
    if not first_field:
        return None
    space = first_field.find(" ")  # strip a trailing "1" version token, if any
    return first_field[:space] if space >= 0 else first_field


def unfold(raw: str) -> str:
    """Collapse RFC 5322 header folding (CRLF + whitespace) into a single line."""
    return re.sub(r"\s+", " ", raw).strip()


class Verdict:
    """One Authentication-Results header's parsed DMARC outcome."""

    def __init__(self, authserv: str | None, dmarc: str | None, header_from: str | None, raw: str):
        self.authserv = authserv
        self.dmarc = dmarc.lower() if dmarc else None
        self.header_from = header_from.lower() if header_from else None
        self.raw = raw

    def has_dmarc(self) -> bool:
        return self.dmarc is not None

    def is_pass(self) -> bool:
        return self.dmarc == "pass"


def parse_header(raw: str) -> Verdict:
    h = unfold(raw)
    dmarc = DMARC_RE.search(h)
    hfrom = HEADER_FROM_RE.search(h)
    return Verdict(
        authserv=authserv_id(h),
        dmarc=dmarc.group(1) if dmarc else None,
        header_from=hfrom.group(1) if hfrom else None,
        raw=h,
    )


# --- properties file reader (just enough for our keys) ------------------------
def read_properties(path: Path) -> dict[str, str]:
    props: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        s = line.strip()
        if not s or s.startswith("#") or s.startswith("!"):
            continue
        for sep in ("=", ":"):
            if sep in s:
                k, v = s.split(sep, 1)
                props[k.strip()] = v.strip()
                break
    return props


# --- IMAP probe ---------------------------------------------------------------
def fetch_candidates(args, senders: list[str]) -> list[bytes]:
    """Return raw RFC822 bytes of candidate messages (sender mail first, else recent)."""
    cls = imaplib.IMAP4 if args.no_ssl else imaplib.IMAP4_SSL
    print(f"Connecting to {args.host}:{args.port} ({'plain' if args.no_ssl else 'SSL'}) ...")
    conn = cls(args.host, args.port)
    try:
        conn.login(args.username, args.password)
        # readonly: never touch \Seen or move anything.
        conn.select(args.folder, readonly=True)

        uids: list[bytes] = []
        for sender in senders:
            typ, data = conn.search(None, "FROM", f'"{sender}"')
            if typ == "OK" and data and data[0]:
                found = data[0].split()
                print(f"  {len(found)} message(s) from {sender}")
                uids.extend(found)
        if not uids:
            print("  no mail matched the expected sender(s); scanning most recent instead.")
            typ, data = conn.search(None, "ALL")
            if typ == "OK" and data and data[0]:
                uids = data[0].split()

        uids = uids[-args.limit:]  # newest tail
        raws: list[bytes] = []
        for uid in reversed(uids):  # newest first
            typ, msg_data = conn.fetch(uid, "(RFC822)")
            if typ == "OK" and msg_data and msg_data[0]:
                raws.append(msg_data[0][1])
        return raws
    finally:
        try:
            conn.logout()
        except Exception:
            pass


def main() -> int:
    ap = argparse.ArgumentParser(
        description="Derive payments.interac.imap.dmarc.* from real mailbox messages.")
    ap.add_argument("--properties", type=Path,
                    help="application.properties to read IMAP settings from")
    ap.add_argument("--host")
    ap.add_argument("--port", type=int)
    ap.add_argument("--username")
    ap.add_argument("--password")
    ap.add_argument("--folder")
    ap.add_argument("--sender", action="append", dest="senders",
                    help="expected From address (repeatable); overrides properties")
    ap.add_argument("--limit", type=int, default=20,
                    help="max messages to inspect (default 20)")
    ap.add_argument("--no-ssl", action="store_true", help="plain IMAP (not 993/SSL)")
    args = ap.parse_args()

    props = read_properties(args.properties) if args.properties else {}

    def pick(cli, key, default=None):
        return cli if cli is not None else props.get(key, default)

    args.host = pick(args.host, "payments.interac.imap.host")
    args.port = int(pick(args.port, "payments.interac.imap.port", 993))
    args.username = pick(args.username, "payments.interac.imap.username")
    args.password = args.password or props.get("payments.interac.imap.password")
    args.folder = pick(args.folder, "payments.interac.imap.folder", "INBOX")

    senders = args.senders
    if not senders:
        raw = props.get("payments.interac.imap.expected-senders",
                        "notify@payments.interac.ca")
        senders = [s.strip() for s in raw.split(",") if s.strip()]

    if not args.host or not args.username:
        ap.error("host and username are required (pass --properties or --host/--username).")
    if not args.password or args.password.startswith("CHANGE_ME"):
        args.password = getpass.getpass(f"IMAP password for {args.username}: ")

    raws = fetch_candidates(args, senders)
    if not raws:
        print("\nNo messages to inspect. Send/receive a real Interac notification first.")
        return 1

    # Aggregate every Authentication-Results header across the sampled messages.
    verdicts: list[Verdict] = []
    msgs_with_header = 0
    for rawmsg in raws:
        msg = message_from_bytes(rawmsg)
        ar_headers = msg.get_all("Authentication-Results") or []
        if ar_headers:
            msgs_with_header += 1
        for h in ar_headers:
            verdicts.append(parse_header(h))

    print(f"\nInspected {len(raws)} message(s); "
          f"{msgs_with_header} carried an Authentication-Results header; "
          f"{len(verdicts)} header(s) total.\n")

    if not verdicts:
        print("No Authentication-Results headers found. Your provider may not stamp them,")
        print("or they're under a different header name. DMARC enforcement can't be used")
        print("here without them — leave payments.interac.imap.dmarc.enabled=false.")
        return 1

    # Only headers that actually carry a dmarc method are useful to us.
    with_dmarc = [v for v in verdicts if v.has_dmarc()]
    if not with_dmarc:
        print("Authentication-Results headers exist but none carry a `dmarc=` token.")
        print("Seen authserv-ids:",
              ", ".join(sorted({v.authserv for v in verdicts if v.authserv})) or "(none)")
        print("Your server validates SPF/DKIM but not DMARC. Enforcement would quarantine")
        print("everything (fail-closed). Keep dmarc.enabled=false, or enable DMARC on the server.")
        return 1

    authserv_counts = Counter(v.authserv for v in with_dmarc if v.authserv)
    pass_authserv = Counter(v.authserv for v in with_dmarc if v.is_pass() and v.authserv)
    domains = Counter(v.header_from for v in with_dmarc if v.is_pass() and v.header_from)

    # Recommend the authserv-id that most often produced an aligned dmarc=pass; if
    # nothing passed, still surface the dominant one so the operator can see why.
    if pass_authserv:
        recommended_authserv = pass_authserv.most_common(1)[0][0]
    else:
        recommended_authserv = authserv_counts.most_common(1)[0][0]
    recommended_domain = domains.most_common(1)[0][0] if domains else None

    print("=== DMARC verdicts seen (newest first) ===")
    for v in verdicts:
        tag = "  " if v.has_dmarc() else " ·"  # ·: trusted-looking header w/o dmarc token
        print(f"{tag} authserv-id={v.authserv or '?':30} "
              f"dmarc={v.dmarc or '-':6} header.from={v.header_from or '-'}")

    print("\n=== Summary ===")
    print(f"authserv-id(s) carrying DMARC : {dict(authserv_counts)}")
    print(f"  of those, dmarc=pass        : {dict(pass_authserv) or '{} (NONE passed!)'}")
    print(f"aligned header.from on pass   : {dict(domains) or '{}'}")

    if len(authserv_counts) > 1:
        print("\n⚠  More than one authserv-id stamped DMARC. Pick the one belonging to YOUR")
        print("   receiving server (the last hop before the mailbox). A spoofer can forge the")
        print("   others; only your own server's header is trustworthy.")
    if not pass_authserv:
        print("\n⚠  No message produced dmarc=pass. Enabling enforcement now would quarantine")
        print("   real payments. Investigate why DMARC isn't passing before turning it on.")

    print("\n=== Paste into application.properties ===")
    print("payments.interac.imap.dmarc.enabled=true")
    print(f"payments.interac.imap.dmarc.authserv-id={recommended_authserv or ''}")
    if recommended_domain and not any(
        s.lower().endswith("@" + recommended_domain) for s in senders
    ):
        # header.from differs from the trusted sender's domain — be explicit.
        print(f"payments.interac.imap.dmarc.aligned-domain={recommended_domain}")
    else:
        print("# aligned-domain left blank: header.from matches the trusted sender's domain")
        print("# (the app defaults aligned-domain to the From domain when blank).")
        print("payments.interac.imap.dmarc.aligned-domain=")

    return 0


if __name__ == "__main__":
    sys.exit(main())
