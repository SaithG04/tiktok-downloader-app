package com.quiroga.tiktokdownloader

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quiroga.tiktokdownloader.ui.theme.TikTokDownloaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import java.io.IOException

data class VideoSource(
    val index: Int,
    val url: String
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inicializa la UI con el enlace si existe
        setContent {
            TikTokDownloaderTheme {
                TikTokDownloaderScreen(getTikTokUrlFromIntent())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Recupera la URL del portapapeles si la actividad vuelve al primer plano
        setContent {
            TikTokDownloaderTheme {
                TikTokDownloaderScreen(getTikTokUrlFromIntent())
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Actualiza la URL con el nuevo enlace de TikTok cuando la actividad ya está abierta
        setContent {
            TikTokDownloaderTheme {
                TikTokDownloaderScreen(getTikTokUrlFromIntent(intent))
            }
        }
    }

    private fun getTikTokUrlFromIntent(intent: Intent? = this.intent): String? {
        val tiktokUrl = intent?.data?.toString()
        return tiktokUrl
    }
}

@Composable
fun TikTokDownloaderScreen(tiktokUrl: String?) {
    // Estados para el formulario y resultados
    var url by remember { mutableStateOf(tiktokUrl ?: "") }
    var output by remember { mutableStateOf("") }
    var sources by remember { mutableStateOf<List<VideoSource>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isSearchCompleted by remember { mutableStateOf(false) }
    var isFormVisible by remember { mutableStateOf(true) }
    var isTitleVisible by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Usando el color rojo
    val redColor = Color(0xFFFF0000) // Rojo puro

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center // Esto centra todo el contenido en la pantalla
    ) {
        Column(
            modifier = Modifier
                .wrapContentHeight() // Esto permite que la Column ocupe solo el espacio necesario
                .verticalScroll(rememberScrollState()), // Para permitir desplazamiento
            horizontalAlignment = Alignment.CenterHorizontally // Centrado horizontal
        ) {
            if (isTitleVisible) {
                Text(
                    text = "Descargar video de TikTok",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        color = redColor, // Color rojo
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (isFormVisible) {

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = {
                        Text(
                            "Ingresa la URL de TikTok",
                            fontFamily = FontFamily.SansSerif,
                            color = Color.Gray
                        )
                    },
                    placeholder = {
                        Text("https://www.tiktok.com", fontFamily = FontFamily.SansSerif)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    textStyle = TextStyle(color = Color.Black),
                )
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipboardText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                        if (clipboardText != null && clipboardText.contains("tiktok.com")) {
                            url = clipboardText
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = redColor) // Botón rojo
                ) {
                    Text("Pegar del portapapeles", fontFamily = FontFamily.SansSerif, color = Color.White)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (!url.trim().startsWith("https://") && !url.trim().startsWith("http://")) {
                            output = "Por favor, ingresa una URL válida de TikTok."
                            return@Button
                        }
                        // Inicia la búsqueda y peticion al backend
                        isLoading = true
                        isSearchCompleted = false

                        coroutineScope.launch {
                            try{
                                val result = fetchVideoSources(context, url.trim())
                                if (result.sources.isNotEmpty()) {
                                    sources = result.sources
                                } else {
                                    output = "No se encontraron fuentes de video disponibles."
                                }
                                isLoading = false
                                isSearchCompleted = true
                                isFormVisible = false
                                isTitleVisible = false
                            }catch (e: Exception){
                                isLoading = false
                                Log.e("Exception", "Error: ${e.message}")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = redColor) // Botón rojo
                ) {
                    Text("Descargar video", fontFamily = FontFamily.SansSerif, color = Color.White)
                }
            }

            if (isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }

            if (output.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = output, fontFamily = FontFamily.SansSerif, color = redColor)
            }

            if (sources.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Fuentes de video disponibles:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                sources.forEachIndexed { index, source ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Fuente ${index + 1} (${extractQuality(source.url)})", fontFamily = FontFamily.SansSerif)
                        Button(
                            onClick = {
                                // Aquí se abre la URL en el navegador o se descarga el video
                                val intent = Intent(Intent.ACTION_VIEW, source.url.toUri())
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = redColor) // Botón rojo
                        ) {
                            Text(text = "Descargar video ${index + 1}", fontFamily = FontFamily.SansSerif, color = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (isSearchCompleted) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        // Resetear el estado para una nueva búsqueda
                        isSearchCompleted = false
                        url = ""
                        output = ""
                        sources = emptyList()
                        isFormVisible = true
                        isTitleVisible = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = redColor) // Botón rojo
                ) {
                    Text("Buscar un nuevo video", fontFamily = FontFamily.SansSerif, color = Color.White)
                }
            }
        }
    }
}

data class DownloadResponse(
    val sources: List<VideoSource>
)

// Función suspendida para hacer la petición al backend y parsear la respuesta JSON
suspend fun fetchVideoSources(context: Context, tiktokUrl: String): DownloadResponse = withContext(Dispatchers.IO){
    val backendUrl = context.getString(R.string.backend_url)
    val endPoint = context.getString(R.string.end_point)
    val client = OkHttpClient()

    val json = "{\"url\":\"$tiktokUrl\"}"
    val mediaType = "application/json; charset=utf-8".toMediaType()
    val requestBody = json.toRequestBody(mediaType)

    val request = Request.Builder()
        .url("$backendUrl/$endPoint")
        .post(requestBody)
        .build()

    Log.d("Request", request.toString())
    Log.d("RequestBody", json)

    val response = client.newCall(request).execute()
    
    if (response.isSuccessful) {
        val responseBody = response.body?.string() ?: "{}"
        // Verificar si la respuesta es HTML
        if (responseBody.startsWith("<!DOCTYPE")) {
            Log.d("Response", responseBody.substring(0, 100))
            throw JSONException("La respuesta es HTML en lugar de JSON")
        }

        // Procesar la respuesta JSON
        val responseJson = JSONObject(responseBody)
        val data = responseJson.optJSONObject("data")
        val sourcesArray = data?.optJSONArray("sources")
        val sourceList = mutableListOf<VideoSource>()

        if (sourcesArray != null) {
            for (i in 0 until sourcesArray.length()) {
                val sourceObj = sourcesArray.getJSONObject(i)
                val index = sourceObj.optInt("index")
                val sourceUrl = sourceObj.optString("url")
                if (sourceUrl.isNotEmpty()) {
                    sourceList.add(VideoSource(index, sourceUrl))
                }
            }
        }
        return@withContext DownloadResponse(sourceList)
    } else {
        throw IOException("Error en la solicitud HTTP: ${response.code}")
    }
}

fun extractQuality(url: String): String {
    val regex = "br=(\\d+)".toRegex()
    val match = regex.find(url)
    return if (match != null) {
        val bitrate = match.groupValues[1].toIntOrNull() ?: 0
        if (bitrate > 1000) "${bitrate / 1000} kbps" else "Desconocida"
    } else {
        "Desconocida"
    }
}

fun String.toUri(): Uri = Uri.parse(this)
