import java.sql.DriverManager
import java.util.Properties

fun main(args: Array<String>) {
    val user = System.getenv("DB_USER") ?: "root"
    val password = System.getenv("DB_PASSWORD") ?: "jum2nibo6"
    val host = System.getenv("DB_HOST") ?: "localhost"
    val port = System.getenv("DB_PORT") ?: "3306"
    val dbName = args.firstOrNull() ?: System.getenv("DB_NAME") ?: ""

    try {
        Class.forName("com.mysql.cj.jdbc.Driver")
        val url = if (dbName.isNotBlank()) {
            "jdbc:mysql://$host:$port/$dbName?useSSL=false&allowPublicKeyRetrieval=true"
        } else {
            "jdbc:mysql://$host:$port/?useSSL=false&allowPublicKeyRetrieval=true"
        }

        println("Connecting to MySQL at $url with user $user...")
        val props = Properties().apply {
            put("user", user)
            put("password", password)
        }

        DriverManager.getConnection(url, props).use { conn ->
            if (dbName.isBlank()) {
                println("\nNo database name provided. Listing available databases:")
                conn.createStatement().executeQuery("SHOW DATABASES").use { rs ->
                    while (rs.next()) {
                        println(" - ${rs.getString(1)}")
                    }
                }
                println("\nTip: Run with database name argument, e.g. ./gradlew queryData -PdbName=your_db_name")
            } else {
                println("\nQuerying table 'tmp__fulldata' from database '$dbName' (LIMIT 20)...")
                conn.createStatement().executeQuery("SELECT * FROM tmp__fulldata LIMIT 20").use { rs ->
                    val meta = rs.metaData
                    val cols = (1..meta.columnCount).map { meta.getColumnName(it) }
                    println(cols.joinToString(" | "))
                    println("-".repeat(80))
                    var count = 0
                    while (rs.next()) {
                        val row = cols.map { rs.getObject(it)?.toString() ?: "NULL" }
                        println(row.joinToString(" | "))
                        count++
                    }
                    println("\nTotal rows displayed: $count")
                }
            }
        }
    } catch (e: Exception) {
        println("Error querying MySQL: ${e.message}")
        e.printStackTrace()
    }
}
