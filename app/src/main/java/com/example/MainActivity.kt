package com.example

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

// Navigation tabs
enum class ScreenTab {
    HOME, BROWSER, ABOUT
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainResponsiveScreen()
            }
        }
    }
}

// Connectivity utility
@Composable
fun rememberConnectivityState(): State<Boolean> {
    val context = LocalContext.current
    val connectivityManager = remember {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    val isConnected = remember {
        val initialValue = connectivityManager.activeNetwork?.let { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } ?: false
        mutableStateOf(initialValue)
    }

    DisposableEffect(connectivityManager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isConnected.value = true
            }
            override fun onLost(network: Network) {
                isConnected.value = false
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        onDispose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
    return isConnected
}

// Active connection latency ping helper
suspend fun checkNetworkLatency(): Long {
    return withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val url = URL("https://www.google.com")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 3500
            connection.readTimeout = 3500
            connection.requestMethod = "HEAD"
            connection.connect()
            val code = connection.responseCode
            connection.disconnect()
            if (code in 200..399) {
                System.currentTimeMillis() - startTime
            } else {
                -1L
            }
        } catch (e: Exception) {
            -1L
        }
    }
}

@Composable
fun MainResponsiveScreen() {
    var selectedTab by remember { mutableStateOf(ScreenTab.HOME) }
    var currentBrowseUrl by remember { mutableStateOf("https://aistudio.google.com/") }
    val isOnline by rememberConnectivityState()
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var pageLoadProgress by remember { mutableIntStateOf(0) }
    
    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .testTag("root_scaffold")
    ) {
        val isTablet = maxWidth >= 600.dp

        // Full Bleed background image
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.img_fox_background_1779449330142),
                contentDescription = "Background Fox Illustration",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Beautiful dark frosted glass background gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0F172A).copy(alpha = 0.55f),
                                Color(0xFF0F172A).copy(alpha = 0.88f),
                                Color(0xFF0B192C).copy(alpha = 0.98f)
                            )
                        )
                    )
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!isTablet) {
                    BottomNavigationBarComponent(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTablet) {
                    NavigationRailComponent(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                    VerticalDivider(color = Color.White.copy(alpha = 0.12f), thickness = 1.dp)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "MainContentTransition"
                    ) { targetTab ->
                        when (targetTab) {
                            ScreenTab.HOME -> HomeScreen(
                                isOnline = isOnline,
                                onNavigateToUrl = { url ->
                                    currentBrowseUrl = url
                                    selectedTab = ScreenTab.BROWSER
                                }
                            )
                            ScreenTab.BROWSER -> BrowserScreen(
                                url = currentBrowseUrl,
                                onUrlChange = { currentBrowseUrl = it },
                                progress = pageLoadProgress,
                                onProgressChange = { pageLoadProgress = it },
                                onWebViewCreated = { webViewInstance = it },
                                webViewInstance = webViewInstance
                            )
                            ScreenTab.ABOUT -> AboutScreen(
                                isOnline = isOnline
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBarComponent(
    selectedTab: ScreenTab,
    onTabSelected: (ScreenTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.testTag("navigation_bottom_bar"),
        containerColor = Color(0xFF0F172A).copy(alpha = 0.95f),
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            modifier = Modifier.testTag("nav_home_tab"),
            icon = { Icon(Icons.Default.Home, contentDescription = "Início") },
            label = { Text(stringResource(id = R.string.nav_home), fontWeight = FontWeight.Bold) },
            selected = selectedTab == ScreenTab.HOME,
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.Black,
                selectedTextColor = Color(0xFFF97316),
                indicatorColor = Color(0xFFF97316),
                unselectedIconColor = Color.White.copy(alpha = 0.6f),
                unselectedTextColor = Color.White.copy(alpha = 0.6f)
            ),
            onClick = { onTabSelected(ScreenTab.HOME) }
        )
        NavigationBarItem(
            modifier = Modifier.testTag("nav_browser_tab"),
            icon = { Icon(Icons.Default.Search, contentDescription = "Navegador") },
            label = { Text("Navegador", fontWeight = FontWeight.Bold) },
            selected = selectedTab == ScreenTab.BROWSER,
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.Black,
                selectedTextColor = Color(0xFFF97316),
                indicatorColor = Color(0xFFF97316),
                unselectedIconColor = Color.White.copy(alpha = 0.6f),
                unselectedTextColor = Color.White.copy(alpha = 0.6f)
            ),
            onClick = { onTabSelected(ScreenTab.BROWSER) }
        )
        NavigationBarItem(
            modifier = Modifier.testTag("nav_about_tab"),
            icon = { Icon(Icons.Default.Info, contentDescription = "Sobre") },
            label = { Text(stringResource(id = R.string.nav_about), fontWeight = FontWeight.Bold) },
            selected = selectedTab == ScreenTab.ABOUT,
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.Black,
                selectedTextColor = Color(0xFFF97316),
                indicatorColor = Color(0xFFF97316),
                unselectedIconColor = Color.White.copy(alpha = 0.6f),
                unselectedTextColor = Color.White.copy(alpha = 0.6f)
            ),
            onClick = { onTabSelected(ScreenTab.ABOUT) }
        )
    }
}

@Composable
fun NavigationRailComponent(
    selectedTab: ScreenTab,
    onTabSelected: (ScreenTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier.testTag("navigation_rail"),
        containerColor = Color(0xFF0F172A).copy(alpha = 0.95f),
        header = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_fox_icon_1779449356068),
                    contentDescription = "Fox Logo",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Color(0xFFF97316), CircleShape)
                )
            }
        }
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        NavigationRailItem(
            modifier = Modifier.testTag("nav_rail_home"),
            icon = { Icon(Icons.Default.Home, contentDescription = "Início") },
            label = { Text(stringResource(id = R.string.nav_home), fontWeight = FontWeight.Bold) },
            selected = selectedTab == ScreenTab.HOME,
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = Color.Black,
                selectedTextColor = Color(0xFFF97316),
                indicatorColor = Color(0xFFF97316),
                unselectedIconColor = Color.White.copy(alpha = 0.6f),
                unselectedTextColor = Color.White.copy(alpha = 0.6f)
            ),
            onClick = { onTabSelected(ScreenTab.HOME) }
        )
        Spacer(modifier = Modifier.height(12.dp))
        NavigationRailItem(
            modifier = Modifier.testTag("nav_rail_browser"),
            icon = { Icon(Icons.Default.Search, contentDescription = "Navegador") },
            label = { Text("Navega", fontWeight = FontWeight.Bold) },
            selected = selectedTab == ScreenTab.BROWSER,
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = Color.Black,
                selectedTextColor = Color(0xFFF97316),
                indicatorColor = Color(0xFFF97316),
                unselectedIconColor = Color.White.copy(alpha = 0.6f),
                unselectedTextColor = Color.White.copy(alpha = 0.6f)
            ),
            onClick = { onTabSelected(ScreenTab.BROWSER) }
        )
        Spacer(modifier = Modifier.height(12.dp))
        NavigationRailItem(
            modifier = Modifier.testTag("nav_rail_about"),
            icon = { Icon(Icons.Default.Info, contentDescription = "Sobre") },
            label = { Text(stringResource(id = R.string.nav_about), fontWeight = FontWeight.Bold) },
            selected = selectedTab == ScreenTab.ABOUT,
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = Color.Black,
                selectedTextColor = Color(0xFFF97316),
                indicatorColor = Color(0xFFF97316),
                unselectedIconColor = Color.White.copy(alpha = 0.6f),
                unselectedTextColor = Color.White.copy(alpha = 0.6f)
            ),
            onClick = { onTabSelected(ScreenTab.ABOUT) }
        )
    }
}

@Composable
fun HomeScreen(
    isOnline: Boolean,
    onNavigateToUrl: (String) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Fox branding hero block
        Box(
            modifier = Modifier
                .padding(bottom = 16.dp)
                .size(110.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1E293B).copy(alpha = 0.6f))
                .border(2.dp, Color(0xFFF97316), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_fox_icon_1779449356068),
                contentDescription = "Fox Icon",
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        }

        Text(
            text = "Fox Navigator",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Connectivity bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(bottom = 24.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(
                    if (isOnline) Color(0xFF10B981).copy(alpha = 0.15f)
                    else Color(0xFFF59E0B).copy(alpha = 0.15f)
                )
                .border(
                    width = 1.dp,
                    color = if (isOnline) Color(0xFF10B981).copy(alpha = 0.4f)
                    else Color(0xFFF59E0B).copy(alpha = 0.4f),
                    shape = RoundedCornerShape(100.dp)
                )
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) Color(0xFF10B981) else Color(0xFFF59E0B))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isOnline) "Conexão de Internet Ativa" else "Sem Conexão Física",
                color = if (isOnline) Color(0xFF34D399) else Color(0xFFFBBF24),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Test purpose card description
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E293B).copy(alpha = 0.85f)
            ),
            border = CardStrokeHelper.subtleStroke()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = stringResource(id = R.string.about_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = stringResource(id = R.string.app_description),
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Start
                )
            }
        }

        // 2 Core buttons requested
        Text(
            text = "NAVEGAÇÃO RÁPIDA",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.5f),
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Button(
            onClick = { onNavigateToUrl("https://aistudio.google.com/") },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(bottom = 12.dp)
                .testTag("btn_ai_studio"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316))
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(id = R.string.btn_ai_studio),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Button(
            onClick = { onNavigateToUrl("https://github.com/") },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(bottom = 24.dp)
                .testTag("btn_github"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
            border = CardStrokeHelper.subtleStroke(Color(0xFF475569))
        ) {
            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(id = R.string.btn_github),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // External browser quick trigger links
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/"))
                    context.startActivity(intent)
                }
                .padding(8.dp)
                .testTag("btn_external_browser")
        ) {
            Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFFF97316), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Abrir AI Studio Externo",
                color = Color(0xFFF97316),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun BrowserScreen(
    url: String,
    onUrlChange: (String) -> Unit,
    progress: Int,
    onProgressChange: (Int) -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    webViewInstance: WebView?
) {
    val context = LocalContext.current
    var inputUrl by remember(url) { mutableStateOf(url) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Browser Controls
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp),
            border = CardStrokeHelper.subtleStroke()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (webViewInstance?.canGoBack() == true) {
                                webViewInstance.goBack()
                            }
                        },
                        enabled = webViewInstance?.canGoBack() == true
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Voltar",
                            tint = if (webViewInstance?.canGoBack() == true) Color.White else Color.White.copy(alpha = 0.3f)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (webViewInstance?.canGoForward() == true) {
                                webViewInstance.goForward()
                            }
                        },
                        enabled = webViewInstance?.canGoForward() == true
                    ) {
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = "Avançar",
                            tint = if (webViewInstance?.canGoForward() == true) Color.White else Color.White.copy(alpha = 0.3f)
                        )
                    }

                    IconButton(
                        onClick = { webViewInstance?.reload() }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Recarregar", tint = Color.White)
                    }

                    // Simple address bar string visualizer
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(100.dp))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = url.removePrefix("https://").removePrefix("www."),
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Abrir no Navegador Externo", tint = Color(0xFFF97316))
                    }
                }

                // Progress Indicator
                if (progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = Color(0xFFF97316),
                        trackColor = Color.Transparent,
                    )
                } else {
                    Spacer(modifier = Modifier.height(3.dp))
                }
            }
        }

        // Active WebView view container
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.White)
                .testTag("webview_component")
        ) {
            WebViewContainer(
                url = url,
                onUrlChange = onUrlChange,
                onProgressChange = onProgressChange,
                onWebViewCreated = onWebViewCreated,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun WebViewContainer(
    url: String,
    onUrlChange: (String) -> Unit,
    onProgressChange: (Int) -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                    finishedUrl?.let { onUrlChange(it) }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    onProgressChange(newProgress)
                }
            }
            onWebViewCreated(this)
        }
    }

    LaunchedEffect(url) {
        if (webView.url != url) {
            webView.loadUrl(url)
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
    )
}

@Composable
fun AboutScreen(isOnline: Boolean) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var pingResult by remember { mutableStateOf<String>("Não testado") }
    var isPinging by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Configurações e Testes",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 18.dp, top = 8.dp)
        )

        // Test Purpose Detailed Info
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.85f)),
            shape = RoundedCornerShape(16.dp),
            border = CardStrokeHelper.subtleStroke()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFF97316))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Objetivo do Aplicativo",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Este software serve como ambiente experimental e teste de utilização integrado ao AI Studio Build. Ele consome dados vivos da rede e valida componentes reativos, compatibilidade adaptável em tempo de execução Android e acesso à infraestrutura local por meio do Android Manifest.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    lineHeight = 20.sp
                )
            }
        }

        // Live Internet Probe Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.85f)),
            shape = RoundedCornerShape(16.dp),
            border = CardStrokeHelper.subtleStroke()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = if (isOnline) Color(0xFF10B981) else Color(0xFFF59E0B)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Acesso à Rede & Ping Latência",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Status: " + (if (isOnline) "ONLINE" else "OFFLINE"),
                    color = if (isOnline) Color(0xFF34D399) else Color(0xFFFBBF24),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Latência (google.com): $pingResult",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (isOnline) {
                            isPinging = true
                            pingResult = "Medindo..."
                            coroutineScope.launch {
                                val delayTime = checkNetworkLatency()
                                pingResult = if (delayTime >= 0) "$delayTime ms" else "Falhou a resposta"
                                isPinging = false
                            }
                        } else {
                            pingResult = "Sem Internet para testar"
                        }
                    },
                    enabled = isOnline && !isPinging,
                    modifier = Modifier.fillMaxWidth().testTag("btn_test_latency"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316))
                ) {
                    Text("Pinger Latência", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Project Metadata Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.85f)),
            shape = RoundedCornerShape(16.dp),
            border = CardStrokeHelper.subtleStroke()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Dados Adicionais",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Desenvolvedor:", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    Text("rafael.silva@aluno.fmpsc.edu.br", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Plataforma:", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    Text("AI Studio Build Android", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Estado do Launcher:", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    Text("Personalizado com Raposa", color = Color(0xFFF97316), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

object CardStrokeHelper {
    @Composable
    fun subtleStroke(color: Color = Color.White.copy(alpha = 0.12f)) = BorderStroke(1.dp, color)
}

