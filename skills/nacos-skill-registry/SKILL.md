---
name: nacos-skill-registry
description: Discover, install, and upload AI skills with Nacos. Use when users want to find, install, or publish skills to a team's Nacos server.
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

The default config file path is `~/.nacos-skill-registry.conf`. Check if it exists:

```bash
cat ~/.nacos-skill-registry.conf
```

**If the config file exists**, use it directly for all subsequent commands:

```bash
nacos-cli --config ~/.nacos-skill-registry.conf skill-list
```

**If the config file does NOT exist**, ask the user to choose one of the following options:

1. **Provide an existing config file path** - User has a config file elsewhere. Use it directly, no need to save.
2. **Input custom configuration** - User provides host, port, username, password, namespace. Save to `~/.nacos-skill-registry.conf`.

For option 2, save the configuration to `~/.nacos-skill-registry.conf` so it can be reused next time:

```yaml
# ~/.nacos-skill-registry.conf
host: <user-provided-host>
port: <user-provided-port>
username: <user-provided-username>
password: <user-provided-password>
namespace: <user-provided-namespace>
```

**IMPORTANT: After saving the config file, you MUST use `--config ~/.nacos-skill-registry.conf` in all subsequent commands. Do NOT pass host, port, username, or password as individual command-line flags — nacos-cli does not support them.**

### Step 3: Understand What They Need

When a user asks for help, identify:

1. The domain (e.g., code review, testing, deployment, documentation)
2. The specific task (e.g., writing tests, reviewing PRs, generating docs)
3. Whether this is a common enough task that a skill likely exists in Nacos

### Step 4: Search for Skills

Run the skill-list command with the resolved config:

```bash
nacos-cli --config ~/.nacos-skill-registry.conf skill-list
```

To filter by name:

```bash
nacos-cli --config ~/.nacos-skill-registry.conf skill-list --name <keyword>
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
nacos-cli --config ~/.nacos-skill-registry.conf skill-get <skill-name>

This will download the skill to ~/.skills/ and make it available immediately.
Would you like me to install it?
```

### Step 6: Install the Skill

If the user wants to proceed, download and install the skill:

```bash
nacos-cli --config ~/.nacos-skill-registry.conf skill-get <skill-name>
```

The skill will be downloaded to `~/.skills/` by default. To install to a custom location:

```bash
nacos-cli --config ~/.nacos-skill-registry.conf skill-get <skill-name> -o /custom/path
```

After installation, confirm the skill is available by checking the directory:

```bash
ls ~/.skills/<skill-name>/SKILL.md
```

## How to Help Users Upload Skills to Nacos

When a user wants to share a skill with their team by publishing it to Nacos, follow these steps.

### Step 1: Ensure nacos-cli is Available and Configured

Same as the discovery flow above -- check `which nacos-cli` and resolve configuration via `~/.nacos-skill-registry.conf`.

### Step 2: Verify the Skill Directory

A valid skill directory must contain a `SKILL.md` file with proper frontmatter (name, description). Confirm the path:

```bash
ls <path-to-skill>/SKILL.md
```

If the file doesn't exist or lacks frontmatter, help the user create or fix it before uploading.

### Step 3: Upload the Skill

```bash
nacos-cli --config ~/.nacos-skill-registry.conf skill-upload <path-to-skill>
```

The command reads the skill's `SKILL.md` frontmatter to determine the skill name and description, then publishes all files in the directory to the Nacos server.

### Step 4: Verify the Upload

After uploading, verify the skill is visible in Nacos:

```bash
nacos-cli --config ~/.nacos-skill-registry.conf skill-list --name <skill-name>
```

Example response to user:

```text
Your skill "<skill-name>" has been uploaded to Nacos successfully!
Team members can install it with:
nacos-cli --config <their-config> skill-get <skill-name>
```

## Connection Reference

**IMPORTANT: nacos-cli does NOT support `--host`, `--port`, `--user`, `--password` or `--namespace` as command-line flags. You MUST always use `--config` to pass connection settings. NEVER attempt to pass connection parameters as individual command-line flags.**

The ONLY supported connection flag:

| Flag | Short | Description |
| :--- | :--- | :--- |
| --config | -c | Path to configuration file |

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
nacos-cli skill-upload /path/to/your-skill --config ~/.nacos-skill-registry.conf
```

## Tips for Effective Use

1. **Always use `--config`**: nacos-cli only accepts connection settings via config file. Never use `--host`, `--port`, `--user`, `--password`, or `--namespace` as command-line flags — they are not supported and will cause errors.
2. **Use specific keywords**: "react testing" is better than just "testing" when filtering
3. **Try alternative terms**: If "deploy" doesn't work, try "deployment" or "ci-cd"
4. **Check namespaces**: Different teams may store skills in different Nacos namespaces - use `-n <namespace>` to switch
5. **Use config files**: Save connection details to avoid typing credentials repeatedly
6. **Keep skills updated**: Use `nacos-cli skill-sync --all` to keep local skills in sync with Nacos