#!/bin/sh
# Sets up the Lumora backend on Ubuntu (20.04 or 24.04), run as root next to the files of packaging/server and
# lumora-social-server.jar:   sh install.sh
# Safe to run again (updates the jar and the units, keeps the database, its password and the nginx site).
# The database password is generated here and only written to /opt/lumora/config/backend.env (root, mode 600).
# TLS: afterwards once  certbot --nginx -d lumora.jardateien.de --redirect  (see docs/SOCIAL.md).
set -eu
SRC=$(cd "$(dirname "$0")" && pwd)
export DEBIAN_FRONTEND=noninteractive

echo "== packages"
apt-get update -q
apt-get install -y -q openjdk-21-jre-headless postgresql nginx ufw openssl

echo "== user and folders"
id lumora >/dev/null 2>&1 || useradd --system --home-dir /opt/lumora --no-create-home --shell /usr/sbin/nologin lumora
install -d -m 755 -o root -g root /opt/lumora /opt/lumora/backend
install -d -m 700 -o root -g root /opt/lumora/config
install -d -m 750 -o lumora -g lumora /opt/lumora/data /opt/lumora/logs
install -d -m 700 -o postgres -g postgres /opt/lumora/backups

echo "== database"
ENV=/opt/lumora/config/backend.env
if [ ! -f "$ENV" ]; then
    PW=$(openssl rand -hex 24)
    if [ "$(runuser -u postgres -- psql -tAc "SELECT 1 FROM pg_roles WHERE rolname = 'lumora'")" = "1" ]; then
        ROLE="ALTER ROLE lumora LOGIN PASSWORD '$PW';"
    else
        ROLE="CREATE ROLE lumora LOGIN PASSWORD '$PW';"
    fi
    # the password goes through stdin, not the command line
    runuser -u postgres -- psql -q -v ON_ERROR_STOP=1 <<SQL
SET password_encryption = 'scram-sha-256';
$ROLE
SQL
    if [ "$(runuser -u postgres -- psql -tAc "SELECT 1 FROM pg_database WHERE datname = 'lumora'")" != "1" ]; then
        runuser -u postgres -- psql -q -v ON_ERROR_STOP=1 -c "CREATE DATABASE lumora OWNER lumora ENCODING 'UTF8' TEMPLATE template0"
        runuser -u postgres -- psql -q -v ON_ERROR_STOP=1 -c "REVOKE ALL ON DATABASE lumora FROM PUBLIC"
    fi
    (
        umask 077
        printf 'LUMORA_DB_URL=jdbc:postgresql://127.0.0.1:5432/lumora\nLUMORA_DB_USER=lumora\nLUMORA_DB_PASSWORD=%s\n' "$PW" > "$ENV"
    )
    unset PW ROLE
    echo "database user created, password stored in $ENV"
else
    echo "kept: $ENV"
fi
chown root:root "$ENV"
chmod 600 "$ENV"

echo "== backend"
install -m 644 -o root -g root "$SRC/lumora-social-server.jar" /opt/lumora/backend/lumora-social-server.jar
install -m 755 -o root -g root "$SRC/lumora-backup.sh" /opt/lumora/backend/lumora-backup.sh
install -m 644 -o root -g root "$SRC/lumora-backend.service" "$SRC/lumora-backup.service" "$SRC/lumora-backup.timer" /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now lumora-backup.timer
systemctl enable lumora-backend
systemctl restart lumora-backend

echo "== nginx"
if [ ! -f /etc/nginx/sites-available/lumora ]; then
    # certbot adds TLS to this file later: only installed once
    install -m 644 -o root -g root "$SRC/nginx-lumora.conf" /etc/nginx/sites-available/lumora
fi
ln -sf /etc/nginx/sites-available/lumora /etc/nginx/sites-enabled/lumora
# only the link to nginx's example page
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl reload nginx

echo "== firewall (SSH first)"
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable
ufw status verbose

echo "== done"
systemctl --no-pager --lines=0 status lumora-backend || true
