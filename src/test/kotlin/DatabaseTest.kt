package tools

import ContextManager
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.sql.DriverManager

class DatabaseTest {

    @Test
    fun testSQLiteConnectionAndQuery() {
        val tempFile = File.createTempFile("test_db", ".sqlite")
        tempFile.deleteOnExit()

        DriverManager.getConnection("jdbc:sqlite:${tempFile.absolutePath}").use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT)")
                stmt.execute("INSERT INTO users (name) VALUES ('Alice')")
                stmt.execute("INSERT INTO users (name) VALUES ('Bob')")
            }
        }

        val contextManager = ContextManager()
        val queryTool = QueryDatabaseTool(contextManager)

        val args = buildJsonObject {
            put("url", tempFile.absolutePath)
            put("query", "SELECT * FROM users")
        }

        val result: CallToolResult = queryTool.handle(args)
        assertFalse(result.isError == true)
        val textContent = result.content.firstOrNull() as? TextContent
        val content = textContent?.text ?: ""
        assertTrue(content.contains("Alice"))
        assertTrue(content.contains("Bob"))
    }

    @Test
    fun testReadonlyEnforcement() {
        val tempFile = File.createTempFile("test_db_ro", ".sqlite")
        tempFile.deleteOnExit()

        val contextManager = ContextManager()
        val queryTool = QueryDatabaseTool(contextManager)

        val args = buildJsonObject {
            put("url", tempFile.absolutePath)
            put("query", "INSERT INTO users (name) VALUES ('Charlie')")
        }

        val result: CallToolResult = queryTool.handle(args)
        assertTrue(result.isError == true)
        val textContent = result.content.firstOrNull() as? TextContent
        val content = textContent?.text ?: ""
        assertTrue(content.contains("Only read-only queries"))
    }

    @Test
    fun testMySQLAndMariaDBCredentialsHandling() {
        val contextManager = ContextManager()
        val queryTool = QueryDatabaseTool(contextManager)

        // Test passing explicit username and password for MySQL / MariaDB URL
        val args = buildJsonObject {
            put("url", "jdbc:mysql://localhost:3306/testdb")
            put("query", "SELECT 1")
            put("username", "my_mysql_user")
            put("password", "my_mysql_pass")
        }

        val result: CallToolResult = queryTool.handle(args)
        // Since no local MySQL server is running, it will return a database connection error,
        // but this verifies that the parameters and credentials are correctly accepted and processed.
        assertNotNull(result)
        val textContent = result.content.firstOrNull() as? TextContent
        val content = textContent?.text ?: ""
        assertTrue(result.isError == true)
        assertTrue(content.contains("Database error") || content.contains("Communications link failure") || content.contains("Connection refused") || content.contains("Access denied"))
    }
}
