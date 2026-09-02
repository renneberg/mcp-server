import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.util.collections.ConcurrentMap
import io.modelcontextprotocol.kotlin.sdk.server.StreamableHttpServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.McpJson

fun Application.configureServer(authToken: String? = null) {
    installCors(authEnabled = authToken != null)
    install(ContentNegotiation) {
        json(McpJson)
    }

    if (authToken == null) {
        mcpStreamableHttp {
            createMcpServer()
        }
    } else {
        configureAuthenticatedMcp(authToken)
    }
}

private fun Application.configureAuthenticatedMcp(authToken: String) {
    install(SSE)
    install(Authentication) {
        bearer("mcp-bearer") {
            authenticate { credential ->
                if (credential.token == authToken) {
                    UserIdPrincipal("mcp-client")
                } else {
                    null
                }
            }
        }
    }

    val transports = ConcurrentMap<String, StreamableHttpServerTransport>()

    routing {
        authenticate("mcp-bearer") {
            route("/mcp") {
                sse {
                    val transport = findTransport(call, transports) ?: return@sse
                    transport.handleRequest(this, call)
                }

                post {
                    val transport = getOrCreateTransport(call, transports) ?: return@post
                    transport.handleRequest(null, call)
                }

                delete {
                    val transport = findTransport(call, transports) ?: return@delete
                    transport.handleRequest(null, call)
                }
            }
        }
    }
}

private fun Application.installCors(authEnabled: Boolean = false) {
    install(CORS) {
        anyHost() 
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Delete)
        allowNonSimpleContentTypes = true
        allowHeader("Mcp-Session-Id")
        allowHeader("Mcp-Protocol-Version")
        exposeHeader("Mcp-Session-Id")
        exposeHeader("Mcp-Protocol-Version")
        if (authEnabled) {
            allowHeader(HttpHeaders.Authorization)
        }
    }
}
