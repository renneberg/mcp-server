package tools

import ContextManager
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties

class QueryDatabaseTool(
    private val contextManager: ContextManager
) {
    init {
        // Ensure JDBC drivers are registered
        try { Class.forName("org.sqlite.JDBC") } catch (_: Exception) {}
        try { Class.forName("com.mysql.cj.jdbc.Driver") } catch (_: Exception) {}
        try { Class.forName("org.mariadb.jdbc.Driver") } catch (_: Exception) {}

        // Test database connection on startup
        testConnectionOnStartup()
    }

    private fun testConnectionOnStartup() {
        val url = getDefaultJdbcUrl()
        val user = System.getenv("DB_USER") ?: "root"
        val pass = System.getenv("DB_PASSWORD") ?: ""
        try {
            val props = Properties().apply {
                if (user.isNotBlank()) put("user", user)
                if (pass.isNotBlank()) put("password", pass)
                put("useSSL", "false")
                put("allowPublicKeyRetrieval", "true")
                put("connectTimeout", "3000")
            }
            DriverManager.getConnection(url, props).use { _ ->
                System.err.println("MCP Server: Successfully established initial database connection to $url (User: $user)")
            }
        } catch (e: Exception) {
            System.err.println("MCP Server Startup Warning: Could not connect to database at $url: ${e.message}")
        }
    }

    private fun getDefaultJdbcUrl(): String {
        System.getenv("DB_URL")?.let { return it }
        val host = System.getenv("DB_HOST") ?: "localhost"
        val port = System.getenv("DB_PORT") ?: "3306"
        val dbName = System.getenv("DB_NAME") ?: ""
        return if (dbName.isNotBlank()) {
            "jdbc:mysql://$host:$port/$dbName"
        } else {
            "jdbc:mysql://$host:$port/"
        }
    }

    private fun extractTableName(query: String): String? {
        val regex = "(?i)\\b(?:FROM|JOIN)\\s+([`\"]?)([a-zA-Z0-9_]+)\\1".toRegex()
        val match = regex.find(query)
        return match?.groupValues?.getOrNull(2)
    }

    fun handle(arguments: Map<String, JsonElement>?): CallToolResult {
        if (arguments == null) {
            return CallToolResult(content = listOf(TextContent("Error: Missing arguments")), isError = true)
        }

        val query = arguments["query"]?.jsonPrimitive?.content 
            ?: return CallToolResult(content = listOf(TextContent("Error: Missing 'query' argument")), isError = true)

        val urlArg = arguments["url"]?.jsonPrimitive?.content ?: getDefaultJdbcUrl()
        val username = arguments["username"]?.jsonPrimitive?.content ?: System.getenv("DB_USER")
        val password = arguments["password"]?.jsonPrimitive?.content ?: System.getenv("DB_PASSWORD")

        // Safety check: ensure read-only
        val trimmedQuery = query.trim().uppercase()
        val allowedPrefixes = listOf("SELECT", "PRAGMA", "EXPLAIN", "WITH", "SHOW", "DESCRIBE")
        val isAllowed = allowedPrefixes.any { trimmedQuery.startsWith(it) }
        
        val forbiddenKeywords = listOf("INSERT", "UPDATE", "DELETE", "DROP", "CREATE", "ALTER", "TRUNCATE", "REPLACE", "GRANT", "REVOKE", "EXEC", "EXECUTE")
        val hasForbidden = forbiddenKeywords.any { keyword ->
            trimmedQuery.contains(keyword)
        }

        if (!isAllowed || hasForbidden) {
            return CallToolResult(
                content = listOf(TextContent("Error: Only read-only queries (SELECT, PRAGMA, EXPLAIN, WITH, SHOW, DESCRIBE) are permitted. Modification queries are strictly prohibited.")),
                isError = true
            )
        }

        val jdbcUrl = if (urlArg.startsWith("jdbc:")) {
            urlArg
        } else {
            "jdbc:sqlite:$urlArg"
        }

        try {
            val connection = if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                val props = Properties().apply {
                    put("user", username)
                    put("password", password)
                    put("useSSL", "false")
                    put("allowPublicKeyRetrieval", "true")
                }
                DriverManager.getConnection(jdbcUrl, props)
            } else {
                DriverManager.getConnection(jdbcUrl)
            }

            connection.use { conn ->
                try {
                    conn.isReadOnly = true
                } catch (_: Exception) {}

                // Automatically scan databases for table name if no specific database was provided in URL
                if (jdbcUrl.contains("mysql") || jdbcUrl.contains("mariadb")) {
                    val hasSpecificDb = jdbcUrl.substringAfterLast("/").isNotBlank() && !jdbcUrl.endsWith("/")
                    if (!hasSpecificDb) {
                        val tableName = extractTableName(query)
                        if (tableName != null) {
                            try {
                                val stmt = conn.createStatement()
                                val rs = stmt.executeQuery("SELECT TABLE_SCHEMA FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = '$tableName' AND TABLE_SCHEMA NOT IN ('information_schema', 'performance_schema', 'mysql', 'sys')")
                                val schemas = mutableListOf<String>()
                                while (rs.next()) {
                                    schemas.add(rs.getString(1))
                                }
                                rs.close()
                                stmt.close()

                                when {
                                    schemas.size == 1 -> {
                                        conn.catalog = schemas[0]
                                    }
                                    schemas.size > 1 -> {
                                        return CallToolResult(
                                            content = listOf(TextContent("Error: Table '$tableName' exists in multiple databases (${schemas.joinToString(", ")}). Please specify the database name in your query (e.g. SELECT * FROM databasename.$tableName).")),
                                            isError = true
                                        )
                                    }
                                }
                            } catch (_: Exception) {
                                // Proceed with query if schema scan fails
                            }
                        }
                    }
                }

                conn.createStatement().use { statement ->
                    statement.executeQuery(query).use { resultSet ->
                        val metaData = resultSet.metaData
                        val columnCount = metaData.columnCount
                        val columns = (1..columnCount).map { metaData.getColumnName(it) }

                        val rows = mutableListOf<Map<String, Any?>>()
                        while (resultSet.next()) {
                            val row = mutableMapOf<String, Any?>()
                            for (i in 1..columnCount) {
                                row[columns[i - 1]] = resultSet.getObject(i)
                            }
                            rows.add(row)
                        }

                        contextManager.recordAction("Queried database ($jdbcUrl): $query")

                        if (rows.isEmpty()) {
                            return CallToolResult(content = listOf(TextContent("Query executed successfully. No rows returned.\nColumns: ${columns.joinToString(", ")}")))
                        }

                        val sb = StringBuilder()
                        sb.append("Columns: ${columns.joinToString(" | ")}\n")
                        sb.append("-".repeat(60)).append("\n")
                        for (row in rows) {
                            val line = columns.map { col -> "${row[col]}" }.joinToString(" | ")
                            sb.append(line).append("\n")
                        }
                        sb.append("\nTotal rows: ${rows.size}")

                        return CallToolResult(content = listOf(TextContent(sb.toString())))
                    }
                }
            }
        } catch (e: SQLException) {
            val errorMsg = "Database Error [SQLState: ${e.sqlState}, ErrorCode: ${e.errorCode}]: ${e.message}"
            return CallToolResult(
                content = listOf(TextContent(errorMsg)),
                isError = true
            )
        } catch (e: Exception) {
            val errorMsg = "Error executing query: ${e.message}"
            return CallToolResult(
                content = listOf(TextContent(errorMsg)),
                isError = true
            )
        }
    }
}
