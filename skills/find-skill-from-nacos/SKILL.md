---
name: find-skill-from-nacos
description: Discover and install AI skills from Nacos. Use when users want to find or install skills from a team's Nacos server.
---

# Find Skills from Nacos

This skill helps you discover and install AI skills from a Nacos configuration center using the nacos-cli tool.

## When to Use This Skill

Use this skill when the user:

- Asks "how do I do X" where X might be a task with an existing skill in Nacos
- Says "find a skill for X" or "is there a skill in Nacos for X"
- Asks "what skills are available" or "list skills from Nacos"
- Wants to search for tools, templates, or workflows stored in Nacos
- Needs to download or install a skill from a team/organization's Nacos server
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

If not found, install it from source:

```bash
git clone https://github.com/nacos-group/nacos-cli.git
cd nacos-cli && go build -o nacos-cli
mkdir -p ~/.local/bin
cp nacos-cli ~/.local/bin/
export PATH="$HOME/.local/bin:$PATH"
```

To make the PATH change permanent, add the following line to `~/.bashrc` or `~/.zshrc`:
```bash
export PATH="$HOME/.local/bin:$PATH"
```

### Step 2: Resolve Configuration

The default config file path is `~/.find-skill-from-nacos.conf`. Check if it exists:

```bash
cat ~/.find-skill-from-nacos.conf
```

**If the config file exists**, use it directly for all subsequent commands:

```bash
nacos-cli --config ~/.find-skill-from-nacos.conf skill-list
```

**If the config file does NOT exist**, ask the user to choose one of the following options:

1. **Provide an existing config file path** - User has a config file elsewhere. Use it directly, no need to save.
2. **Use default configuration** - Use the built-in defaults (127.0.0.1:8848, nacos/nacos). Save to `~/.find-skill-from-nacos.conf`.
3. **Input custom configuration** - User provides host, port, username, password, namespace. Save to `~/.find-skill-from-nacos.conf`.

For options 2 and 3, save the configuration to `~/.find-skill-from-nacos.conf` so it can be reused next time:

```yaml
# ~/.find-skill-from-nacos.conf
host: 127.0.0.1
port: 8848
username: nacos
password: nacos
namespace: ""
```

### Step 3: Understand What They Need

When a user asks for help, identify:

1. The domain (e.g., code review, testing, deployment, documentation)
2. The specific task (e.g., writing tests, reviewing PRs, generating docs)
3. Whether this is a common enough task that a skill likely exists in Nacos

### Step 4: Search for Skills

Run the skill-list command with the resolved config:

```bash
nacos-cli --config ~/.find-skill-from-nacos.conf skill-list
```

To filter by name:

```bash
nacos-cli --config ~/.find-skill-from-nacos.conf skill-list --name <keyword>
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
nacos-cli --config ~/.find-skill-from-nacos.conf skill-get <skill-name>

This will download the skill to ~/.skills/ and make it available immediately.
Would you like me to install it?
```

### Step 6: Install the Skill

If the user wants to proceed, download and install the skill:

```bash
nacos-cli --config ~/.find-skill-from-nacos.conf skill-get <skill-name>
```

The skill will be downloaded to `~/.skills/` by default. To install to a custom location:

```bash
nacos-cli --config ~/.find-skill-from-nacos.conf skill-get <skill-name> -o /custom/path
```

After installation, confirm the skill is available by checking the directory:

```bash
ls ~/.skills/<skill-name>/SKILL.md
```

## Connection Reference

**Command-line flags** (can override config file settings):

| Flag | Short | Description |
| :--- | :--- | :--- |
| --server | -s | Nacos server address (host:port) |
| --username | -u | Nacos username (default: nacos) |
| --password | -p | Nacos password (default: nacos) |
| --namespace | -n | Nacos namespace ID |
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
nacos-cli skill-upload /path/to/your-skill --config ~/.find-skill-from-nacos.conf
```

## Tips for Effective Use

1. **Use specific keywords**: "react testing" is better than just "testing" when filtering
2. **Try alternative terms**: If "deploy" doesn't work, try "deployment" or "ci-cd"
3. **Check namespaces**: Different teams may store skills in different Nacos namespaces - use `-n <namespace>` to switch
4. **Use config files**: Save connection details to avoid typing credentials repeatedly
5. **Keep skills updated**: Use `nacos-cli skill-sync --all` to keep local skills in sync with Nacos