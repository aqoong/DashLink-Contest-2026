package ai.pleos.contest.dashlink

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData

data class PositionFix(
    val latitude: Double,
    val longitude: Double,
    val sourceTimestamp: Long?,
    val receivedElapsedMs: Long,
    val quality: String,
)

class PleosLocationGateway(context: Context, private val listener: Listener) {
    interface Listener {
        fun onStatus(ready: Boolean, detail: String)
        fun onLocation(fix: PositionFix?)
        fun onError(detail: String)
    }

    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var sdk: FusedLocation? = null
    private var updates: FusedLocationListener? = null
    private var session = 0
    private var request = 0
    private var revision = 0

    fun start() {
        check(Looper.myLooper() == main.looper)
        if (sdk != null) return
        if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            listener.onLocation(null)
            listener.onStatus(false, "위치 권한이 필요합니다")
            return
        }
        val token = ++session
        try {
            val client = FusedLocation(context)
            if (!client.isSupported()) {
                listener.onLocation(null)
                listener.onStatus(false, "이 환경은 Pleos 위치 SDK를 지원하지 않습니다")
                return
            }
            sdk = client
            client.initialize()
            val callback = object : FusedLocationListener {
                override fun onChanged(location: LocationData?) {
                    val received = SystemClock.elapsedRealtime()
                    deliver(token) {
                        revision++
                        accept(location, received, cached = false)
                    }
                }

                override fun onError(exception: Exception) = deliver(token) {
                    revision++
                    fail("위치 업데이트 실패", exception)
                }
            }
            updates = callback
            client.registerFusedLocationCallback(callback)
            listener.onStatus(true, "위치 SDK 초기화 · 위치 응답 대기")
            refresh()
        } catch (exception: Exception) {
            stop()
            fail("위치 SDK 시작 실패", exception)
        }
    }

    fun refresh() {
        check(Looper.myLooper() == main.looper)
        val client = sdk ?: return
        val token = session
        val query = ++request
        val before = revision
        val timeout = Runnable {
            if (session == token && sdk != null && request == query && revision == before) {
                request++
                listener.onLocation(null)
                listener.onStatus(false, "위치 응답 없음 · 다시 조회할 수 있습니다")
                listener.onError("위치 조회 응답이 8초 안에 도착하지 않았습니다")
            }
        }
        main.postDelayed(timeout, 8_000)
        try {
            client.getLastKnownLocation(object : FusedLocationListener {
                override fun onChanged(location: LocationData?) {
                    val received = SystemClock.elapsedRealtime()
                    deliver(token) {
                        main.removeCallbacks(timeout)
                        if (request == query && revision == before) {
                            accept(location, received, cached = true)
                        }
                    }
                }

                override fun onError(exception: Exception) = deliver(token) {
                    main.removeCallbacks(timeout)
                    if (request == query && revision == before) fail("마지막 위치 조회 실패", exception)
                }
            })
        } catch (exception: Exception) {
            main.removeCallbacks(timeout)
            fail("마지막 위치 조회 실패", exception)
        }
    }

    fun stop() {
        check(Looper.myLooper() == main.looper)
        session++
        main.removeCallbacksAndMessages(null)
        val client = sdk
        val callback = updates
        sdk = null
        updates = null
        if (client != null) {
            try {
                if (callback != null) client.unregisterFusedLocationCallback(callback)
            } catch (exception: Exception) {
                listener.onError("위치 구독 해제 실패 (${exception.javaClass.simpleName})")
            } finally {
                try {
                    client.release()
                } catch (exception: Exception) {
                    listener.onError("위치 SDK 해제 실패 (${exception.javaClass.simpleName})")
                }
            }
        }
    }

    private fun deliver(token: Int, action: () -> Unit) {
        main.post { if (session == token && sdk != null) action() }
    }

    private fun accept(location: LocationData?, received: Long, cached: Boolean) {
        val fix = location?.toPositionFix(received, cached)
        listener.onLocation(fix)
        if (location != null && fix == null) {
            listener.onStatus(false, "위치 좌표가 유효하지 않습니다")
            listener.onError("SDK 위도·경도가 숫자 또는 좌표 범위를 벗어났습니다")
        } else {
            listener.onStatus(true, if (fix == null) "위치 없음 · 설정과 수신 상태를 확인하세요" else fix.quality)
        }
    }

    private fun fail(detail: String, exception: Exception) {
        listener.onLocation(null)
        listener.onStatus(false, detail)
        listener.onError("$detail (${exception.javaClass.simpleName})")
    }

    companion object {
        const val PERMISSION = "pleos.car.permission.FUSED_LOCATION"
    }
}

internal fun LocationData.toPositionFix(receivedElapsedMs: Long, cached: Boolean): PositionFix? {
    if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
        !longitude.isFinite() || longitude !in -180.0..180.0
    ) return null
    // shortcut: timestamp epoch와 confidence 계약 미공개, 공식 계약 확인 후 최신성·오차 판정 추가.
    val origin = if (cached) "마지막 확인 위치(캐시)" else "SDK 위치 업데이트"
    val rawConfidence = if (confidence.isFinite()) confidence.toString() else "값 없음"
    return PositionFix(
        latitude, longitude, timestampMillis, receivedElapsedMs,
        "$origin · 최신성 미검증 · 신뢰도 해석 미확인($rawConfidence)",
    )
}
