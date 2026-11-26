#!/bin/sh
# /opt/lumora/backend/lumora-backup.sh: nightly dump of the Lumora database (started by lumora-backup.timer).
# Keeps 14 days of compressed dumps in /opt/lumora/backups (mode 700, owner postgres).
set -eu
DIR=/opt/lumora/backups
STAMP=$(date +%Y-%m-%d_%H-%M)
umask 077
pg_dump --format=custom --compress=9 lumora > "$DIR/lumora-$STAMP.dump.tmp"
mv "$DIR/lumora-$STAMP.dump.tmp" "$DIR/lumora-$STAMP.dump"
find "$DIR" -name 'lumora-*.dump' -mtime +14 -delete
