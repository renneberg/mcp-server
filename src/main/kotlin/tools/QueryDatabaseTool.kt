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

    private fun extractAllTableNames(query: String): List<String> {
        val tableRegex = "(?i)\\b(?:FROM|JOIN|,)\\s+(?:([`\"]?[a-zA-Z0-9_]+[`\"]?)\\s*\\.\\s*)?([`\"]?)([a-zA-Z0-9_]+)\\2".toRegex()
        val matches = tableRegex.findAll(query)
        val tables = mutableSetOf<String>()
        for (match in matches) {
            val dbGroup = match.groups[1]?.value
            val tableGroup = match.groups[3]?.value
            if (tableGroup != null && dbGroup.isNullOrBlank()) {
                val cleanTable = tableGroup.replace("`", "").replace("\"", "")
                // Filter out common SQL keywords that might falsely match
                if (!listOf("SELECT", "WHERE", "GROUP", "ORDER", "LIMIT", "AS", "ON", "USING").contains(cleanTable.uppercase())) {
                    tables.add(cleanTable)
                }
            }
        }
        return tables.toList()
    }

    private fun resolveQueryTables(conn: java.sql.Connection, query: String): String {
        val tableNames = extractAllTableNames(query)
        if (tableNames.isEmpty()) return query

        var resolvedQuery = query
        val stmt = conn.createStatement()

        for (tableName in tableNames) {
            // Skip if query already references db.tableName
            if (resolvedQuery.contains(Regex("(?i)\\b[a-zA-Z0-9_]+\\s*\\.\\s*`?$tableName`?\\b"))) continue

            val rs = stmt.executeQuery("SELECT TABLE_SCHEMA FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = '$tableName' AND TABLE_SCHEMA NOT IN ('information_schema', 'performance_schema', 'mysql', 'sys')")
            val schemas = mutableListOf<String>()
            while (rs.next()) {
                schemas.add(rs.getString(1))
            }
            rs.close()

            when {
                schemas.size == 1 -> {
                    val db = schemas[0]
                    val pattern = "(?i)\\b(FROM|JOIN|,)\\s+(`?)$tableName\\2\\b".toRegex()
                    resolvedQuery = pattern.replace(resolvedQuery) { match ->
                        "${match.groupValues[1]} `$db`.`$tableName`"
                    }
                }
                schemas.size > 1 -> {
                    throw IllegalStateException("Table '$tableName' exists in multiple databases (${schemas.joinToString(", ")}). Please qualify with the database name (e.g., databasename.$tableName).")
                }
            }
        }
        stmt.close()
        return resolvedQuery
    }

    fun handle(arguments: Map<String, JsonElement>?): CallToolResult {
        if (arguments == null) {
            return CallToolResult(content = listOf(TextContent("Error: Missing arguments")), isError = true)
        }

        val rawQuery = arguments["query"]?.jsonPrimitive?.content 
            ?: return CallToolResult(content = listOf(TextContent("Error: Missing 'query' argument")), isError = true)

        val urlArg = arguments["url"]?.jsonPrimitive?.content ?: getDefaultJdbcUrl()
        val username = arguments["username"]?.jsonPrimitive?.content ?: System.getenv("DB_USER")
        val password = arguments["password"]?.jsonPrimitive?.content ?: System.getenv("DB_PASSWORD")

        // Safety check: ensure read-only
        val trimmedQuery = rawQuery.trim().uppercase()
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

                // Automatically resolve table references across multiple MySQL/MariaDB databases
                val query = if (jdbcUrl.contains("mysql") || jdbcUrl.contains("mariadb")) {
                    try {
                        resolveQueryTables(conn, rawQuery)
                    } catch (e: Exception) {
                        return CallToolResult(
                            content = listOf(TextContent("Database Resolution Error: ${e.message}")),
                            isError = true
                        )
                    }
                } else {
                    rawQuery
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
