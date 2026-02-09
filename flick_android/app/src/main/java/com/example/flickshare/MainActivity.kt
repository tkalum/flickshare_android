package com.example.flickshare

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flickshare.network.AnnounceManager
import com.example.flickshare.network.DiscoveryManager
import com.example.flickshare.ui.theme.FlickShareTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var multicastLock: WifiManager.MulticastLock? = null

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("flickshare_lock").apply {
            setReferenceCounted(true)
            acquire()
        }

        setContent {
            FlickShareTheme(darkTheme = true) {
                val configuration = LocalConfiguration.current
                val screenHeight = configuration.screenHeightDp.dp
                val viewingAreaHeight = screenHeight * 0.30f // Slightly smaller for better balance

                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
                val collapsedFraction = scrollBehavior.state.collapsedFraction

                // Pager State for Swipe (0 = Receive, 1 = Send)
                val pagerState = rememberPagerState(initialPage = 1) { 2 }
                val scope = rememberCoroutineScope()

                var statusText by remember { mutableStateOf("") }
                var isActionActive by remember { mutableStateOf(false) }
                val deviceName = android.os.Build.MODEL

                val discoveryManager = remember { DiscoveryManager(this, { statusText = it }, { isActionActive = false }) }
                val announceManager = remember { AnnounceManager(this, { statusText = it }, { isActionActive = false }) }

                val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    if (result.resultCode == RESULT_OK) {
                        result.data?.data?.let { uri ->
                            isActionActive = true
                            startSendingFile(uri, announceManager) { statusText = it }
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        Surface(
                            modifier = Modifier.clipToBounds(),
                            shadowElevation = lerp(0.dp, 4.dp, collapsedFraction),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            LargeTopAppBar(
                                title = {
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "FlickShare",
                                            fontSize = lerp(30.sp, 20.sp, collapsedFraction),
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .padding(bottom = lerp(12.dp, 0.dp, collapsedFraction))
                                                .align(if (collapsedFraction > 0.7f) Alignment.CenterStart else Alignment.Center)
                                        )
                                    }
                                },
                                expandedHeight = viewingAreaHeight,
                                scrollBehavior = scrollBehavior,
                                colors = TopAppBarDefaults.largeTopAppBarColors(
                                    containerColor = Color.Transparent,
                                    scrolledContainerColor = Color.Transparent
                                )
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        // HorizontalPager handles the Swipe Left/Right logic
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = !isActionActive // Disable swipe when transferring
                        ) { page ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(Modifier.height(innerPadding.calculateTopPadding() + 20.dp))

                                AssistChip(
                                    onClick = { },
                                    label = { Text(deviceName) },
                                    leadingIcon = { Icon(Icons.Default.Devices, null, Modifier.size(18.dp)) },
                                    shape = CircleShape
                                )

                                Spacer(Modifier.height(32.dp))

                                // Action Card
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(32.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        if (!isActionActive) {
                                            Icon(
                                                if (page == 1) Icons.Default.FileUpload else Icons.Default.FileDownload,
                                                null, modifier = Modifier.size(64.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.height(16.dp))
                                            Text(
                                                if (page == 1) "Send Files" else "Receive Files",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 24.sp
                                            )
                                            Spacer(Modifier.height(32.dp))
                                            Button(
                                                onClick = {
                                                    if (page == 1) {
                                                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                                                        filePicker.launch(intent)
                                                    } else {
                                                        isActionActive = true
                                                        discoveryManager.start()
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                                shape = CircleShape
                                            ) {
                                                Text(if (page == 1) "Choose file" else "Catch file")
                                            }
                                        } else {
                                            CircularProgressIndicator(strokeCap = StrokeCap.Round)
                                            Text(statusText, modifier = Modifier.padding(top = 16.dp))
                                            TextButton(onClick = { isActionActive = false }) {
                                                Text("Cancel", color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                                // Spacer to allow scrolling
                                Spacer(modifier = Modifier.height(600.dp))
                            }
                        }

                        // Bottom Navigation Pill with smooth indicator
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 32.dp, start = 60.dp, end = 60.dp)
                                .height(64.dp)
                                .fillMaxWidth(),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shadowElevation = 12.dp
                        ) {
                            Row(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                                listOf("Receive", "Send").forEachIndexed { index, label ->
                                    val isSelected = pagerState.currentPage == index

                                    // Smooth color transition based on pager state
                                    val containerColor by animateColorAsState(
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        label = "TabColor"
                                    )
                                    val contentColor by animateColorAsState(
                                        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        label = "TabContent"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(containerColor, CircleShape)
                                            .clickable {
                                                scope.launch { pagerState.animateScrollToPage(index) }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(label, fontWeight = FontWeight.Bold, color = contentColor)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startSendingFile(uri: Uri, announceManager: AnnounceManager, updateStatus: (String) -> Unit) {
        val fileName = getFileName(uri)
        val pfd = contentResolver.openFileDescriptor(uri, "r")
        pfd?.let { announceManager.start(fileName, 24242, it.detachFd()) }
    }

    private fun getFileName(uri: Uri): String {
        var name = "file"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                name = cursor.getString(index)
            }
        }
        return name
    }

    private fun lerp(start: TextUnit, stop: TextUnit, fraction: Float): TextUnit = (start.value + (stop.value - start.value) * fraction).sp
    private fun lerp(start: Dp, stop: Dp, fraction: Float): Dp = start + (stop - start) * fraction

    override fun onDestroy() {
        super.onDestroy()
        if (multicastLock?.isHeld == true) multicastLock?.release()
    }
}