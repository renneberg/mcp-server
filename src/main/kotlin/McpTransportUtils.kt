import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.util.collections.ConcurrentMap
import io.modelcontextprotocol.kotlin.sdk.server.StreamableHttpServerTransport

const val MCP_SESSION_ID_HEADER = "mcp-session-id"

suspend fun findTransport(
    call: ApplicationCall,
    transports: ConcurrentMap<String, StreamableHttpServerTransport>,
): StreamableHttpServerTransport? {
    val sessionId = call.request.header(MCP_SESSION_ID_HEADER)
    if (sessionId.isNullOrEmpty()) {
        call.respond(HttpStatusCode.BadRequest, "Bad Request: No valid session ID provided")
        return null
    }
    val transport = transports[sessionId]
    if (transport == null) {
        call.respond(HttpStatusCode.NotFound, "Session not found")
        return null
    }
    return transport
}

suspend fun getOrCreateTransport(
    call: ApplicationCall,
    transports: ConcurrentMap<String, StreamableHttpServerTransport>,
): StreamableHttpServerTransport? {
    val sessionId = call.request.header(MCP_SESSION_ID_HEADER)
    if (sessionId != null) {
        val transport = transports[sessionId]
        if (transport == null) {
            call.respond(HttpStatusCode.NotFound, "Session not found")
        }
        return transport
    }

    val configuration = StreamableHttpServerTransport.Configuration(
        enableJsonResponse = true,
    )
    val transport = StreamableHttpServerTransport(configuration)

    transport.setOnSessionInitialized { initializedSessionId ->
        transports[initializedSessionId] = transport
    }
    transport.setOnSessionClosed { closedSessionId ->
        transports.remove(closedSessionId)
    }

    val server = createMcpServer()
    server.onClose {
        transport.sessionId?.let { transports.remove(it) }
    }
    server.createSession(transport)

    return transport
}
