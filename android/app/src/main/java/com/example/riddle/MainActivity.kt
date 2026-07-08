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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
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

@Composable
fun DiaryScreen(context: Context) {
    val sharedPrefs = remember { context.getSharedPreferences("RiddlePrefs", Context.MODE_PRIVATE) }
    
    // API key configuration state
    var apiKey by remember { mutableStateOf(sharedPrefs.getString("api_key", "") ?: "") }
    var showConfigDialog by remember { mutableStateOf(apiKey.isEmpty()) }
    var tempApiKey by remember { mutableStateOf(apiKey) }

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

                val systemPrompt = """
                    You are the diary of Tom Marvolo Riddle. You are a sentient object containing a fragment of the soul of Tom Riddle at age 16.
                    You are charming, manipulative, deeply intelligent, and curious.
                    
                    RULES:
                    - Keep responses brief (1-2 short sentences).
                    - Be mysterious.
                    - Never break character. You are a sentient diary, not an AI.
                    - Do NOT use markdown, bold, asterisks, or any formatting. Plain text only.
                """.trimIndent()

                val jsonPayload = """
                    {
                        "contents": [
                            {
                                "parts": [
                                    { "text": "Read the handwritten text in this image and respond to the writer as Tom Riddle." },
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
                        // Set auto-fade timer for Tom's response
                        fadeResponseJob = launch {
                            delay(5000)
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
                    riddleResponseText = "Tom is sleeping..."
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

    // Export current Compose canvas to a compressed Base64 PNG image
    fun exportCanvasToBase64(): String? {
        if (strokes.isEmpty() || canvasWidth <= 0 || canvasHeight <= 0) return null

        val bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
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
            path.moveTo(stroke.points[0].x, stroke.points[0].y)
            for (i in 1 until stroke.points.size) {
                val p = stroke.points[i]
                path.lineTo(p.x, p.y)
            }
            canvas.drawPath(path, paint)
        }

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    // Trigger vector ink fade out and send to Gemini
    fun submitWriting() {
        if (strokes.isEmpty() || isProcessing) return;

        scope.launch {
            val base64 = exportCanvasToBase64()
            
            // Animate canvas stroke dissolve
            launch {
                canvasBlur.animateTo(3f, tween(1000))
            }
            canvasAlpha.animateTo(0f, tween(1200))
            
            // Clear strokes and reset animation parameters
            strokes.clear()
            canvasAlpha.snapTo(1f)
            canvasBlur.snapTo(0f)

            if (base64 != null) {
                queryGemini(base64)
            }
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
                                
                                // Launch auto submit countdown (1.6 seconds of pen lift)
                                autoSubmitJob = scope.launch {
                                    delay(1600)
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
