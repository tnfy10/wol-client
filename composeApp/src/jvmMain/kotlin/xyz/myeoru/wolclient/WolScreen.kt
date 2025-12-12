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
import org.jetbrains.compose.ui.tooling.preview.Preview
import java.awt.FileDialog
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.*

@Composable
fun WolScreen(
    openFileDialog: (mode: Int) -> File?
) {
    var macAddress by remember { mutableStateOf("") }
    var ipAddress by remember { mutableStateOf("255.255.255.255") }
    var port by remember { mutableStateOf("9") } // 포트 번호 상태 추가 (기본값 9)
    var statusMessage by remember { mutableStateOf("준비됨") }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🖥️ Wake On LAN", style = MaterialTheme.typography.h5)

        Spacer(Modifier.height(30.dp))

        // --- MAC 주소 입력 ---
        OutlinedTextField(
            value = macAddress,
            onValueChange = { macAddress = it },
            label = { Text("MAC Address") },
            placeholder = { Text("예: AA:BB:CC:DD:EE:FF") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(10.dp))

        // --- IP 주소와 포트 번호를 가로(Row)로 배치 ---
        Row(modifier = Modifier.fillMaxWidth()) {
            // IP 주소 (화면의 70% 차지)
            OutlinedTextField(
                value = ipAddress,
                onValueChange = { ipAddress = it },
                label = { Text("IP Address") },
                modifier = Modifier.weight(0.7f),
                singleLine = true
            )

            Spacer(Modifier.width(10.dp))

            // 포트 번호 (화면의 30% 차지)
            OutlinedTextField(
                value = port,
                onValueChange = { newValue ->
                    // 숫자만 입력되도록 필터링
                    if (newValue.all { it.isDigit() }) {
                        port = newValue
                    }
                },
                label = { Text("Port") },
                placeholder = { Text("9") },
                modifier = Modifier.weight(0.3f),
                singleLine = true
            )
        }

        Spacer(Modifier.height(20.dp))

        // --- 파일 저장/불러오기 버튼 영역 ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    val file = openFileDialog(FileDialog.LOAD)
                    if (file != null) {
                        // 포트 번호까지 같이 불러옴 (Triple 사용)
                        val (loadedMac, loadedIp, loadedPort) = loadConfigFromFile(file)
                        macAddress = loadedMac
                        ipAddress = loadedIp
                        port = loadedPort
                        statusMessage = "설정 불러오기 성공: ${file.name}"
                    }
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.secondary)
            ) {
                Text("📂 불러오기")
            }

            Button(
                onClick = {
                    val file = openFileDialog(FileDialog.SAVE)
                    if (file != null) {
                        saveConfigToFile(file, macAddress, ipAddress, port)
                        statusMessage = "설정 저장 완료: ${file.name}"
                    }
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.secondary)
            ) {
                Text("💾 저장하기")
            }
        }

        Spacer(Modifier.height(30.dp))

        // --- 전송 버튼 ---
        Button(
            onClick = {
                scope.launch {
                    statusMessage = "전송 중..."
                    // 입력된 포트 번호를 숫자로 변환 (없으면 9)
                    val portNumber = port.toIntOrNull() ?: 9
                    val result = sendMagicPacket(macAddress, ipAddress, portNumber)
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

// --- 파일 다이얼로그 (이전과 동일, 파일명 비움 처리 적용됨) ---
fun openFileDialog(window: ComposeWindow, mode: Int): File? {
    val dialog = FileDialog(window, if (mode == FileDialog.LOAD) "설정 파일 열기" else "설정 파일 저장", mode)
    dialog.file = null // 파일명 입력창 비우기
    dialog.isVisible = true

    val dir = dialog.directory
    val file = dialog.file

    return if (dir != null && file != null) {
        File(dir, file)
    } else {
        null
    }
}

// --- 파일 입출력 로직 (포트 추가됨) ---
fun loadConfigFromFile(file: File): Triple<String, String, String> {
    val props = Properties()
    try {
        FileInputStream(file).use { props.load(it) }
    } catch (e: Exception) {
        e.printStackTrace()
        return Triple("", "255.255.255.255", "9")
    }
    return Triple(
        props.getProperty("mac", ""),
        props.getProperty("ip", "255.255.255.255"),
        props.getProperty("port", "9") // 포트 불러오기
    )
}

fun saveConfigToFile(file: File, mac: String, ip: String, port: String) {
    val props = Properties()
    props.setProperty("mac", mac)
    props.setProperty("ip", ip)
    props.setProperty("port", port) // 포트 저장

    val targetFile = if (file.name.contains(".")) file else File(file.parentFile, "${file.name}.properties")

    try {
        FileOutputStream(targetFile).use { props.store(it, "WoL Configuration") }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// --- WoL 전송 로직 ---
suspend fun sendMagicPacket(macStr: String, ipStr: String, port: Int): String {
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
            // 여기서 전달받은 port 변수를 사용합니다.
            val packet = DatagramPacket(bytes, bytes.size, address, port)

            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.send(packet)
            }
            "전송 성공! ($targetIp:$port)"
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

@Preview
@Composable
private fun WolScreenPreview() {
    WolScreen(
        openFileDialog = { null }
    )
}