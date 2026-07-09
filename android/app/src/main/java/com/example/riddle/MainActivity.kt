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
    
    // API key configuration state
    var apiKey by remember { mutableStateOf(sharedPrefs.getString("api_key", "") ?: "") }
    var showConfigDialog by remember { mutableStateOf(apiKey.isEmpty()) }
    var tempApiKey by remember { mutableStateOf(apiKey) }

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

    // Direct HTTP call to Gemini API for handwriting vision & streaming response
    fun queryGemini(base64Image: String) {
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
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:streamGenerateContent?key=$apiKey")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val systemPrompt = getSystemPromptForCharacter(selectedCharacter)

                val jsonPayload = """
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
                            "parts": [{ "text": "$systemPrompt" }]
                        }
                    }
                """.trimIndent()

                conn.outputStream.use { it.write(jsonPayload.toByteArray()) }

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    var line: String?
                    val pattern = Pattern.compile("\"text\":\\s*\"([^\"]+)\"")

                    withContext(Dispatchers.Main) {
                        responseAlpha.snapTo(0f)
                        responseAlpha.animateTo(1f, tween(500))
                    }

                    while (reader.readLine().also { line = it } != null) {
                        val matcher = pattern.matcher(line ?: "")
                        while (matcher.find()) {
                            val token = matcher.group(1)
                                ?.replace("\\n", "\n")
                                ?.replace("\\\"", "\"")
                                ?.replace("\\\\", "\\") ?: ""
                            
                            // Animate character-by-character stream
                            for (char in token) {
                                withContext(Dispatchers.Main) {
                                    riddleResponseText += char
                                }
                                delay(20) // Character typewriting effect
                            }
                        }
                    }
                    reader.close()

                    withContext(Dispatchers.Main) {
                        riddleState = "visible"
                        isProcessing = false
                        // Set auto-fade timer for response text
                        fadeResponseJob = launch {
                            delay(5500)
                            riddleState = "fading"
                            responseAlpha.animateTo(0f, tween(1500))
                            riddleState = "hidden"
                            riddleResponseText = ""
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
            queryGemini(base64)
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
            "Ron Weasley" -> Color(0xFFEA580C)      // Orange
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

        // 2. TOM RIDDLE REPLY (Floating Cursive Text)
        if (riddleResponseText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp)
                    .alpha(responseAlpha.value),
                contentAlignment = Alignment.Center
            ) {
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
                val personas = listOf("Tom Riddle", "Harry Potter", "Hermione Granger", "Ron Weasley", "Normal Diary")
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

        // 3. API KEY CONFIG DIALOG (Shows on 1st launch, or triggers via 5-finger tap)
        if (showConfigDialog) {
            Dialog(onDismissRequest = { if (apiKey.isNotEmpty()) showConfigDialog = false }) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Bind the Horcrux",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Provide your Gemini API Key. A 5-finger tap on the blank screen will return you here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        OutlinedTextField(
                            value = tempApiKey,
                            onValueChange = { tempApiKey = it },
                            label = { Text("Gemini API Key") },
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
                                    sharedPrefs.edit().putString("api_key", tempApiKey).apply()
                                    showConfigDialog = false
                                }
                            }) {
                                Text("Save Key")
                            }
                        }
                    }
                }
            }
        }
    }
}
