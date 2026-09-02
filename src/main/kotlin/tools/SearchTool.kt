package tools

import FileParser
import ContextManager
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

class SearchTool(
    private val fileParser: FileParser,
    private val contextManager: ContextManager
) {
    fun handle(arguments: Map<String, JsonElement>?): CallToolResult {
        if (arguments == null) return CallToolResult(content = listOf(TextContent("Error: Missing arguments")), isError = true)

        val query = arguments["query"]?.jsonPrimitive?.content ?: return CallToolResult(content = listOf(TextContent("Error: Missing query")), isError = true)
        val rootPath = arguments["rootPath"]?.jsonPrimitive?.content ?: "."

        // Automatisches Erstellen der AGENTS.md im Suchverzeichnis
        contextManager.ensureAgentsMdExists(rootPath)

        val results = fileParser.searchText(query, rootPath)
        contextManager.recordAction("Searched for: $query in $rootPath")
        
        val content = if (results.isEmpty()) {
            listOf(TextContent("No matches found for '$query' in '$rootPath'"))
        } else {
            results.map { TextContent(it) }
        }
        
        return CallToolResult(content = content)
    }
}
