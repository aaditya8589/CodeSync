# CodeSync

[![CI](https://github.com/aaditya8589/CodeSync/actions/workflows/ci.yml/badge.svg)](https://github.com/aaditya8589/CodeSync/actions/workflows/ci.yml)

A real-time collaborative code editor: several people edit the same files at the same time, and anyone in the room can compile and run the code in a sandbox.

<!-- Demo: record a short GIF of two browser tabs typing at once and clicking Run Code, save it as docs/demo.gif, then replace this comment with ![CodeSync demo](docs/demo.gif) -->

## What works

- **Concurrent editing with Operational Transformation.** Two people typing in the same line converge to the same text; nobody's keystrokes are lost.
- **Live cursors and presence.** Everyone sees who is in the room and where each person's caret and selection are, with a coloured name label, kept exact while both sides type.
- **Version history.** Browse earlier versions of a file (edits grouped by pauses, with who made them), compare any version with the current file side by side, and restore it for everyone in the room.
- **Server-authoritative documents.** Every edit is validated, rebased and saved by the server; refreshing, joining late or restarting the server keeps the code.
- **Sandboxed execution of C++, Python and Java, with input.** Run compiles and runs the room's saved code in a throw-away Docker container with no network and hard limits, and reports judge-style results (compile error, runtime error, time limit, memory limit).
- **Problems with a judge.** Attach a problem to a room and its statement appears next to the code for everyone. Submit judges the open file on hidden tests (Accepted, Wrong answer on test 3, time or memory limit, runtime or compile error), and everyone in the room sees the verdict live.
- **Compete mode.** A timed match (1-3 problems, 5-120 minutes) gets its own match room and a share link. Whoever opens the link asks to join and the host lets them in; the match waits in a lobby until the host starts everyone's clock at once. Every player gets a private file per problem that nobody else can see, run or read the history of until time is up; then all code is revealed. Live ICPC standings: most problems solved, then least time, with 5 minutes added per rejected try. A room's Compete button makes a match room and gives everyone in the room its link.
- **Files.** Anyone in the room can add files (`.cpp`, `.py`, `.java`, `.txt`, ...), which start with a template for their language, and rename or delete them (F2 / Delete work too). Every change appears in everyone's file list immediately; a rename keeps the file's history and does not interrupt anyone typing in it.
- **Sign in with Google** (optional) next to email and password. The backend verifies Google's ID token itself (signature against Google's published keys, issuer, audience, expiry) and then issues its own JWT, so the rest of the app is unchanged.
- **Rooms and access control.** JWT authentication, room membership checked on every REST call, WebSocket subscription and edit.

## Architecture

```mermaid
flowchart LR
    subgraph Browser
        M[Monaco editor] --> OC[OT client<br/>one op in flight]
    end
    OC <-- STOMP over WebSocket --> WS[WebSocket controller]
    M -- REST --> API[REST controllers]
    subgraph Spring Boot backend
        WS --> DS[DocumentService<br/>lock, rebase, apply]
        API --> ES[ExecutionService<br/>max 2 concurrent runs]
    end
    DS --> PG[(PostgreSQL<br/>documents + operation log)]
    ES --> PG
    ES -- docker run --> C[Container gcc / python / temurin<br/>no network, 256 MB, 1 CPU]
```

### Edit flow

1. The client turns each Monaco change into an operation such as `[4821, "x", 5203]` (keep 4821 characters, insert `x`, keep 5203) and sends it with the revision it was based on.
2. The server locks the document row (`SELECT ... FOR UPDATE`), loads the operations the client had not seen yet, and **transforms** the incoming operation past them.
3. It applies the result, saves the content and an operation-log entry in one transaction, and broadcasts the transformed operation.
4. The sender treats the broadcast as an acknowledgement; everyone else transforms it against their own unacknowledged edits and applies it without moving their cursor.

## Design decisions

| Decision | Why |
|---|---|
| **OT instead of a CRDT (e.g. Yjs)** | Run Code needs an authoritative plain-text copy on the server; the server already ordered edits with revisions; offline and peer-to-peer editing are not goals. |
| **One operation in flight per client** | Edits typed while waiting are composed into one buffered operation, so fast typing sends far fewer messages (about 14 messages for 80 keystrokes in testing). |
| **Run the saved copy, not code sent by the browser** | Everyone in the room runs exactly the same code, and nobody can run code they cannot see. |
| **Code and input passed to the container over stdin** | No host folders are mounted into the container, which keeps the sandbox small and avoids path problems. |
| **Deny-by-default WebSocket rules** | Only the exact room topic and the user's private error queue may be subscribed to, and clients may only send to `/app/...`. An attack script showed that wildcard subscriptions and direct broker sends were possible before this. |
| **Cursors as positions at a revision, transformed on arrival** | A cursor is only sent when the sender has no unacknowledged edits, so its offset means "this position in server revision R". The receiver moves it through the operations since R and its own unsent edits, so cursors stay exact under concurrent typing. |
| **Presence kept in memory, cursors never touch the database** | Presence only matters while a connection is open. The subscription check records which rooms a connection may use, so frequent cursor messages are checked against memory; the sender's name and tab ID come from the server, so nobody can move someone else's cursor. |
| **Snapshots plus the operation log for history** | The log stores how many characters an edit deleted, not which, so old versions cannot be rebuilt backwards from the current text. A full snapshot is saved at a file's first edit and every 100 revisions; any version is the nearest earlier snapshot plus at most 100 replayed operations. |
| **Restore is an ordinary edit** | Restoring computes one operation from the current text to the old text and applies it like typing, so every open editor receives it live, concurrent edits are transformed as usual, and a restore can itself be undone from the history. |
| **One container per submission, not per test** | The program is compiled once and run against every test inside the same container, each with its own time limit; starting a container and compiling per test would multiply the cost. Each test's output is read back by its announced length, so a program cannot print something that looks like a result. |
| **The server's clock runs the match** | The server records when a match starts and ends and stamps each submission when the request arrives, so neither a wrong computer clock nor a slow judge changes a result. Browsers only display a countdown, corrected by the difference between the server's time and their own. |
| **Private match files are hidden at every door** | One rule (shared, or yours, or the match is over) is checked wherever a file's content can leave the server: the file list, edits, history, Run and Submit. Someone else's file answers "not found", so even its existence stays hidden. Edits to a private file are sent only on its owner's personal WebSocket channel, never the room's; a browser test checks that the other player's browser receives no frame containing that code. |
| **A match has its own room, entered only through the host** | A contest does not belong in a room full of shared files, so each match gets a room with nothing but the players' own files. Its link is an invitation to ask, not a key: the host lets each player in, and joining a match room by its id the way ordinary rooms work is refused, so a forwarded link cannot let strangers in. The clock waits in a lobby until the host starts it for everyone at once. |
| **ICPC scoring** | Ranking by runtime would be partly luck (milliseconds vary between runs), so the standings use problems solved, then minutes to each accepted solution plus 5 per rejected try. Compile errors cost nothing: the code never ran. |
| **Problems as text files** | Problems live in `backend/src/main/resources/problems`, generated with reference solutions (`tools/problems/generate.py`) and loaded into the database at startup. An independent solution for every problem runs through the real judge in CI, so a wrong expected output would fail the build. |
| **Hidden tests stay hidden** | Only the examples show input, expected and actual output. Hidden tests return their verdict and time, not even their error output, since a program could print its input there. |
| **A rename changes only the name** | The file keeps its id, text and revision, so open editors are updated in place without reloading and edits in flight are unaffected; only a new extension changes the highlighting and the language it runs as. Adding or deleting a file changes the set of files, so those make every tab reload the list. |
| **File changes in a room run one at a time** | Creating, renaming and deleting a file lock the room's row first, so the duplicate-name, 20-file and keep-one-file checks always see the previous change. Deleting also locks the file, so an edit being applied finishes first and a later one gets "not found" and makes its tab reload. |
| **Google's ID token checked by the backend, then our own JWT** | The browser gets a signed token from Google's button and the backend verifies it with Google's public keys (cached as long as Google's headers allow), so no Google secret is stored and no redirect flow is needed. After that the user is an ordinary CodeSync user with the same JWT as a password login. |
| **Synchronous execution with a 2-slot limit** | Simple and measurable; a queue with separate workers is the next step only if real load needs it. |

## Sandbox

Every run gets a fresh container:

```
docker run --rm -i --network none --memory 256m --memory-swap 256m --cpus 1 --pids-limit 64
  --read-only --tmpfs /tmp:rw,exec,size=64m --user 1000:1000 --cap-drop ALL
  --security-opt no-new-privileges <image for the language> ...
```

| Language | Image | Check before running | Time limit |
|---|---|---|---|
| C++ (`.cpp`, `.cc`) | `gcc:14` | `g++ -O2 -std=c++17` | 2 s |
| Python (`.py`) | `python:3.13-slim` | `python3 -m py_compile` (syntax errors are reported like compile errors) | 5 s |
| Java (`.java`) | `eclipse-temurin:21-jdk` | `javac`; the file is named after the program's `public class`, so `Solution` works as well as `Main` | 4 s |

The time limit applies to the program only; the whole run is limited to 20 seconds (after which the container is killed by name), and output to 64 KB per stream. A Java `OutOfMemoryError` or Python `MemoryError` counts as a memory limit. CI runs real attacks in every language on every push: infinite loop, memory bomb, fork bomb, network access, output flood and writing outside `/tmp`.

The class name taken from Java source goes into a shell command, so only a plain identifier is accepted; anything else falls back to `Main`.

## Testing

| Area | Tests |
|---|---|
| OT core (Java) | 29: hand-checked cases, 10,000 random convergence and compose cases, JSON round-trips, UTF-16 checks |
| OT core and client (TypeScript) | 40: the same algorithm, 500 simulated three-client sessions with delays and reordering, Monaco change conversion, and 300 sessions checking that every remote cursor lands on exactly the right character |
| Version history (Java) | 11: grouping edits into versions, and against the real database: every one of 250 revisions rebuilt exactly across snapshots, restore and undoing a restore, history that starts after older edits, invalid revisions, non-members |
| Presence (Java) | 15: the presence registry, and the controller rejecting cursors from connections that did not pass the room check |
| Code runner | 20 with a fake `docker` (every outcome including hung containers and Docker being down, each language's command and image), 3 for language detection, and 25 against real Docker in all three languages |
| Judge | 8 verdict rules, 3 problem-file parsing (including every bundled problem), 3 judge output parsing, 4 against real Docker (an independent solution accepted on every problem in C++, Python and Java; wrong answers and `int` overflow caught), 5 against the real database (problems, attaching to rooms, submitting) |
| Files | 4 for file names and templates, 6 against the real database (creation, case-insensitive duplicates, invalid names, the 20-file limit, non-members) |
| Integration scripts | `frontend/scripts/ot-server-check.mjs` (two clients against the running server) and `frontend/scripts/ws-security-check.mjs` (a non-member trying to read, inject, and join presence) |

The Java and TypeScript OT implementations were cross-checked to produce identical results on 5,000 random cases. Mutation tests confirmed the suites fail when key parts of the algorithm are broken.

## Run it locally

Requirements: Java 21+, Node 22+, PostgreSQL, Docker Desktop.

```powershell
# once: create the database and pull the compiler image
psql -U postgres -c "CREATE DATABASE codesync"
docker pull gcc:14
docker pull python:3.13-slim
docker pull eclipse-temurin:21-jdk

# backend (http://localhost:8080)
cd backend
$env:DB_PASSWORD = "<your postgres password>"
$env:JWT_SECRET = "<a random string of at least 32 characters>"
.\mvnw.cmd spring-boot:run

# frontend (http://localhost:5173)
cd frontend
npm install
npm run dev
```

Tests:

```powershell
cd backend;  .\mvnw.cmd test                      # needs PostgreSQL for the Spring context test
$env:CODESYNC_DOCKER_TESTS = "true"; .\mvnw.cmd test -Dtest=CodeRunnerDockerTest
cd frontend; npm test
```

### Configuration

All secrets and environment-specific values come from environment variables, so nothing secret is in the repository.

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `DB_PASSWORD` | yes | | PostgreSQL password |
| `JWT_SECRET` | yes | | Key that signs login tokens, at least 32 characters. The backend refuses to start without it. |
| `DB_URL` | no | `jdbc:postgresql://localhost:5432/codesync` | Database location |
| `DB_USERNAME` | no | `postgres` | Database user |
| `CORS_ALLOWED_ORIGINS` | no | `http://localhost:5173` | Comma-separated frontend URLs allowed to call the API and open WebSockets |
| `JWT_EXPIRATION_MINUTES` | no | `480` | How long a login lasts (8 hours, longer than any match). After that the app asks you to sign in again and takes you back to where you were. |
| `CODESYNC_IMAGE_CPP`, `CODESYNC_IMAGE_PYTHON`, `CODESYNC_IMAGE_JAVA` | no | `gcc:14`, `python:3.13-slim`, `eclipse-temurin:21-jdk` | Image used to run each language |
| `SHOW_SQL` | no | `false` | Log every SQL query |
| `GOOGLE_CLIENT_ID` | no | (off) | OAuth client id for "Sign in with Google"; without it the button is hidden. See below. |
| `VITE_API_URL` (frontend, at build time) | no | `http://localhost:8080` | Backend URL; the WebSocket URL is derived from it |

### Sign in with Google (optional)

1. In [Google Cloud Console](https://console.cloud.google.com/apis/credentials), create a project, set up the **OAuth consent screen** (External, app name, your email), then **Create credentials → OAuth client ID → Web application**.
2. Under **Authorized JavaScript origins** add `http://localhost:5173` (and later your real domain). No redirect URI is needed: the button uses a popup.
3. Start the backend with `GOOGLE_CLIENT_ID=<the client id>`. The id is not a secret; the frontend reads it from `GET /api/auth/google/config`.

A Google sign-in finds the account by Google's permanent user id, or creates one with a username from the email. It never takes over an existing password account with the same email, because registration does not prove the email belongs to whoever typed it.

Generate a secret in PowerShell with a cryptographic random generator:

```powershell
$bytes = New-Object byte[] 48
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

## Deployment

CodeSync runs on any Linux server with Docker using `deploy/docker-compose.yml`: Caddy (HTTPS and the React app), the backend, and PostgreSQL. A step-by-step guide for a free Oracle Cloud server is in [deploy/README.md](deploy/README.md).

## Project structure

```
backend/src/main/java/com/codesync/backend
  ot/          TextOperation: apply, compose, transform, rebase, JSON
  service/     DocumentService (OT on the server), RoomService
  execution/   CodeRunner (Docker sandbox), ExecutionService
  config/      Security, WebSocket configuration and authorization
frontend/src
  ot/          textOperation, otClient state machine, Monaco adapter
  hooks/       useCodeSync (STOMP connection, resync)
  components/  CodeEditor (one Monaco model per file)
```

## Known limitations

- Undo also undoes other people's edits (collaborative undo is not implemented).
- Edits typed while disconnected are discarded on reconnect; the editor is read-only while disconnected.
- The operation log is never compacted.

## Roadmap

Deployment, an estimate of each solution's time complexity from how its running time grows, more problems, and a shared input box for the room.
