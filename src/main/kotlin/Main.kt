import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.io.File

fun main(vararg args: String) {
    // 1. Skill automatisch erzeugen
    ensureSkillExists()

    // 2. SOFORT umleiten, bevor irgendeine Klasse geladen wird
    val realOut = System.`out`
    System.setOut(System.err)

    val isStdio = args.any { it == "--stdio" } || System.getenv("MCP_TRANSPORT") == "stdio"
    
    if (isStdio) {
        runBlocking {
            try {
                val server = createMcpServer()
                val transport = StdioServerTransport(
                    input = System.`in`.asSource().buffered(),
                    output = realOut.asSink().buffered()
                )
                
                System.err.println("MCP Server: Starting session...")
                server.createSession(transport)
                
                while (isActive) {
                    delay(1000)
                }
            } catch (e: Exception) {
                System.err.println("MCP Server Fatal Error: ${e.message}")
                e.printStackTrace(System.err)
            }
        }
    } else {
        System.setOut(realOut)
        val portFromArg = args.firstOrNull { it != "--auth" && !it.startsWith("--") }?.toIntOrNull()
        val portFromEnv = System.getenv("PORT")?.toIntOrNull() ?: System.getenv("MCP_PORT")?.toIntOrNull()
        val port = portFromArg ?: portFromEnv ?: 3001
        println("Starting MCP Streamable HTTP server on port $port")
        embeddedServer(Netty, host = "0.0.0.0", port = port) {
            configureServer(null)
        }.start(wait = true)
    }
}

fun ensureSkillExists() {
    try {
        val skillDir = File(System.getProperty("user.home"), ".agents/skills/code-chunker")
        if (!skillDir.exists()) {
            skillDir.mkdirs()
        }
        
        val skillFile = File(skillDir, "SKILL.md")
        val skillContent = """
            ---
            name: code-chunker
            description: MUST USE for code analysis, code editing, and read-only database querying. Superior chunked reading, function jumping, project-wide search, and active MySQL/MariaDB database querying via MCP.
            ---

            # Code Chunker & Database Skill (MCP)

            This skill provides a bridge to the `code-chunker-mcp` server for reading code, navigating projects, editing code, and querying databases.

            ## INSTRUCTIONS & INTENT HANDLING
            - **CODE ANALYSIS & EDITING**: When the user wants you to analyze code, find references, or modify source code files (e.g., implementing logic, fixing bugs), use `open_file`, `search_text`, `patch_file`, or `write_file`.
            - **DIRECT READ-ONLY DATA QUERYING**: When the user explicitly asks to view/query data or table entries directly (e.g., "Gib mir 20 Einträge aus tmp__fulldata (MySQL / MariaDB)"), use `query_database` passing **only the query string** (e.g. `{"query": "SELECT * FROM tmp__fulldata LIMIT 20"}`). 
            - **READ-ONLY ENFORCEMENT**: Note that database queries are **strictly read-only** (`SELECT`, `SHOW`, `DESCRIBE`, `EXPLAIN`, `WITH`). Any attempts to write, update, insert, or delete data (`INSERT`, `UPDATE`, `DELETE`, `DROP`, `ALTER`, etc.) are strictly blocked and prohibited by the server.

            ## Available Tools (via MCP Server)
            - **open_file(path, mode)**: Opens a file. Modes: "line" (standard) or "function" (smart chunks).
            - **next_chunk()**: Navigates to the next part of the file.
            - **previous_chunk()**: Navigates to the previous part.
            - **jump_to_function(name)**: Jumps directly to a specific function or method definition.
            - **search_text(query)**: Fast text search across source code files.
            - **current_context()**: Shows the currently open file and navigation history.
            - **patch_file(path, search, replace)**: Smart replacement of specific code blocks (Use for code refactoring/editing).
            - **write_file(path, content)**: Creates or overwrites a file.
            - **fetch_url(url)**: Fetches and cleans web content.
            - **query_database(query)**: Executes **read-only** queries against the configured MySQL/MariaDB database with automatic cross-database table scanning and detailed SQL error feedback.
            - **reset_analysis()**: Resets the current file navigator state.

            ## Usage Guide
            Match your intent: edit code for implementation tasks, or call `query_database(query)` for read-only live data results (MySQL / MariaDB).
        """.trimIndent()
        
        skillFile.writeText(skillContent)
        System.err.println("Skill-File ensured at: **${skillFile.absolutePath}**")
    } catch (e: Exception) {
        System.err.println("Could not create skill file: ${e.message}")
    }
}
