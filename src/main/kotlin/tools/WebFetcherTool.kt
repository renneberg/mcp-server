package tools

import ContextManager
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

class WebFetcherTool(
    private val contextManager: ContextManager
) {
    private val client = HttpClient(CIO)

    suspend fun handle(arguments: Map<String, JsonElement>?): CallToolResult {
        if (arguments == null) return CallToolResult(content = listOf(TextContent("Error: Missing arguments")), isError = true)

        val url = arguments["url"]?.jsonPrimitive?.content ?: return CallToolResult(content = listOf(TextContent("Error: Missing URL")), isError = true)

        return try {
            val response: HttpResponse = client.get(url)
            val rawHtml = response.bodyAsText()
            
            val cleanText = cleanHtml(rawHtml)
            
            contextManager.recordAction("Fetched URL: $url")
            CallToolResult(content = listOf(TextContent(cleanText.take(10000))))
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error fetching URL: ${e.message}")), isError = true)
        }
    }

    private fun cleanHtml(html: String): String {
        var text = html.replace(Regex("<script.*?>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
        text = text.replace(Regex("<style.*?>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
        text = text.replace(Regex("<.*?>"), " ")
        return text.replace(Regex("\\s+"), " ").trim()
    }
}
