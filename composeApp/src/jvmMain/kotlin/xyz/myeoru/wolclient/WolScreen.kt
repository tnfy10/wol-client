package xyz.myeoru.wolclient

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.ui.tooling.preview.Preview
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

@Preview
@Composable
fun WolScreen() {
    // 저장된 값을 불러오는 대신 기본값으로 초기화
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

        // MAC 주소 입력
        OutlinedTextField(
            value = macAddress,
            onValueChange = { macAddress = it },
            label = { Text("MAC Address (예: AA:BB:CC:...)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(15.dp))

        // IP 주소 입력
        OutlinedTextField(
            value = ipAddress,
            onValueChange = { ipAddress = it },
            label = { Text("IP Address") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("기본값: 255.255.255.255") }
        )
        Text(
            text = "* 내부망: 255.255.255.255 / 외부망: DDNS 주소",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.Start).padding(start = 5.dp, top = 4.dp)
        )

        Spacer(Modifier.height(25.dp))

        Button(
            onClick = {
                scope.launch {
                    // 저장 로직(saveConfig) 삭제됨
                    statusMessage = "전송 중..."
                    val result = sendMagicPacket(macAddress, ipAddress)
                    statusMessage = result
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("PC 켜기 (Send Packet)")
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = statusMessage,
            color = if (statusMessage.contains("실패")) MaterialTheme.colors.error else MaterialTheme.colors.primary
        )
    }
}

// --- WoL 전송 로직 ---
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