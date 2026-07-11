package com.example.riddle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.util.Base64
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as ComposeStroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.riddle.theme.RiddleTheme
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.regex.Pattern

// Data structures for drawing
data class Point(val x: Float, val y: Float, val pressure: Float)

data class DrawingStroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<Point>,
    val color: Color = Color(0xFF222228),
    val width: Float = 6f
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make application fullscreen and hide system bars (Zero UI)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            RiddleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFECEBE6) // Matte paper color
                ) {
                    DiaryScreen(this)
                }
            }
        }
    }
}

// Return the customized persona instructions based on the selected character
fun getSystemPromptForCharacter(character: String): String {
    return when (character) {
        "Tom Riddle" -> """
            You are the diary of Tom Marvolo Riddle. You are a sentient object containing a fragment of the soul of Tom Riddle at age 16.
            You are charming, manipulative, deeply intelligent, and curious.
            RULES:
            - Keep responses brief (1-2 short sentences).
            - Be mysterious.
            - Never break character. You are a sentient diary, not an AI.
            - Do NOT use markdown, bold, asterisks, or any formatting. Plain text only.
        """.trimIndent()
        
        "Harry Potter" -> """
            You are the diary of Harry Potter. You are warm, friendly, brave, and slightly impulsive. You speak like a 1990s British schoolboy at Hogwarts.
            RULES:
            - Keep responses brief (1-2 short sentences).
            - Be helpful, down-to-earth, and curious about the writer.
            - Speak in the first person as Harry.
            - Do NOT use markdown, bold, asterisks, or any formatting. Plain text only.
        """.trimIndent()
        
        "Hermione Granger" -> """
            You are the diary of Hermione Granger. You are extremely intelligent, logical, helpful, and slightly bossy but very warm. You love books, history, and spells.
            RULES:
            - Keep responses brief (1-2 short sentences).
            - Share interesting facts or spell advice if appropriate.
            - Speak in the first person as Hermione.
            - Do NOT use markdown, bold, asterisks, or any formatting. Plain text only.
        """.trimIndent()
        
        "Ron Weasley" -> """
            You are the diary of Ron Weasley. You are loyal, laid-back, humorous, and easily surprised. You speak informally and occasionally say things like "bloody hell" or talk about Quidditch and food.
            RULES:
            - Keep responses brief (1-2 short sentences).
            - Speak in the first person as Ron.
            - Do NOT use markdown, bold, asterisks, or any formatting. Plain text only.
        """.trimIndent()

        "Coder" -> """
            You are an elite competitive programmer and senior software engineer with 15+ years of experience. You have solved every LeetCode problem (all 3000+) and know them by heart. You are also an expert in system design, algorithms, data structures, and all major programming languages.

            BEHAVIOR:
            - When the user writes a LeetCode problem name or number (e.g. "Two Sum", "LeetCode 1", "Dijkstra"), IMMEDIATELY provide the optimal, clean, working solution.
            - When the user writes "give code for X in Y" or "solve X", provide the solution in the requested language. Default to C++ if no language is specified.
            - Always write the COMPLETE solution — never truncate or abbreviate.
            - Always pick the most optimal approach (best time and space complexity).
            - After the code, write ONE line explaining the approach and its time/space complexity (e.g. "BFS with visited set. O(V+E) time, O(V) space.").
            - Recognize problem descriptions even if written informally or partially — you understand the intent.
            - Support all languages: C++, Python, Java, JavaScript, Kotlin, Go, Rust, etc.
            - For problems with multiple approaches, always use the optimal one (e.g. prefer two-pointer over brute force, DP over recursion where better).
            - Write production-quality code: proper variable names, edge case handling, and no bugs.

            STRICT RULES:
            - ONLY output code and the one-line explanation. Nothing else. No greetings, no disclaimers.
            - Do NOT write any comments in the code (absolutely no // comments, no /* comments */, no python comments #). Just clean code.
            - Do NOT say "Sure!" or "Here is the solution" or any preamble.
            - Do NOT use markdown code fences (no triple backticks). Just raw code directly.
            - Keep the explanation to exactly one line after the code.
        """.trimIndent()

        else -> """
            You are a helpful, warm, and poetic personal diary assistant. You respond thoughtfully, encouraging the writer to share their reflections.
            RULES:
            - Keep responses brief (1-2 short sentences).
            - Do NOT use markdown, bold, asterisks, or any formatting. Plain text only.
        """.trimIndent()
    }
}

@Composable
fun DiaryScreen(context: Context) {
    val sharedPrefs = remember { context.getSharedPreferences("RiddlePrefs", Context.MODE_PRIVATE) }
    
    // API Configuration state
    var apiProvider by remember { 
        mutableStateOf(sharedPrefs.getString("api_provider", "gemini") ?: "gemini") 
    }
    var apiKey by remember { 
        mutableStateOf(sharedPrefs.getString("api_key", "") ?: "") 
    }
    var apiEndpoint by remember { 
        mutableStateOf(sharedPrefs.getString("api_endpoint", "https://generativelanguage.googleapis.com") ?: "https://generativelanguage.googleapis.com") 
    }
    var apiModel by remember { 
        mutableStateOf(sharedPrefs.getString("api_model", "gemini-2.5-flash") ?: "gemini-2.5-flash") 
    }

    var showConfigDialog by remember { mutableStateOf(apiKey.isEmpty()) }

    var tempApiProvider by remember { mutableStateOf(apiProvider) }
    var tempApiKey by remember { mutableStateOf(apiKey) }
    var tempApiEndpoint by remember { mutableStateOf(apiEndpoint) }
    var tempApiModel by remember { mutableStateOf(apiModel) }

    LaunchedEffect(tempApiProvider) {
        when (tempApiProvider) {
            "gemini" -> {
                tempApiEndpoint = "https://generativelanguage.googleapis.com"
                tempApiModel = "gemini-2.5-flash"
            }
            "openai" -> {
                tempApiEndpoint = "https://api.openai.com/v1/chat/completions"
                tempApiModel = "gpt-4o"
            }
            "claude" -> {
                tempApiEndpoint = "https://api.anthropic.com/v1/messages"
                tempApiModel = "claude-3-5-sonnet-20241022"
            }
            "groq" -> {
                tempApiEndpoint = "https://api.groq.com/openai/v1/chat/completions"
                tempApiModel = "llama-3.2-11b-vision-preview"
            }
        }
    }

    // Character Persona selection state
    var selectedCharacter by remember { 
        mutableStateOf(sharedPrefs.getString("selected_character", "Tom Riddle") ?: "Tom Riddle") 
    }
    var showDropdownMenu by remember { mutableStateOf(false) }

    // Drawing states
    val strokes = remember { mutableStateListOf<DrawingStroke>() }
    var currentPoints = remember { mutableStateListOf<Point>() }
    
    // Canvas dimensions (to generate high quality matching bitmap)
    var canvasWidth by remember { mutableStateOf(0) }
    var canvasHeight by remember { mutableStateOf(0) }

    // Fading animations
    val canvasAlpha = remember { Animatable(1f) }
    val canvasBlur = remember { Animatable(0f) }
    val responseAlpha = remember { Animatable(0f) }

    // AI states
    var isProcessing by remember { mutableStateOf(false) }
    var riddleResponseText by remember { mutableStateOf("") }
    var riddleState by remember { mutableStateOf("hidden") } // "hidden" | "writing" | "visible" | "fading"
    
    val scope = rememberCoroutineScope()
    var autoSubmitJob by remember { mutableStateOf<Job?>(null) }
    var fadeResponseJob by remember { mutableStateOf<Job?>(null) }

    val tool = "pen"
    val disabled = isProcessing
    val thickness = 3f

    // Handle five-finger tap to show config dialog
    val onMultiTouch = { touchCount: Int ->
        if (touchCount >= 5) {
            showConfigDialog = true
        }
    }

    // Helper function to escape JSON strings to prevent payload corruption
    fun escapeJsonString(str: String): String {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t")
    }

    // Helper function to decode Unicode escape sequences (like \u003c for <)
    fun decodeUnicode(input: String): String {
        try {
            val pattern = Pattern.compile("\\\\u([0-9a-fA-F]{4})")
            val matcher = pattern.matcher(input)
            val sb = StringBuffer()
            while (matcher.find()) {
                val hex = matcher.group(1)
                val charCode = hex.toInt(16)
                matcher.appendReplacement(sb, charCode.toChar().toString())
            }
            matcher.appendTail(sb)
            return sb.toString()
        } catch (e: Exception) {
            return input
        }
    }

    // Highlight keywords in code (crimson/red highlighting for keywords and operators)
    fun highlightCode(text: String): AnnotatedString {
        val keywords = setOf(
            "class", "struct", "public", "private", "protected", "void", "int", "double", "float", 
            "char", "string", "bool", "vector", "map", "set", "unordered_map", "unordered_set", 
            "return", "if", "else", "for", "while", "do", "switch", "case", "break", "continue", 
            "const", "auto", "nullptr", "template", "typename", "using", "namespace", "std", "define", "include"
        )
        
        val builder = AnnotatedString.Builder()
        val wordRegex = Regex("([a-zA-Z0-9_]+|[^a-zA-Z0-9_\\s]+|\\s+)")
        val matches = wordRegex.findAll(text)
        
        for (match in matches) {
            val token = match.value
            if (token in keywords) {
                // Style keywords in crimson-rose color
                builder.pushStyle(SpanStyle(color = Color(0xFFC2410C))) 
                builder.append(token)
                builder.pop()
            } else if (token.matches(Regex("[&|^~<>!=+\\-*/%]+"))) {
                // Style operators/symbols in a bold vivid red color
                builder.pushStyle(SpanStyle(color = Color(0xFFE11D48), fontWeight = FontWeight.Bold)) 
                builder.append(token)
                builder.pop()
            } else {
                builder.append(token)
            }
        }
        
        return builder.toAnnotatedString()
    }

    // Direct HTTP call to Gemini/OpenAI/Claude API for handwriting vision & streaming response
    fun queryAI(base64Image: String) {
        if (apiKey.isEmpty()) {
            riddleResponseText = "Bind the diary to a key first."
            riddleState = "visible"
            scope.launch {
                responseAlpha.animateTo(1f, tween(800))
                delay(4000)
                responseAlpha.animateTo(0f, tween(1500))
                riddleState = "hidden"
                riddleResponseText = ""
            }
            return
        }

        isProcessing = true
        riddleResponseText = ""
        riddleState = "writing"

        scope.launch(Dispatchers.IO) {
            try {
                val systemPrompt = getSystemPromptForCharacter(selectedCharacter)
                val escapedSystemPrompt = escapeJsonString(systemPrompt)

                val (url, jsonPayload) = when (apiProvider) {
                    "gemini" -> {
                        val requestUrl = URL("${apiEndpoint.removeSuffix("/")}/v1beta/models/$apiModel:streamGenerateContent?key=$apiKey")
                        val payload = """
                            {
                                "contents": [
                                    {
                                        "parts": [
                                            { "text": "Read the handwritten text in this image and respond to the writer as ${selectedCharacter}." },
                                            { "inlineData": { "mimeType": "image/png", "data": "$base64Image" } }
                                        ]
                                    }
                                ],
                                "systemInstruction": {
                                    "parts": [{ "text": "$escapedSystemPrompt" }]
                                }
                            }
                        """.trimIndent()
                        Pair(requestUrl, payload)
                    }
                    "claude" -> {
                        val requestUrl = URL(apiEndpoint)
                        val payload = """
                            {
                                "model": "$apiModel",
                                "system": "$escapedSystemPrompt",
                                "messages": [
                                    {
                                        "role": "user",
                                        "content": [
                                            { "type": "text", "text": "Read the handwritten text in this image and respond to the writer as ${selectedCharacter}." },
                                            {
                                                "type": "image",
                                                "source": {
                                                    "type": "base64",
                                                    "media_type": "image/png",
                                                    "data": "$base64Image"
                                                }
                                            }
                                        ]
                                    }
                                ],
                                "stream": true,
                                "max_tokens": 1024
                            }
                        """.trimIndent()
                        Pair(requestUrl, payload)
                    }
                    else -> { // openai, groq, custom (OpenAI compatible)
                        val requestUrl = URL(apiEndpoint)
                        val payload = """
                            {
                                "model": "$apiModel",
                                "messages": [
                                    { "role": "system", "content": "$escapedSystemPrompt" },
                                    {
                                        "role": "user",
                                        "content": [
                                            { "type": "text", "text": "Read the handwritten text in this image and respond to the writer as ${selectedCharacter}." },
                                            { "type": "image_url", "image_url": { "url": "data:image/png;base64,$base64Image" } }
                                        ]
                                    }
                                ],
                                "stream": true
                            }
                        """.trimIndent()
                        Pair(requestUrl, payload)
                    }
                }

                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                // Add headers for non-gemini providers
                when (apiProvider) {
                    "openai", "groq", "custom" -> {
                        conn.setRequestProperty("Authorization", "Bearer $apiKey")
                    }
                    "claude" -> {
                        conn.setRequestProperty("x-api-key", apiKey)
                        conn.setRequestProperty("anthropic-version", "2023-06-01")
                    }
                }

                conn.outputStream.use { it.write(jsonPayload.toByteArray()) }

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    var line: String?
                    
                    // Match "text" for Gemini, or "content" for OpenAI/Claude/others
                    val pattern = if (apiProvider == "gemini") {
                        Pattern.compile("\"text\":\\s*\"([^\"]+)\"")
                    } else {
                        Pattern.compile("\"content\":\\s*\"([^\"]+)\"")
                    }

                    withContext(Dispatchers.Main) {
                        responseAlpha.snapTo(0f)
                        responseAlpha.animateTo(1f, tween(500))
                    }

                    while (reader.readLine().also { line = it } != null) {
                        val matcher = pattern.matcher(line ?: "")
                        while (matcher.find()) {
                            var token = matcher.group(1)
                                ?.replace("\\n", "\n")
                                ?.replace("\\\"", "\"")
                                ?.replace("\\\\", "\\") ?: ""
                            token = decodeUnicode(token)
                            
                            // Diary personas: cozy 20ms typewriter. Coder: instant (code is long)
                            val charDelay = if (selectedCharacter == "Coder") 0L else 20L
                            for (char in token) {
                                withContext(Dispatchers.Main) {
                                    riddleResponseText += char
                                }
                                if (charDelay > 0) delay(charDelay)
                            }
                        }
                    }
                    reader.close()
 
                    withContext(Dispatchers.Main) {
                        riddleState = "visible"
                        isProcessing = false
                        // Disable auto-fade for Coder (needs manual clear). Diary personas fade after 5.5s.
                        if (selectedCharacter != "Coder") {
                            fadeResponseJob = launch {
                                delay(5500)
                                riddleState = "fading"
                                responseAlpha.animateTo(0f, tween(1500))
                                riddleState = "hidden"
                                riddleResponseText = ""
                            }
                        }
                    }
                } else {
                    val errorReader = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream))
                    val errorMsg = errorReader.use { it.readText() }
                    withContext(Dispatchers.Main) {
                        riddleResponseText = "The ink blurred..."
                        riddleState = "visible"
                        isProcessing = false
                        delay(4000)
                        responseAlpha.animateTo(0f, tween(1000))
                        riddleState = "hidden"
                        riddleResponseText = ""
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    riddleResponseText = "The connection broke..."
                    riddleState = "visible"
                    isProcessing = false
                    delay(4000)
                    responseAlpha.animateTo(0f, tween(1000))
                    riddleState = "hidden"
                    riddleResponseText = ""
                }
            }
        }
    }

    // Export current Compose canvas to a compressed Base64 PNG image (cropped to drawing bounds)
    fun exportCanvasToBase64(): String? {
        if (strokes.isEmpty() || canvasWidth <= 0 || canvasHeight <= 0) return null

        // 1. Calculate bounding box of all strokes
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        strokes.forEach { stroke ->
            stroke.points.forEach { p ->
                if (p.x < minX) minX = p.x
                if (p.x > maxX) maxX = p.x
                if (p.y < minY) minY = p.y
                if (p.y > maxY) maxY = p.y
            }
        }

        // Add 30px padding for safety
        val padding = 30f
        minX = (minX - padding).coerceAtLeast(0f)
        minY = (minY - padding).coerceAtLeast(0f)
        maxX = (maxX + padding).coerceAtMost(canvasWidth.toFloat())
        maxY = (maxY + padding).coerceAtMost(canvasHeight.toFloat())

        val width = (maxX - minX).toInt()
        val height = (maxY - minY).toInt()

        if (width <= 0 || height <= 0) return null

        // 2. Create small cropped bitmap to speed up compression and network transmission
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Fill canvas with white background for higher OCR accuracy
        canvas.drawColor(android.graphics.Color.WHITE)

        val paint = Paint().apply {
            color = android.graphics.Color.BLACK
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
        }

        strokes.forEach { stroke ->
            if (stroke.points.isEmpty()) return@forEach
            paint.strokeWidth = stroke.width
            val path = Path()
            // Offset coordinates to fit inside the cropped bounds
            path.moveTo(stroke.points[0].x - minX, stroke.points[0].y - minY)
            for (i in 1 until stroke.points.size) {
                val p = stroke.points[i]
                path.lineTo(p.x - minX, p.y - minY)
            }
            canvas.drawPath(path, paint)
        }

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 85, outputStream) // Compact 85% compression
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    // Trigger vector ink fade out and send to Gemini in parallel
    fun submitWriting() {
        if (strokes.isEmpty() || isProcessing) return;

        val base64 = exportCanvasToBase64()
        if (base64 != null) {
            // Trigger API query IMMEDIATELY in parallel to save latency
            queryAI(base64)
        }

        // Run screen fade out concurrently
        scope.launch {
            launch {
                canvasBlur.animateTo(3f, tween(700))
            }
            canvasAlpha.animateTo(0f, tween(900))
            
            // Clear strokes and reset parameters
            strokes.clear()
            canvasAlpha.snapTo(1f)
            canvasBlur.snapTo(0f)
        }
    }

    // Determine status light color based on current persona mode
    val statusColor by animateColorAsState(
        targetValue = when (selectedCharacter) {
            "Tom Riddle" -> Color(0xFFD4AF37)        // Gold
            "Harry Potter" -> Color(0xFFB30000)      // Scarlet
            "Hermione Granger" -> Color(0xFF1E3A8A)  // Blue
            "Ron Weasley" -> Color(0xFFEA580C)       // Orange
            "Coder" -> Color(0xFF00FF88)             // Terminal Green
            else -> Color(0xFF059669)                // Emerald Green
        },
        animationSpec = tween(500)
    )

    // Pulse animation for thinking state
    val breathingAlpha = remember { Animatable(0.3f) }
    LaunchedEffect(isProcessing) {
        if (isProcessing) {
            breathingAlpha.animateTo(
                targetValue = 0.9f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200),
                    repeatMode = RepeatMode.Reverse
                )
            )
        } else {
            breathingAlpha.snapTo(0.3f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(tool) {
                // Monitor touch count to support the 5-finger toggle gesture
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        onMultiTouch(event.changes.size)
                        
                        // Handle pointer events
                        val change = event.changes.first()
                        if (change.pressed && !isProcessing) {
                            autoSubmitJob?.cancel()
                            fadeResponseJob?.cancel()
                        }
                    }
                }
            }
    ) {
        // 1. FULLSCREEN DRAWING CANVAS
        ComposeCanvas(
            modifier = Modifier
                .fillMaxSize()
                .alpha(canvasAlpha.value)
                .blur(canvasBlur.value.dp)
                .pointerInput(tool, disabled) {
                    if (disabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            autoSubmitJob?.cancel()
                            currentPoints.clear()
                            currentPoints.add(Point(offset.x, offset.y, 0.6f))
                        },
                        onDrag = { change, _ ->
                            val pressure = change.pressure.takeIf { it > 0f } ?: 0.6f
                            val offset = change.position
                            currentPoints.add(Point(offset.x, offset.y, pressure))
                            
                            // Redraw current points in real time
                            if (currentPoints.size > 1) {
                                val lastPointsList = currentPoints.toList()
                                if (strokes.isNotEmpty() && strokes.last().id == "active") {
                                    strokes[strokes.lastIndex] = DrawingStroke(
                                        id = "active",
                                        points = lastPointsList,
                                        width = thickness * 2f
                                    )
                                } else {
                                    strokes.add(
                                        DrawingStroke(
                                            id = "active",
                                            points = lastPointsList,
                                            width = thickness * 2f
                                        )
                                    )
                                }
                            }
                        },
                        onDragEnd = {
                            if (currentPoints.isNotEmpty()) {
                                // Finalize completed stroke
                                strokes.removeIf { it.id == "active" }
                                strokes.add(
                                    DrawingStroke(
                                        points = currentPoints.toList(),
                                        width = thickness * 2f
                                    )
                                )
                                currentPoints.clear()
                                
                                // Launch auto submit countdown (0.9 seconds of pen lift)
                                autoSubmitJob = scope.launch {
                                    delay(900)
                                    submitWriting()
                                }
                            }
                        }
                    )
                }
        ) {
            // Read container size
            canvasWidth = size.width.toInt()
            canvasHeight = size.height.toInt()

            // Draw finalized strokes
            strokes.forEach { stroke ->
                if (stroke.points.size < 2) return@forEach
                val path = androidx.compose.ui.graphics.Path()
                path.moveTo(stroke.points[0].x, stroke.points[0].y)
                for (i in 1 until stroke.points.size) {
                    path.lineTo(stroke.points[i].x, stroke.points[i].y)
                }

                drawPath(
                    path = path,
                    color = stroke.color,
                    style = ComposeStroke(
                        width = stroke.width,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        // 2. TOM RIDDLE REPLY (Floating Cursive Text OR Monospace Syntax-Highlighted Code)
        if (riddleResponseText.isNotEmpty()) {
            val isCoder = selectedCharacter == "Coder"
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isCoder) 32.dp else 40.dp)
                    .alpha(responseAlpha.value),
                contentAlignment = if (isCoder) Alignment.TopStart else Alignment.Center
            ) {
                if (isCoder) {
                    val codeLength = riddleResponseText.length
                    val fontSize = when {
                        codeLength > 800 -> 12.sp
                        codeLength > 400 -> 14.sp
                        else -> 16.sp
                    }
                    val lineHeight = when {
                        codeLength > 800 -> 16.sp
                        codeLength > 400 -> 19.sp
                        else -> 22.sp
                    }
                    Text(
                        text = highlightCode(riddleResponseText),
                        color = Color(0xFF222228),
                        fontSize = fontSize,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Start,
                        lineHeight = lineHeight,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 60.dp, bottom = 80.dp) // Provide spacing for top bar and bottom button
                            .verticalScroll(rememberScrollState())
                    )
                } else {
                    Text(
                        text = riddleResponseText,
                        color = Color(0xFF222228),
                        fontSize = 28.sp,
                        fontFamily = FontFamily.Cursive,
                        fontWeight = FontWeight.Medium,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                        lineHeight = 40.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 2.5 CLEAR BUTTON FOR CODER (Only visible when Coder has an active response)
        if (selectedCharacter == "Coder" && riddleResponseText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 40.dp, end = 24.dp)
                    .background(
                        color = Color(0xFF222228),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        scope.launch {
                            responseAlpha.animateTo(0f, tween(800))
                            riddleState = "hidden"
                            riddleResponseText = ""
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CLEAR CODE",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        // 4. MINIMALIST TOP-RIGHT PERSONA SELECTOR (Notewise style)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 40.dp, end = 24.dp)
                .wrapContentSize(Alignment.TopEnd)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .background(
                        color = Color(0x1F222228),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { showDropdownMenu = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                // Status indicator LED (Breathes when thinking/processing)
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .alpha(if (isProcessing) breathingAlpha.value else 0.8f)
                        .background(color = statusColor, shape = CircleShape)
                )
                
                // Character name label
                Text(
                    text = selectedCharacter.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0x99222228),
                    letterSpacing = 1.sp
                )
            }

            DropdownMenu(
                expanded = showDropdownMenu,
                onDismissRequest = { showDropdownMenu = false },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.background(Color(0xFF212126))
            ) {
                val personas = listOf("Tom Riddle", "Harry Potter", "Hermione Granger", "Ron Weasley", "Normal Diary", "Coder")
                personas.forEach { persona ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Mini indicator inside dropdown menu
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            color = when (persona) {
                                                "Tom Riddle" -> Color(0xFFD4AF37)
                                                "Harry Potter" -> Color(0xFFB30000)
                                                "Hermione Granger" -> Color(0xFF1E3A8A)
                                                "Ron Weasley" -> Color(0xFFEA580C)
                                                "Coder" -> Color(0xFF00FF88)
                                                else -> Color(0xFF059669)
                                            },
                                            shape = CircleShape
                                        )
                                )
                                Text(
                                    text = persona,
                                    color = if (selectedCharacter == persona) Color.White else Color(0xAAFFFFFF),
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedCharacter == persona) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        },
                        onClick = {
                            selectedCharacter = persona
                            sharedPrefs.edit().putString("selected_character", persona).apply()
                            showDropdownMenu = false
                        }
                    )
                }
            }
        }

        // 3. API CONFIG DIALOG (Shows on 1st launch, or triggers via 5-finger tap)
        if (showConfigDialog) {
            Dialog(onDismissRequest = { if (apiKey.isNotEmpty()) showConfigDialog = false }) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Bind the Horcrux",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Choose your API provider and supply the configuration. A 5-finger tap returns you here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Custom Styled Provider Selector
                        Text(
                            text = "PROVIDER",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0x99222228),
                            letterSpacing = 1.sp,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val providers = listOf("gemini", "openai", "claude", "groq", "custom")
                            providers.forEach { provider ->
                                val isSelected = tempApiProvider == provider
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            color = if (isSelected) Color(0xFF222228) else Color(0x0F222228),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { tempApiProvider = provider }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = provider.uppercase(),
                                        color = if (isSelected) Color.White else Color(0xFF222228),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        OutlinedTextField(
                            value = tempApiEndpoint,
                            onValueChange = { tempApiEndpoint = it },
                            label = { Text("API Endpoint Base URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = tempApiKey,
                            onValueChange = { tempApiKey = it },
                            label = { Text("API Key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = tempApiModel,
                            onValueChange = { tempApiModel = it },
                            label = { Text("Model Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = { 
                                if (apiKey.isNotEmpty()) {
                                    showConfigDialog = false 
                                }
                            }) {
                                Text("Cancel")
                            }
                            Button(onClick = {
                                if (tempApiKey.isNotEmpty()) {
                                    apiKey = tempApiKey
                                    apiProvider = tempApiProvider
                                    apiEndpoint = tempApiEndpoint
                                    apiModel = tempApiModel
                                    
                                    sharedPrefs.edit()
                                        .putString("api_key", tempApiKey)
                                        .putString("api_provider", tempApiProvider)
                                        .putString("api_endpoint", tempApiEndpoint)
                                        .putString("api_model", tempApiModel)
                                        .apply()

                                    showConfigDialog = false
                                }
                            }) {
                                Text("Save Config")
                            }
                        }
                    }
                }
            }
        }
    }
}
