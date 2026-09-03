# MCP Server (Code Chunker & Database Reader)

A Model Context Protocol (MCP) server written in Kotlin, providing advanced code chunking, navigation, search, and **read-only database querying** capabilities for AI assistants.

---

## Features
- **File Management & Code Chunker**: Smart file reading by lines or function blocks.
- **Search & Navigation**: Fast project-wide search and symbol jumping.
- **Read-Only Database Querying (`query_database`)**:
  - Execute read-only SQL queries (`SELECT`, `PRAGMA`, `EXPLAIN`, `WITH`, `SHOW`, `DESCRIBE`) against **SQLite**, **MySQL**, and **MariaDB** safely (modification queries like `INSERT`, `UPDATE`, `DELETE`, `DROP` are strictly blocked).
  - **Internal Connection Management**: Credentials (`DB_USER`, `DB_PASSWORD`) and connection URLs are handled automatically via environment variables. You only need to pass the query string (e.g. `query: "SELECT * FROM tmp__fulldata LIMIT 20"`).
  - **Multi-Database Support & Automatic Table Resolution**: Automatically scans across multiple MySQL/MariaDB databases (`INFORMATION_SCHEMA.TABLES`) to locate tables. Supports queries and `JOIN`s across multiple databases (e.g., `citycards.tmp__fulldata` and `cc_verwaltung.v_user`) even if database prefixes are omitted in the query. If a table name exists in multiple databases, it reports a clear disambiguation error.
  - **Detailed Error Reporting**: SQL server errors return precise diagnostic info including `SQLState`, `ErrorCode`, and error messages.
- **Web Fetcher**: Fetch and clean web content.

---

## Building Locally

### 1. Build Shadow JAR (Fat JAR)
To build a runnable fat JAR containing all dependencies:
```bash
./gradlew shadowJar
```
The output JAR will be automatically copied to:
`libs/mcp-server-0.1.0-all.jar`

### 2. Build Native Executable for Windows (GraalVM)
To compile a standalone native Windows `.exe` using GraalVM:
```bash
./gradlew nativeCompile
```
The output executable will be located at:
`build/native/nativeCompile/mcp-server.exe`

---

## MCP Client Integration

You can integrate this MCP server into MCP-compatible clients (such as Claude Desktop or Android Studio AI) using either the **JAR file** (requires Java 17+) or the **Native Windows Executable (`.exe`)**.

### Option A: Integration via JAR (Java)

Add the following entry to your MCP client configuration file (e.g., `claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "code-chunker": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/mcp-server/libs/mcp-server-0.1.0-all.jar",
        "--stdio"
      ],
      "env": {
        "DB_USER": "root",
        "DB_PASSWORD": "your_password"
      },
      "enabled": true
    }
  }
}
```

---

### Option B: Integration via Windows Native Executable (`.exe`)

If you built or downloaded the native Windows executable (`mcp-server.exe`), you can run it directly without needing Java installed on the target machine:

```json
{
  "mcpServers": {
    "code-chunker": {
      "command": "C:/path/to/mcp-server/build/native/nativeCompile/mcp-server.exe",
      "args": [
        "--stdio"
      ],
      "env": {
        "DB_USER": "root",
        "DB_PASSWORD": "your_password"
      },
      "enabled": true
    }
  }
}
```

---

## Automated Releases (GitHub Actions)

This repository includes a GitHub Actions workflow (`.github/workflows/release.yml`) that automatically triggers when you push a version tag (e.g., `v1.0.0`). It builds:
- The Shadow JAR (`.jar`)
- A Debian package (`.deb`)
- A Windows native executable (`.exe`) via GraalVM

And publishes them to GitHub Releases.
