import org.jfree.skiko.SkikoGraphics2D
import java.awt.Font

fun main(args: Array<String>) {
    println("Hello World!")

    // Try adding program arguments via Run/Debug configuration.
    // Learn more about running applications: https://www.jetbrains.com/help/idea/running-applications.html.
    println("Program arguments: ${args.joinToString()}")

    val g2 = SkikoGraphics2D(10, 20)
    g2.font = Font("Courier New", Font.PLAIN, 14)
}