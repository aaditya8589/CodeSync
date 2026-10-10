# Deploying CodeSync

Everything runs on one Linux server with Docker, because Run Code starts a new container for every
program. Hosting platforms such as Vercel, Render or Railway do not allow that.

```
browser ── HTTPS ──> Caddy (web) ──> /api, /ws ──> backend ──> PostgreSQL (db)
                       │                              │
                       └── React app (static files)   └── host Docker ──> sandbox container per run
```

- **web**: Caddy serves the React app, forwards `/api` and `/ws` to the backend, and gets a free
  HTTPS certificate from Let's Encrypt automatically.
- **backend**: the Spring Boot app. It talks to the server's Docker through `/var/run/docker.sock`
  to start sandbox containers. It is only reachable through Caddy.
- **db**: PostgreSQL 18, data kept in a Docker volume.

## 1. Server (Oracle Cloud Always Free)

1. Create an account at cloud.oracle.com (a card is needed for identity verification; the
   Always Free resources are not charged).
2. **Compute → Instances → Create instance**
   - Image: **Canonical Ubuntu 24.04**
   - Shape: **Ampere → VM.Standard.A1.Flex**, 2 OCPUs and 12 GB memory (free up to 4 / 24)
   - Networking: keep "Assign a public IPv4 address" on
   - SSH keys: **Generate a key pair** and download the private key
   - If it says *Out of capacity*, try another availability domain or try again later.
3. Note the instance's **public IP address**.
4. Open ports 80 and 443 in Oracle's firewall: **Instance → Subnet → Default Security List →
   Add Ingress Rules**: source `0.0.0.0/0`, TCP, destination ports `80,443`.

## 2. Free domain (DuckDNS)

1. Sign in at duckdns.org, create a subdomain (e.g. `codesync-yourname`).
2. Set its **current ip** to the server's public IP and click **update ip**.

## 3. Set up the server

From PowerShell on your computer (use the downloaded key file):

```powershell
ssh -i C:\path\to\ssh-key.key ubuntu@<public-ip>
```

On the server:

```bash
# Ubuntu images on Oracle also block ports in the server's own firewall
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save

# Docker
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
exit    # log out and in again so the group change applies
```

## 4. Start CodeSync

```bash
git clone https://github.com/aaditya8589/CodeSync.git
cd CodeSync/deploy

cp .env.example .env
echo "JWT_SECRET=$(openssl rand -base64 48)"    # copy this line into .env
echo "DB_PASSWORD=$(openssl rand -base64 24)"   # and this one
nano .env                                       # set DOMAIN, DB_PASSWORD, JWT_SECRET

# The images programs run in (pulled once, so the first run is not slow)
docker pull gcc:14
docker pull python:3.13-slim
docker pull eclipse-temurin:21-jdk

docker compose up -d --build     # the first build takes a few minutes
docker compose ps                # db, backend and web should be "running"/"healthy"
```

Open `https://<your-domain>`: register, create a room, and run some code.

## Updating

```bash
cd ~/CodeSync && git pull
cd deploy && docker compose up -d --build
```

## When something is wrong

| Symptom | Check |
|---|---|
| Site does not load at all | Ports 80/443 open in both the Security List and `iptables`; DuckDNS points at the right IP |
| Browser warns about the certificate | `docker compose logs web`; Let's Encrypt needs port 80 reachable from the internet |
| Login or rooms fail | `docker compose logs backend`; `https://<domain>/api/health` should say it is running |
| Run Code says "not available" | `docker compose logs backend`; the three images are pulled (`docker images`) |

Useful commands: `docker compose logs -f backend`, `docker compose restart backend`,
`docker compose down` (stops everything; data stays in the volumes).
