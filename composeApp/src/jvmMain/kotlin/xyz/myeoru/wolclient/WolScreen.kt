package xyz.myeoru.wolclient

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.*

@Composable
fun WolScreen(window: ComposeWindow) {
    var macAddress by remember { mutableStateOf("") }
    var ipAddress by remember { mutableStateOf("255.255.255.255") }
    var statusMessage by remember { mutableStateOf("준비됨") }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🖥️ Wake On LAN", style = MaterialTheme.typography.h5)

        Spacer(Modifier.height(30.dp))

        // --- 입력 필드 ---
        OutlinedTextField(
            value = macAddress,
            onValueChange = { macAddress = it },
            label = { Text("MAC Address") },
            placeholder = { Text("예: AA:BB:CC:DD:EE:FF") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = ipAddress,
            onValueChange = { ipAddress = it },
            label = { Text("IP Address") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(20.dp))

        // --- 파일 저장/불러오기 버튼 영역 ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // [불러오기 버튼]
            Button(
                onClick = {
                    val file = openFileDialog(window, FileDialog.LOAD)
                    if (file != null) {
                        val (loadedMac, loadedIp) = loadConfigFromFile(file)
                        macAddress = loadedMac
                        ipAddress = loadedIp
                        statusMessage = "설정 불러오기 성공: ${file.name}"
                    }
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.secondary)
            ) {
                Text("📂 설정 불러오기")
            }

            // [저장하기 버튼]
            Button(
                onClick = {
                    val file = openFileDialog(window, FileDialog.SAVE)
                    if (file != null) {
                        saveConfigToFile(file, macAddress, ipAddress)
                        statusMessage = "설정 저장 완료: ${file.name}"
                    }
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.secondary)
            ) {
                Text("💾 설정 저장하기")
            }
        }

        Spacer(Modifier.height(30.dp))

        // --- 전송 버튼 ---
        Button(
            onClick = {
                scope.launch {
                    statusMessage = "전송 중..."
                    val result = sendMagicPacket(macAddress, ipAddress)
                    statusMessage = result
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("🚀 PC 켜기 (Send Packet)")
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = statusMessage,
            color = if (statusMessage.contains("실패")) MaterialTheme.colors.error else MaterialTheme.colors.primary
        )
    }
}

// --- 파일 다이얼로그 띄우는 함수 ---
fun openFileDialog(window: ComposeWindow, mode: Int): File? {
    val dialog = FileDialog(window, if (mode == FileDialog.LOAD) "설정 파일 열기" else "설정 파일 저장", mode)

    dialog.file = null
    dialog.isVisible = true

    val dir = dialog.directory
    val file = dialog.file

    return if (dir != null && file != null) {
        File(dir, file)
    } else {
        null
    }
}

// --- 파일 입출력 로직 ---
fun loadConfigFromFile(file: File): Pair<String, String> {
    val props = Properties()
    try {
        FileInputStream(file).use { props.load(it) }
    } catch (e: Exception) {
        e.printStackTrace()
        return Pair("", "255.255.255.255")
    }
    return Pair(
        props.getProperty("mac", ""),
        props.getProperty("ip", "255.255.255.255")
    )
}

fun saveConfigToFile(file: File, mac: String, ip: String) {
    val props = Properties()
    props.setProperty("mac", mac)
    props.setProperty("ip", ip)

    // 사용자가 확장자를 안 적었으면 .properties 붙여주기 (편의성)
    val targetFile = if (file.name.contains(".")) file else File(file.parentFile, "${file.name}.properties")

    try {
        FileOutputStream(targetFile).use { props.store(it, "WoL Configuration") }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// --- WoL 전송 로직 (기존 동일) ---
suspend fun sendMagicPacket(macStr: String, ipStr: String, port: Int = 9): String {
    return withContext(Dispatchers.IO) {
        try {
            val targetIp = if (ipStr.isBlank()) "255.255.255.255" else ipStr.trim()
            val macBytes = getMacBytes(macStr)

            val bytes = ByteArray(6 + 16 * macBytes.size)
            for (i in 0 until 6) bytes[i] = 0xff.toByte()
            for (i in 0 until 16) {
                System.arraycopy(macBytes, 0, bytes, 6 + i * macBytes.size, macBytes.size)
            }

            val address = InetAddress.getByName(targetIp)
            val packet = DatagramPacket(bytes, bytes.size, address, port)

            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.send(packet)
            }
            "전송 성공! ($targetIp)"
        } catch (e: Exception) {
            e.printStackTrace()
            "전송 실패: ${e.message}"
        }
    }
}

fun getMacBytes(macStr: String): ByteArray {
    val hex = macStr.replace(":", "").replace("-", "").trim()
    if (hex.length != 12) throw IllegalArgumentException("잘못된 MAC 주소")
    val bytes = ByteArray(6)
    for (i in 0 until 6) {
        bytes[i] = Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16).toByte()
    }
    return bytes
}