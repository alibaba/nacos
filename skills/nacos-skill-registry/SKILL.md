---
name: nacos-skill-registry
description: Helps users discover and install AI skills from a team's Nacos server when they ask questions like "how do I do X", "I want to X", "help me with X", "find a skill for X", "is there a skill that can...", or express interest in extending capabilities. This skill should be used when the user is looking for functionality that might exist as an installable skill in Nacos. Also supports uploading and releasing skills for team sharing with version-aware command guidance.
---

# Nacos Skill Registry

This skill helps you discover, install, upload, and release AI skills in a Nacos configuration center using the
nacos-cli tool. In current lifecycle versions, uploading is only a draft-creation step, not the recommended final
sharing action.

## When to Use This Skill

Use this skill when the user:

- Asks "how do I do X" where X might be a task with an existing skill in Nacos
- Says "find a skill for X" or "is there a skill in Nacos for X"
- Asks "what skills are available" or "list skills from Nacos"
- Wants to search for tools, templates, or workflows stored in Nacos
- Needs to download or install a skill from a team/organization's Nacos server
- Wants to publish, release, or share a skill through Nacos for their team
- Mentions they want to share or discover skills within their team

## What is nacos-cli?

The nacos-cli is a command-line tool for managing AI skills stored in a Nacos configuration center. Think of Nacos as a private skill registry for teams and organizations.

**GitHub**: https://github.com/nacos-group/nacos-cli

Prefer the latest nacos-cli version whenever possible. The legacy command flow is only a fallback when the user's
environment cannot upgrade.

**Check the installed CLI before choosing commands:**

```bash
nacos-cli --version
nacos-cli skill-release --help
```

If `skill-release` prints command help, use the current lifecycle command set. If it reports an unknown command, the
user is probably on a nacos-cli version before 1.0.4. Recommend upgrading to the latest CLI first; if they cannot
upgrade, use the legacy publish command set below.

**Current lifecycle commands (`nacos-cli` v1.0.4 and newer):**

- `nacos-cli skill-list` - Search and list available skills
- `nacos-cli skill-describe <name>` - Show skill detail and version history
- `nacos-cli skill-get <name...>` - Download one or more skills locally
- `nacos-cli skill-upload <path>` - Create or update a skill draft in Nacos; do not present this as final publishing
- `nacos-cli skill-review <name>` - Submit a draft for review
- `nacos-cli skill-release <name> --version <version>` - Release an approved version online
- `nacos-cli skill-publish <path>` - Deprecated compatibility shortcut for `skill-upload` + `skill-review`

**Legacy commands (before `nacos-cli` 1.0.4):**

- `nacos-cli skill-list` - Search and list available skills
- `nacos-cli skill-get <name>` - Download a skill locally
- `nacos-cli skill-publish <path>` - Recommended publishing command; equivalent to upload + review

Do not assume `skill-review`, `skill-release`, or `skill-describe` exists without checking the installed CLI. Match the
commands to the user's actual version.

## How to Help Users Find and Install Skills

### Step 1: Ensure nacos-cli is Available

Check if nacos-cli is installed:

```bash
which nacos-cli
```

If not found, there are two options:

**Option A: Use via npx or npm**

You can run nacos-cli directly through npx without any installation:

```bash
npx @nacos-group/cli@latest <command>
```

For example: `npx @nacos-group/cli@latest skill-list` or `npx @nacos-group/cli@latest skill-get my-skill`.

If using npx, replace all `nacos-cli` commands in the subsequent steps with `npx @nacos-group/cli@latest`.

To install globally with npm:

```bash
npm install -g @nacos-group/cli@latest
```

**Option B: Install nacos-cli globally**

**Linux / macOS:**

```bash
curl -fsSL https://nacos.io/nacos-installer.sh | sudo bash -s -- --cli
```

**Windows (PowerShell):**

```powershell
iwr -UseBasicParsing https://nacos.io/nacos-installer.ps1 -OutFile $env:TEMP\nacos-installer.ps1; & $env:TEMP\nacos-installer.ps1 -cli; Remove-Item $env:TEMP\nacos-installer.ps1
```

### Step 2: Resolve Configuration

First check whether the installed CLI supports profiles:

```bash
nacos-cli profile --help
```

If `profile` prints command help, use the managed profile workflow. If it reports an unknown command, use the legacy
`--config` file or pass connection flags directly.

#### Current Profile Workflow (`nacos-cli` 0.0.11 and newer)

`nacos-cli` 0.0.11 and newer support a **profile-based** configuration system. The default profile is stored at
`~/.nacos-cli/default.conf`; named profiles are stored as `~/.nacos-cli/<profile>.conf`. Once configured, all commands
work without extra connection flags.

Check if the default profile already exists by running:

```bash
test -f ~/.nacos-cli/default.conf && echo "configured" || echo "not configured"
```

**If the output is "configured"**, skip to the next step -- nacos-cli will use it automatically.

**If the output is "not configured"**, use the managed profile flow:

```bash
nacos-cli profile edit
```

For a named environment:

```bash
nacos-cli profile edit dev
nacos-cli --profile dev skill-list
```

If the command prompts for connection details, ask the user for:

1. Nacos server host (for example `10.0.0.1`; the CLI default is `market.hiclaw.io`)
2. Nacos server port (for example `8848`; the CLI default is `80` when no host or port is provided)
3. Auth type: `nacos`, `aliyun`, or `sts-hiclaw`
4. Credentials for the selected auth mode
5. Namespace ID (leave empty for the public namespace)

Auth mode guidance:

- `nacos`: username/password.
- `aliyun`: AccessKey/SecretKey, with optional security token when applicable.
- `sts-hiclaw`: uses dynamic STS credentials. Set `HICLAW_CONTROLLER_URL` and `HICLAW_AUTH_TOKEN_FILE`; the CLI reads
  the token file and calls `<controller>/api/v1/credentials/sts`.

Environment variables can provide connection settings when no command-line or profile value is available:

```bash
export NACOS_HOST=127.0.0.1
export NACOS_PORT=8848
export NACOS_NAMESPACE=your-namespace-id
export NACOS_AUTH_TYPE=nacos
```

Use `--config <file>` only when the user explicitly wants a specific config file or when the installed CLI does not have
the `profile` command. Otherwise prefer `profile edit` because it matches the current CLI workflow.

Configuration priority is:

1. Command-line flags
2. `--config` file or selected profile
3. Environment variables
4. CLI defaults

Useful global flags:

```bash
nacos-cli --host 127.0.0.1 --port 8848 skill-list
nacos-cli --profile dev skill-list
nacos-cli --config ./local.conf skill-list
nacos-cli --auth-type nacos --username nacos --password nacos skill-list
```

After configuring the default profile, subsequent commands use it automatically.

#### Legacy Configuration Workflow

`nacos-cli` versions older than 0.0.11 may not support `profile edit`. For those versions, either pass connection flags
on each command or create a YAML config file and use `--config`.

```bash
nacos-cli skill-list -s 127.0.0.1:8848 -u nacos -p nacos
```

Legacy config file example:

```bash
cat > local.conf << EOF
host: 127.0.0.1
port: 8848
username: nacos
password: nacos
namespace: ""
EOF

nacos-cli --config ./local.conf skill-list
```

### Step 3: Understand What They Need

When a user asks for help, identify:

1. The domain (e.g., code review, testing, deployment, documentation)
2. The specific task (e.g., writing tests, reviewing PRs, generating docs)
3. Whether this is a common enough task that a skill likely exists in Nacos

### Step 4: Search for Skills

Run the skill-list command. With current profiles this uses the selected/default profile automatically; with legacy
configuration, include `--config` or connection flags if needed.

```bash
nacos-cli skill-list
```

To filter by name:

```bash
nacos-cli skill-list --name <keyword>
```

For current CLI versions that support machine-readable output:

```bash
nacos-cli skill-list --output json
```

For example:

- User asks "can you help me review code?" -> `nacos-cli skill-list --name review`
- User asks "is there a skill for testing?" -> `nacos-cli skill-list --name test`
- User asks "what skills do we have?" -> `nacos-cli skill-list`

The command returns results in this format:

```text
Skill List (Total: N)
═══════════════════════════════════════════════════════════════════════════════
  1. <skill-name> - <description>
  2. <skill-name> - <description>
  ...
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

If the user wants to proceed, download and install the skill:

```bash
nacos-cli skill-get <skill-name>
```

The skill will be downloaded to `~/.skills/` by default. Current CLI versions support multiple skill names; older
versions may only accept one name at a time. To install multiple skills or use a custom location:

```bash
nacos-cli skill-get <skill-one> <skill-two>
nacos-cli skill-get <skill-name> -o /custom/path
```

Current CLI versions can download a specific version or route label:

```bash
nacos-cli skill-get <skill-name> --version <version>
nacos-cli skill-get <skill-name> --label latest
```

After installation, confirm the skill is available by checking the directory:

```bash
ls ~/.skills/<skill-name>/SKILL.md
```

## How to Help Users Publish and Release Skills to Nacos

When a user wants to share a skill with their team, first choose the flow that matches their installed nacos-cli.
For current lifecycle versions, do not recommend standalone upload as the user-facing goal; recommend release/publish and
use upload only as the draft step inside that flow.
Recommend upgrading to the latest nacos-cli before publishing or releasing skills. Use the legacy flow only when the
user is pinned to an older CLI.

- Current lifecycle flow (`nacos-cli` v1.0.4 and newer): `skill-upload` -> `skill-review` -> `skill-release`
- Legacy publish flow (before `nacos-cli` 1.0.4): `skill-publish`, which is equivalent to upload + review

### Step 1: Ensure nacos-cli is Available and Configured

Same as the discovery flow above -- check `which nacos-cli` and ensure a profile is configured (see Step 2 of the
discovery flow). Then detect whether lifecycle commands are available:

```bash
nacos-cli --version
nacos-cli skill-release --help
```

If `skill-release` is unavailable and the user cannot upgrade, use the pre-1.0.4 `skill-publish` flow.

### Step 2: Verify the Skill Directory

A valid skill directory must contain a `SKILL.md` file with proper frontmatter (name, description). Confirm the path:

```bash
ls <path-to-skill>/SKILL.md
```

If the file doesn't exist or lacks frontmatter, help the user create or fix it before uploading.

### Step 3: Create or Update the Draft (Current Lifecycle Only)

```bash
nacos-cli skill-upload <path-to-skill>
```

In current lifecycle versions, the command reads the skill's `SKILL.md` frontmatter and uploads the directory or ZIP as
an editing draft. Treat this as an intermediate step before review and release, not as a completed publication.

To create/update drafts for every valid skill directory under a folder:

```bash
nacos-cli skill-upload --all <skills-folder>
```

### Step 4: Submit the Draft for Review (Current Lifecycle Only)

```bash
nacos-cli skill-review <skill-name>
```

The review pipeline is asynchronous. If the server has not marked the version as reviewed yet, wait briefly and check
status before release:

```bash
nacos-cli skill-describe <skill-name>
```

Do not run `skill-release` immediately after `skill-review`. Wait until `skill-describe` shows the target version has
finished review and is approved/reviewed. If the version is still reviewing, wait and check again; if review failed or
was rejected, do not release it.

### Step 5: Release the Approved Version (Current Lifecycle Only)

```bash
nacos-cli skill-release <skill-name> --version <version>
```

Use `--update-latest=false` only when the latest route label should not move to this released version:

```bash
nacos-cli skill-release <skill-name> --version <version> --update-latest=false
```

### Legacy Publish Flow (Before 1.0.4)

For nacos-cli versions before 1.0.4, use `skill-publish`. In this flow, publish is equivalent to upload + review.
Verify the skill is visible after publishing:

```bash
nacos-cli skill-publish <path-to-skill>
nacos-cli skill-list --name <skill-name>
```

If these commands fail with "unknown command", do not retry with them. Ask whether to upgrade nacos-cli or use the
commands shown by `nacos-cli --help`.

### Step 6: Verify the Release

For current lifecycle versions, after release, verify the skill is visible and inspect version status:

```bash
nacos-cli skill-list --name <skill-name>
nacos-cli skill-describe <skill-name>
```

Example response to user:

```text
Your skill "<skill-name>" has been reviewed and released successfully.
Team members can install it with:
nacos-cli skill-get <skill-name>
```

`skill-publish` still exists for backward compatibility, but it only runs upload + review. Prefer the explicit lifecycle
commands above, especially when the user expects the skill to be online.

## Connection Reference

For newer nacos-cli versions with the `profile` command, when no command-line connection flags are provided, nacos-cli
loads the selected profile:

- default profile: `~/.nacos-cli/default.conf`
- named profile: `~/.nacos-cli/<profile>.conf` via `--profile <name>`

This is the recommended way for current nacos-cli: configure once with `nacos-cli profile edit`, then run commands
without connection flags. For older nacos-cli versions without `profile`, use `--config <file>` or pass `-s`, `-u`,
`-p`, and `-n` flags directly.

## When No Skills Are Found

If no relevant skills exist in Nacos:

1. Acknowledge that no existing skill was found
2. Offer to help with the task directly using general capabilities
3. Suggest creating and publishing or releasing a new skill

Example:

```text
I searched for skills related to "xyz" in Nacos but didn't find any matches.
I can still help you with this task directly! Would you like me to proceed?

If this is something your team does often, you could create a skill and
release it through Nacos for everyone:

# Current lifecycle versions:
nacos-cli skill-upload /path/to/your-skill
nacos-cli skill-review <skill-name>
nacos-cli skill-release <skill-name> --version <version>

# Before 1.0.4:
nacos-cli skill-publish /path/to/your-skill
```

## Tips for Effective Use

1. **One-time setup**: If `nacos-cli profile --help` works, run `nacos-cli profile edit` once; otherwise use legacy
   `--config` or connection flags
2. **Use specific keywords**: "react testing" is better than just "testing" when filtering
3. **Try alternative terms**: If "deploy" doesn't work, try "deployment" or "ci-cd"
4. **Check namespaces**: Different teams may store skills in different Nacos namespaces - use `-n <namespace>` to switch
5. **Refresh skills by version**: current lifecycle versions should use `nacos-cli skill-get <skill-name>` to download
   or refresh local skills
6. **Release is explicit in v1.0.4+**: `skill-upload` creates a draft, `skill-review` submits it, and `skill-release`
   makes an approved version online; before 1.0.4, use `skill-publish` (`publish = upload + review`)
