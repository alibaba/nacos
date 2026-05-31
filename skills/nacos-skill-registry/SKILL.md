---
name: nacos-skill-registry
description: Discover, install, update, merge, and publish AI skills with Nacos for personal or team skill registries.
---

# Nacos Skill Registry

This skill helps you discover, install, update, merge, and publish AI skills through Nacos using the `nacos-cli` tool.

## When to Use This Skill

Use this skill when the user:

- Asks "how do I do X" where X might be a task with an existing skill in Nacos
- Says "find a skill for X" or "is there a skill in Nacos for X"
- Asks "what skills are available" or "list skills from Nacos"
- Wants to search for tools, templates, or workflows stored in Nacos
- Needs to download or install a skill from a personal, team, or organization Nacos registry
- Wants to upload or publish a local skill to Nacos for reuse
- Mentions sharing skills across agents, machines, teammates, or execution environments
- Needs to merge local skill edits with a newer remote version and publish the merged result

## What is nacos-cli?

The nacos-cli is a command-line tool for interacting with Nacos. It supports configuration management, skill management, agent spec management, and provides an interactive terminal.

**GitHub**: https://github.com/nacos-group/nacos-cli

**Key skill commands:**

- `nacos-cli skill-list` — Search and list available skills
- `nacos-cli skill-get <name...>` — Download and install one or more skills locally
- `nacos-cli skill-upload <path>` — Upload a skill and create an editing draft
- `nacos-cli skill-review <name> [--version <version>]` — Submit a draft for review
- `nacos-cli skill-release <name> --version <version>` — Release an approved version online
- `nacos-cli skill-describe <name>` — Inspect skill metadata and per-version status

**Other commands:**

- `nacos-cli profile edit|show [name]` — Manage connection profiles
- `nacos-cli config-list|config-get|config-set` — Manage Nacos configurations
- `nacos-cli agentspec-list|agentspec-get|agentspec-upload|agentspec-review|agentspec-release|agentspec-describe` — Manage agent specs
- `nacos-cli interactive` — Start interactive terminal mode
- `nacos-cli completion [bash|zsh|fish|powershell]` — Generate shell completion scripts

## Version-Aware CLI Contract

Always check the installed `nacos-cli` version and available skill commands before publishing or documenting a workflow:

```bash
nacos-cli -v
nacos-cli --help
```

Use the command contract that matches the installed version:

| Installed CLI | Skill publish workflow | Notes |
| :--- | :--- | :--- |
| `1.0.4` | `skill-upload` -> `skill-review` -> wait for approval -> `skill-release` | Current lifecycle. `skill-publish` is deprecated and only runs upload + review for compatibility. |
| Any other version | Inspect `nacos-cli --help` and command-specific help | Do not hard-code old behavior. Follow the commands actually present in the installed CLI. |

For `1.0.4`, treat these as distinct states:

- `skill-upload`: creates or updates an `editing` draft version
- `skill-review`: submits the draft, moving it toward `reviewing`
- `skill-describe`: use this to confirm the submitted version has passed review
- `skill-release`: publishes a reviewed/approved version online; it requires an explicit `--version`

## How to Help Users Find and Install Skills

### Step 1: Ensure nacos-cli is Available

Check if nacos-cli is installed:

```bash
which nacos-cli
```

If not found, install it using the official installer script:

**Linux / macOS:**

```bash
curl -fsSL https://nacos.io/nacos-installer.sh | sudo bash -s -- --cli
```

**Windows (PowerShell):**

```powershell
iwr -UseBasicParsing https://nacos.io/nacos-installer.ps1 -OutFile $env:TEMP\nacos-installer.ps1; & $env:TEMP\nacos-installer.ps1 -cli; Remove-Item $env:TEMP\nacos-installer.ps1
```

### Step 2: Resolve Configuration (Profile)

nacos-cli uses a **profile** mechanism. Profile configs are stored in `~/.nacos-cli/<profile>.conf`. The default profile is `default` (i.e., `~/.nacos-cli/default.conf`).

Before asking the user to create or edit a profile, check whether the environment already provides a complete
`sts-hiclaw` setup:

```bash
printenv NACOS_AUTH_TYPE HICLAW_CONTROLLER_URL HICLAW_AUTH_TOKEN_FILE
```

If `NACOS_AUTH_TYPE=sts-hiclaw`, `HICLAW_CONTROLLER_URL` is set, and `HICLAW_AUTH_TOKEN_FILE` points to a readable local
token file, use `sts-hiclaw` mode directly. Do not require a local `~/.nacos-cli/<profile>.conf` file in this case.

You can verify connectivity directly:

```bash
nacos-cli skill-list
```

Treat `NACOS_AUTH_TYPE` as the source of truth for environment-based auth selection. If it is not set to `sts-hiclaw`,
do not assume `sts-hiclaw` mode only because `HICLAW_CONTROLLER_URL` and `HICLAW_AUTH_TOKEN_FILE` exist. Fall back to
profile initialization when the explicit environment-based setup is missing or fails.

First, check if a default profile already exists:

```bash
nacos-cli profile show
```

**If a profile exists** (the command prints config content), proceed to Step 3. The default profile requires no extra flags:

```bash
nacos-cli skill-list
```

To use a named profile:

```bash
nacos-cli --profile dev skill-list
```

**If no profile exists** (the command reports an error or shows empty/default values), guide the user through first-time initialization:

#### First-Time Profile Initialization

Ask the user the following information:

1. **Nacos server address**: the host and port of their Nacos server (e.g., `127.0.0.1:8848`, or a remote address like `nacos.example.com:8848`)
2. **Auth type**: `none`, `nacos`, `aliyun`, or `sts-hiclaw`
3. **Credentials**:
   - For `none` auth: no credentials are required
   - For `nacos` auth: username and password from the user's local Nacos instance
   - For `aliyun` auth: AccessKey and SecretKey
   - For `sts-hiclaw` auth: credentials are fetched dynamically from HiClaw STS; the local environment must provide
     `HICLAW_CONTROLLER_URL` and `HICLAW_AUTH_TOKEN_FILE`
4. **Namespace** (optional): the Nacos namespace ID to use. Leave empty for the default public namespace.

Once the user provides the info, create the local profile file on their machine:

```bash
mkdir -p ~/.nacos-cli
${EDITOR:-vi} ~/.nacos-cli/default.conf
```

Tell the user to fill in only their local connection values: host, port, auth type, credentials when the selected auth type needs them, and namespace.
Do not include concrete usernames, passwords, access keys, or secret keys in shared docs or skill examples.

For `sts-hiclaw`, the profile should set `authType: sts-hiclaw`; do not store STS credentials in the profile. The CLI reads
`HICLAW_CONTROLLER_URL` and `HICLAW_AUTH_TOKEN_FILE`, then requests temporary credentials from
`$HICLAW_CONTROLLER_URL/api/v1/credentials/sts`.

After writing the config, verify it works:

```bash
nacos-cli profile show
```

Then do a quick connectivity test:

```bash
nacos-cli skill-list
```

If the connection succeeds, tell the user:

```text
Profile initialized! nacos-cli is now connected to <host>:<port>.
You can start searching and installing skills.
```

If it fails (auth error, connection refused, etc.), help the user troubleshoot:

- Connection refused → check host/port, is Nacos server running?
- 401/403 → check username/password or AK/SK
- Namespace error → verify the namespace ID is correct

#### Creating Additional Profiles

If the user needs to connect to multiple Nacos servers (e.g., dev and prod), they can create named profiles:

```bash
# Create or edit a named profile locally (e.g., "dev")
mkdir -p ~/.nacos-cli
${EDITOR:-vi} ~/.nacos-cli/dev.conf
```

Do not hard-code real credentials in shared docs, repositories, or skill examples. Fill the local profile with the
actual host, auth type, username, password, and namespace only on the user's machine.

Then use it with `--profile`:

```bash
nacos-cli --profile dev skill-list
```

### Step 3: Understand What They Need

When a user asks for help, identify:

1. The domain (e.g., code review, testing, deployment, documentation)
2. The specific task (e.g., writing tests, reviewing PRs, generating docs)
3. Whether this is a common enough task that a skill likely exists in Nacos

### Step 4: Search for Skills

List all skills:

```bash
nacos-cli skill-list
```

Filter by name (supports wildcard `*`):

```bash
nacos-cli skill-list --name "creator"
```

With pagination:

```bash
nacos-cli skill-list --page 2 --size 10
```

For example:

- User asks "can you help me review code?" → `nacos-cli skill-list --name review`
- User asks "is there a skill for testing?" → `nacos-cli skill-list --name test`
- User asks "what skills do we have?" → `nacos-cli skill-list`

The command returns results in this format:

```text
Skill List (Page: 1/1, Total: N)
═══════════════════════════════════════════════════════════════════════════════
  1. <skill-name> - <description>
     latest=<version>  editing=<version-or->  reviewing=<version-or->  online=<count>  status=enabled
     scope=<scope>  owner=<owner>  updated=<yyyy-mm-dd hh:mm:ss>  downloads=<count>
  ...
```

For scripts or exact verification, use JSON output:

```bash
nacos-cli skill-list --name <skill-name> --output json
nacos-cli skill-describe <skill-name> --output json
```

### Step 5: Present Options to the User

When you find relevant skills, present them clearly:

1. Summarize what skills were found
2. Highlight the most relevant skill(s) based on user's needs
3. Provide the install command

Example response:

```text
I found N skills in Nacos. The most relevant one for your needs is:

**<skill-name>** - <description>

To install it:
nacos-cli skill-get <skill-name>

This will download the skill to ~/.skills/ and make it available immediately.
Would you like me to install it?
```

### Step 6: Install the Skill

Download and install a skill:

```bash
nacos-cli skill-get <skill-name>
```

Download multiple skills at once:

```bash
nacos-cli skill-get skill-a skill-b skill-c
```

Download a specific version:

```bash
nacos-cli skill-get <skill-name> --version v2
```

Download via route label (e.g., latest, stable):

```bash
nacos-cli skill-get <skill-name> --label stable
```

Download to a custom directory (default is `~/.skills`):

```bash
nacos-cli skill-get <skill-name> -o ~/my-skills
```

After installation, confirm the skill is available:

```bash
ls ~/.skills/<skill-name>/SKILL.md
```

## Handling Version Conflicts When Updating Skills

When a user wants to update a locally installed skill from Nacos, there may be a conflict: the user has made local edits to the skill, and Nacos also has a newer version. This section describes how to detect and resolve such conflicts.

### Understanding Skill Metadata

When `nacos-cli skill-get` installs a skill, it creates a `_meta.json` file in the skill directory with version and origin info:

```json
{
  "ownerId": "abc123",
  "slug": "skill-name",
  "version": "1.0.1",
  "publishedAt": 1771756867954
}
```

Key fields:

- `version` — The version that was installed from Nacos
- `publishedAt` — Timestamp (epoch ms) of when this version was published on Nacos
- `slug` — The skill's unique name in the registry

If the active local skill directory does not have `_meta.json` (for example, a skill under `$CODEX_HOME/skills`), use the
`version` field in `SKILL.md` frontmatter as the local version. If both exist, prefer `_meta.json` for install-origin
metadata and use `SKILL.md` frontmatter as a consistency check.

### Step 1: Detect Local Changes

Before updating a skill, check if the user has made local modifications since it was installed.

Read the metadata:

```bash
cat ~/.skills/<skill-name>/_meta.json
```

Check if any files (excluding `_meta.json`) have been modified after the install. Compare file modification times against the `publishedAt` timestamp:

```bash
# Convert publishedAt (epoch ms) to a reference timestamp for comparison
PUBLISHED_AT=$(python3 -c "import json; print(json.load(open('$HOME/.skills/<skill-name>/_meta.json'))['publishedAt'] / 1000)")

# Find files modified after publishedAt
find ~/.skills/<skill-name> -name '_meta.json' -prune -o -newer <reference> -print
```

Or more simply, compare the SKILL.md modification time with the `publishedAt` timestamp. If the file was modified after install, there are local changes.

### Step 2: Check Remote Version

Query the remote to see if a newer version is available:

```bash
nacos-cli skill-list --name <skill-name>
nacos-cli skill-describe <skill-name>
```

On `nacos-cli` 1.0.4, prefer `skill-describe` for version comparison because it shows each version and its lifecycle
status. Compare the remote online/latest version with the local `_meta.json` version. If the remote version is newer, an update is available.
If `_meta.json` is absent, compare the remote online/latest version with the `version` field in the local `SKILL.md`
frontmatter.

On older CLIs that do not have `skill-describe`, use `skill-list --name <skill-name>` and the fields available in that version.

### Step 3: Determine the Scenario

Based on the results of Step 1 and Step 2, there are four possible scenarios:

| Local Changes? | Remote Newer? | Action |
| :--- | :--- | :--- |
| No | No | Already up to date. No action needed. |
| No | Yes | Safe update — pull remote directly. |
| Yes | No | Local is ahead — suggest publishing local changes. |
| **Yes** | **Yes** | **Conflict — needs resolution (see below).** |

Version gate for publishing local edits:

- If the local version and remote online/latest version are the same, the remote has not advanced beyond the local base.
  When local files contain intended edits, publish the local skill directly according to the installed CLI contract.
- If the remote online/latest version is greater than the local version, do not upload the local directory directly.
  Download the newer remote version, diff it against the local directory, merge remote changes with local edits, then
  publish the merged result as a new version.
- Example: local `0.0.6` and remote latest `0.0.6` means local edits can be uploaded. Local `0.0.6` and remote latest
  `0.0.7` means merge remote `0.0.7` first.

### Step 4: Resolve Conflicts

When both local changes and a newer remote version exist, present the user with two options:

**Option A: Discard local changes, use remote version**

This is the simpler path. Back up the local version first, then overwrite with the remote:

```bash
# 1. Back up the current local skill
cp -r ~/.skills/<skill-name> /tmp/<skill-name>-local-backup

# 2. Pull the latest remote version (overwrites local)
nacos-cli skill-get <skill-name>
```

Tell the user:

```text
Local changes have been backed up to /tmp/<skill-name>-local-backup.
The skill has been updated to the latest remote version (<remote-version>).
If you need to recover your local changes, the backup is available.
```

**Option B: Merge local and remote changes, then submit a new version**

This preserves both local and remote changes. The workflow:

```bash
# 1. Back up local version
cp -r ~/.skills/<skill-name> /tmp/<skill-name>-local-backup

# 2. Download the remote version to a temporary directory
nacos-cli skill-get <skill-name> -o /tmp/<skill-name>-remote

# 3. Diff local vs remote to understand the differences
diff /tmp/<skill-name>-local-backup/SKILL.md /tmp/<skill-name>-remote/<skill-name>/SKILL.md
```

After reviewing the diff:

1. Help the user merge the changes in `~/.skills/<skill-name>/` — incorporate remote improvements while preserving local customizations.
2. Keep the skill itself stateless. Runtime data such as processed issues, PR tracking, task state, or local work logs must stay in a user-level workspace outside agent-specific skill directories.
3. Validate the merged skill before publishing.
4. Publish or submit the merged result using the installed CLI contract.

For `nacos-cli` 1.0.4:

```bash
nacos-cli skill-upload ~/.skills/<skill-name>
nacos-cli skill-review <skill-name>
nacos-cli skill-describe <skill-name>
# Wait until the target version is reviewed/approved.
nacos-cli skill-release <skill-name> --version <new-version>
nacos-cli skill-describe <skill-name>
```

For any other version, inspect command help first and follow the installed behavior:

```bash
nacos-cli --help
nacos-cli skill-upload --help
nacos-cli skill-review --help
nacos-cli skill-release --help
nacos-cli skill-publish --help
```

Tell the user:

```text
I've merged the local changes with the remote version.
- Local version: <local-version>
- Remote version: <remote-version>
- Merged version: <new-version>

The merged skill has been processed according to the installed nacos-cli contract.
- Target version: <new-version>
- Final status: <online|reviewing|editing>
- Latest label: <latest-version>
- Verification command: <skill-describe or skill-list command used>
```

### Step 5: Present the Choice to the User

When a conflict is detected, always explain the situation clearly before acting:

```text
Conflict detected for skill "<skill-name>":
- Local version: <local-version> (with local modifications)
- Remote version: <remote-version>

How would you like to proceed?
A) Discard local changes and update to the remote version (your changes will be backed up)
B) Merge local and remote changes, then publish as a new version
```

**Never overwrite local changes without asking.** Always back up before any destructive operation.

## How to Help Users Publish Skills to Nacos

When a user wants to reuse or share a skill by publishing it to Nacos, follow these steps.

### Step 1: Ensure nacos-cli is Available and Configured

Same as the discovery flow above — check `which nacos-cli`, then use either a configured profile or the environment-based
`sts-hiclaw` setup. If `NACOS_AUTH_TYPE=sts-hiclaw`, `HICLAW_CONTROLLER_URL`, and `HICLAW_AUTH_TOKEN_FILE` are already set,
do not require `nacos-cli profile show` to succeed before publishing.

### Step 2: Verify the Skill Directory

A valid skill directory must contain a `SKILL.md` file with proper frontmatter (name, description). Confirm the path:

```bash
ls <path-to-skill>/SKILL.md
```

If the file doesn't exist or lacks frontmatter, help the user create or fix it before publishing.

### Step 3: Publish the Skill

First confirm the installed CLI contract:

```bash
nacos-cli -v
nacos-cli --help
```

Before uploading a locally edited skill, compare the local version with the remote online/latest version using
`nacos-cli skill-describe <skill-name> --output json`. If they match, proceed with upload/review/release. If the remote
version is newer, stop the direct publish path and use the conflict merge workflow above before publishing.

For `nacos-cli` 1.0.4, use the explicit lifecycle:

```bash
nacos-cli skill-upload ./my-skill
nacos-cli skill-review my-skill
nacos-cli skill-describe my-skill
# Wait until the target version is reviewed/approved.
nacos-cli skill-release my-skill --version <version>
nacos-cli skill-describe my-skill
```

Upload all skills in a directory:

```bash
nacos-cli skill-upload --all ./skills-folder
```

For `1.0.4`, `skill-publish` is deprecated. It only runs `skill-upload` + `skill-review` for compatibility and does not complete `skill-release`.

For any other version, inspect command help before acting. Do not assume whether `skill-publish` uploads only or upload+submits.

### Step 4: Verify the Publish

After upload/review/release, verify the exact version, not only that the skill name appears in `skill-list`.

```bash
nacos-cli skill-list --name <skill-name>
nacos-cli skill-describe <skill-name>
nacos-cli skill-describe <skill-name> --output json
```

For `nacos-cli` 1.0.4, the minimal acceptance check is:

- before release: target version is reviewed/approved in `skill-describe`; do not run `skill-release` while it is only `editing` or still waiting for review
- after release: target version is `online`
- if `--update-latest=false` was not used: `labels.latest` equals the target version
- after downloading a specific version: local `_meta.json` version equals the requested version

Example response to user:

```text
Your skill "<skill-name>" has been processed with the installed nacos-cli workflow.
- Target version: <version>
- Final status: <online|reviewing|editing>
- Latest label: <latest-version>
Other agents, machines, or teammates can install it with:
  nacos-cli skill-get <skill-name>
```

## Connection Reference

**Global flags** (can override profile settings on any command):

| Flag | Short | Description |
| :--- | :--- | :--- |
| --host | | Nacos server host (e.g., 127.0.0.1) |
| --port | | Nacos server port (e.g., 8848) |
| --username | -u | Username for nacos auth |
| --password | -p | Password for nacos auth |
| --namespace | -n | Namespace ID |
| --profile | | Profile name, loads `~/.nacos-cli/<profile>.conf` |
| --config | -c | Path to a config file (overrides profile) |
| --auth-type | | Auth type: `none`, `nacos`, `aliyun`, or `sts-hiclaw` |
| --access-key | | AccessKey for aliyun auth |
| --secret-key | | SecretKey for aliyun auth |
| --security-token | | STS SecurityToken for legacy/explicit sts-hiclaw auth |

For `sts-hiclaw`, prefer environment-based temporary credentials instead of storing credential values in a profile:

```bash
export NACOS_AUTH_TYPE=sts-hiclaw
export HICLAW_CONTROLLER_URL=<your-hiclaw-controller-url>
export HICLAW_AUTH_TOKEN_FILE=<path-to-local-token-file>
nacos-cli skill-list
```

Treat `NACOS_AUTH_TYPE` as the source of truth. Do not assume `sts-hiclaw` mode unless `NACOS_AUTH_TYPE=sts-hiclaw` is
set explicitly.

## When No Skills Are Found

If no relevant skills exist in Nacos:

1. Acknowledge that no existing skill was found
2. Offer to help with the task directly using general capabilities
3. Suggest creating and publishing a new skill

Example:

```text
I searched for skills related to "xyz" in Nacos but didn't find any matches.
I can still help you with this task directly! Would you like me to proceed?

If this is something your team does often, you could create a skill and
publish it to Nacos for everyone:
  nacos-cli skill-upload /path/to/your-skill
  nacos-cli skill-review <skill-name>
  nacos-cli skill-release <skill-name> --version <version>
```

## Tips for Effective Use

1. **Use specific keywords**: "react testing" is better than just "testing" when filtering
2. **Try alternative terms**: If "deploy" doesn't work, try "deployment" or "ci-cd"
3. **Check namespaces**: Different teams may store skills in different Nacos namespaces — use `-n <namespace>` to switch
4. **Use profiles**: Save connection details in profiles (`nacos-cli profile edit`) to avoid typing credentials repeatedly
5. **Version and label**: Use `--version` or `--label` with `skill-get` to pin specific skill versions
6. **Shell completion**: Run `nacos-cli completion zsh` (or bash/fish) for tab completion support
