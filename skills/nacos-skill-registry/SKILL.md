---
name: nacos-skill-registry
description: Helps users discover and install AI skills from a team's Nacos server when they ask questions like "how do I do X", "I want to X", "help me with X", "find a skill for X", "is there a skill that can...", or express interest in extending capabilities. This skill should be used when the user is looking for functionality that might exist as an installable skill in Nacos. Also supports uploading and publishing skills for team sharing.
---

# Nacos Skill Registry

This skill helps you discover, install, and upload AI skills to a Nacos configuration center using the nacos-cli tool.

## When to Use This Skill

Use this skill when the user:

- Asks "how do I do X" where X might be a task with an existing skill in Nacos
- Says "find a skill for X" or "is there a skill in Nacos for X"
- Asks "what skills are available" or "list skills from Nacos"
- Wants to search for tools, templates, or workflows stored in Nacos
- Needs to download or install a skill from a team/organization's Nacos server
- Wants to upload or publish a skill to Nacos for their team
- Mentions they want to share or discover skills within their team

## What is nacos-cli?

The nacos-cli is a command-line tool for managing AI skills stored in a Nacos configuration center. Think of Nacos as a private skill registry for teams and organizations.

**GitHub**: https://github.com/nacos-group/nacos-cli

**Key commands:**

- `nacos-cli skill-list` - Search and list available skills
- `nacos-cli skill-get <name>` - Download and install a skill locally
- `nacos-cli skill-upload <path>` - Publish a skill to Nacos
- `nacos-cli skill-sync <name>` - Keep a skill synchronized in real-time

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

### Step 2: Resolve Configuration

nacos-cli uses a **profile-based** configuration system. The default profile is stored at `~/.nacos-cli/default.conf`.
Once configured, all commands work without any extra flags.

Check if the default profile already exists by running:

```bash
test -f ~/.nacos-cli/default.conf && echo "configured" || echo "not configured"
```

**If the output is "configured"**, skip to the next step — nacos-cli will use it automatically.

**If the output is "not configured"**, you need to create the config file for the user. Ask the user to provide the
following information:

1. Nacos server host (e.g., `10.0.0.1`)
2. Nacos server port (e.g., `8848`)
3. Auth type (`nacos` or `aliyun`)
4. Credentials (username/password for nacos auth, or AccessKey/SecretKey for aliyun auth)
5. Namespace ID (leave empty for public namespace)

Then create the config file directly:

```bash
mkdir -p ~/.nacos-cli && cat > ~/.nacos-cli/default.conf << 'EOF'
host: <user-provided-host>
port: <user-provided-port>
authType: nacos
username: <user-provided-username>
password: <user-provided-password>
namespace: <user-provided-namespace>
EOF
```

For aliyun auth type, use this format instead:

```bash
mkdir -p ~/.nacos-cli && cat > ~/.nacos-cli/default.conf << 'EOF'
host: <user-provided-host>
port: <user-provided-port>
authType: aliyun
accessKey: <user-provided-access-key>
secretKey: <user-provided-secret-key>
namespace: <user-provided-namespace>
EOF
```

After creating the config, all subsequent commands will use it automatically — no extra flags needed.

### Step 3: Understand What They Need

When a user asks for help, identify:

1. The domain (e.g., code review, testing, deployment, documentation)
2. The specific task (e.g., writing tests, reviewing PRs, generating docs)
3. Whether this is a common enough task that a skill likely exists in Nacos

### Step 4: Search for Skills

Run the skill-list command (uses the default profile automatically):

```bash
nacos-cli skill-list
```

To filter by name:

```bash
nacos-cli skill-list --name <keyword>
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

The skill will be downloaded to `~/.skills/` by default. To install to a custom location:

```bash
nacos-cli skill-get <skill-name> -o /custom/path
```

After installation, confirm the skill is available by checking the directory:

```bash
ls ~/.skills/<skill-name>/SKILL.md
```

## How to Help Users Upload Skills to Nacos

When a user wants to share a skill with their team by publishing it to Nacos, follow these steps.

### Step 1: Ensure nacos-cli is Available and Configured

Same as the discovery flow above -- check `which nacos-cli` and ensure a profile is configured (see Step 2 of the
discovery flow).

### Step 2: Verify the Skill Directory

A valid skill directory must contain a `SKILL.md` file with proper frontmatter (name, description). Confirm the path:

```bash
ls <path-to-skill>/SKILL.md
```

If the file doesn't exist or lacks frontmatter, help the user create or fix it before uploading.

### Step 3: Upload the Skill

```bash
nacos-cli skill-upload <path-to-skill>
```

The command reads the skill's `SKILL.md` frontmatter to determine the skill name and description, then publishes all files in the directory to the Nacos server.

### Step 4: Verify the Upload

After uploading, verify the skill is visible in Nacos:

```bash
nacos-cli skill-list --name <skill-name>
```

Example response to user:

```text
Your skill "<skill-name>" has been uploaded to Nacos successfully!
Team members can install it with:
nacos-cli skill-get <skill-name>
```

## Connection Reference

When no flags are provided, nacos-cli automatically loads the default profile from `~/.nacos-cli/default.conf`. This is
the recommended way to use nacos-cli — configure once with `nacos-cli profile edit`, then all commands work without any
flags.

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
```

## Tips for Effective Use

1. **One-time setup**: Run `nacos-cli profile edit` once to configure, then all commands work without any flags
2. **Use specific keywords**: "react testing" is better than just "testing" when filtering
3. **Try alternative terms**: If "deploy" doesn't work, try "deployment" or "ci-cd"
4. **Check namespaces**: Different teams may store skills in different Nacos namespaces - use `-n <namespace>` to switch
5. **Keep skills updated**: Use `nacos-cli skill-sync --all` to keep local skills in sync with Nacos