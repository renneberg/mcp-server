import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.serialization.json.*
import tools.*

// Global Managers
private val languageParser = LanguageParser()
private val fileParser = FileParser(languageParser)
private val chunkManager = ChunkManager()
private val contextManager = ContextManager()

// Tool Instances
private val openFileTool = OpenFileTool(fileParser, chunkManager, contextManager)
private val nextChunkTool = NextChunkTool(chunkManager, contextManager)
private val previousChunkTool = PreviousChunkTool(chunkManager, contextManager)
private val jumpFunctionTool = JumpFunctionTool(chunkManager, contextManager)
private val searchTool = SearchTool(fileParser, contextManager)
private val currentContextTool = CurrentContextTool(chunkManager, contextManager)
private val resetTool = ResetTool(chunkManager, contextManager)
private val webFetcherTool = WebFetcherTool(contextManager)
private val writeFileTool = WriteFileTool(contextManager)
private val patchFileTool = PatchFileTool(contextManager)

fun createMcpServer(): Server {
    val server = Server(
        Implementation(
            name = "code-chunker-mcp",
            version = "1.0.0",
        ),
        ServerOptions(
            capabilities = ServerCapabilities(
                prompts = ServerCapabilities.Prompts(listChanged = true),
                resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
                tools = ServerCapabilities.Tools(listChanged = true),
                logging = ServerCapabilities.Logging
            ),
        ),
    )

    // --- READ TOOLS ---
    server.addTool(
        name = "open_file",
        description = "Opens a file and returns the first chunk",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("path") {
                    put("type", "string")
                    put("description", "Path to the file")
                }
                putJsonObject("mode") {
                    put("type", "string")
                    put("description", "Chunking mode: 'line' or 'function'")
                }
            },
            required = listOf("path"),
        ),
    ) { request -> openFileTool.handle(request.arguments) }

    server.addTool(
        name = "next_chunk",
        description = "Returns the next chunk of the currently open file",
        inputSchema = ToolSchema(properties = buildJsonObject {}),
    ) { nextChunkTool.handle() }

    server.addTool(
        name = "previous_chunk",
        description = "Returns the previous chunk of the currently open file",
        inputSchema = ToolSchema(properties = buildJsonObject {}),
    ) { previousChunkTool.handle() }

    // --- SEARCH TOOLS ---
    server.addTool(
        name = "search_text",
        description = "Searches for text in the project files",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("query") {
                    put("type", "string")
                    put("description", "Text to search for")
                }
                putJsonObject("rootPath") {
                    put("type", "string")
                    put("description", "Root directory for search")
                }
            },
            required = listOf("query"),
        ),
    ) { request -> searchTool.handle(request.arguments) }

    server.addTool(
        name = "jump_to_function",
        description = "Jumps to a specific function in the current file",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("name") {
                    put("type", "string")
                    put("description", "Name of the function")
                }
            },
            required = listOf("name"),
        ),
    ) { request -> jumpFunctionTool.handle(request.arguments) }

    // --- WEB TOOLS ---
    server.addTool(
        name = "fetch_url",
        description = "Fetches a URL and returns cleaned text content",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("url") {
                    put("type", "string")
                    put("description", "The URL to fetch")
                }
            },
            required = listOf("url"),
        ),
    ) { request -> webFetcherTool.handle(request.arguments) }

    // --- WRITE TOOLS ---
    server.addTool(
        name = "write_file",
        description = "Creates or overwrites a file with new content",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("path") {
                    put("type", "string")
                    put("description", "Path to the file")
                }
                putJsonObject("content") {
                    put("type", "string")
                    put("description", "The content to write")
                }
            },
            required = listOf("path", "content"),
        ),
    ) { request -> writeFileTool.handle(request.arguments) }

    server.addTool(
        name = "patch_file",
        description = "Replaces a specific string in a file with new content",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("path") {
                    put("type", "string")
                    put("description", "Path to the file")
                }
                putJsonObject("search") {
                    put("type", "string")
                    put("description", "The string to search for")
                }
                putJsonObject("replace") {
                    put("type", "string")
                    put("description", "The string to replace it with")
                }
            },
            required = listOf("path", "search", "replace"),
        ),
    ) { request -> patchFileTool.handle(request.arguments) }

    // --- STATE TOOLS ---
    server.addTool(
        name = "current_context",
        description = "Shows information about the currently open file and history",
        inputSchema = ToolSchema(properties = buildJsonObject {}),
    ) { currentContextTool.handle() }

    server.addTool(
        name = "reset_analysis",
        description = "Resets the file navigator and clears state",
        inputSchema = ToolSchema(properties = buildJsonObject {}),
    ) { resetTool.handle() }

    return server
}
