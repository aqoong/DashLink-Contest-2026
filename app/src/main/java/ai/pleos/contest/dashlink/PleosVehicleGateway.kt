package ai.pleos.contest.dashlink

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Light
import ai.pleos.playground.vehicle.api.Safety
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.WheelArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SeatAirbagDeployedListener
import java.util.concurrent.TimeoutException

enum class Lamp { HIGH_BEAM, LOW_BEAM, TAIL, FRONT_FOG, REAR_FOG }

data class LampCapability(val lamp: Lamp, val label: String, val writable: Boolean, val detail: String)

class PleosVehicleGateway(
    context: Context,
    private val listener: Listener,
    recoveryPreferences: SharedPreferences,
    private val canControl: () -> Boolean,
) {
    interface Listener {
        fun onConnection(ready: Boolean, detail: String)
        fun onAirbagAvailability(available: Boolean, detail: String)
        fun onAirbag(seat: String, deployed: Boolean?)
        fun onLamps(lamps: List<LampCapability>)
        fun onLampState(lamp: Lamp, state: String)
        fun onError(domain: String, detail: String)
        fun onReading(reading: VehicleReading) {}
        fun onRecoveryChanged(required: Boolean, detail: String) {}
    }

    private class Session(val vehicle: Vehicle) {
        val safety: Safety = vehicle.getSafety()
        val light: Light = vehicle.getLight()
        val airbags = mutableMapOf<SeatArea, SeatAirbagDeployedListener>()
        val lights = mutableMapOf<Lamp, LightStateListener>()
        val capabilities = mutableMapOf<Lamp, LampCapability>()
        val allowedSwitches = mutableMapOf<Lamp, List<LightSwitch>>()
        var active = true
        var connected = false
        var pending = 0
        var released = false
        var writeBusy = false
        val restoreWaiters = mutableListOf<(Boolean, String) -> Unit>()
    }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var session: Session? = null
    private var capturePending = false
    private var lampEpoch = 0
    private val recovery = LampRecoveryJournal(read = {
        LampRecoveryState(
            required = recoveryPreferences.getBoolean("lamp_session_open", false) ||
                recoveryPreferences.contains("lamp_restore_lamp") || recoveryPreferences.contains("lamp_restore_switch") ||
                recoveryPreferences.contains("lamp_write_in_flight") || recoveryPreferences.contains("lamp_restore_revision"),
            lamp = Lamp.entries.firstOrNull { it.name == recoveryPreferences.getString("lamp_restore_lamp", null) },
            original = LightSwitch.entries.firstOrNull { it.name == recoveryPreferences.getString("lamp_restore_switch", null) },
            inFlight = recoveryPreferences.getString("lamp_write_in_flight", null),
            revision = recoveryPreferences.getString("lamp_restore_revision", null),
        )
    }, save = { state ->
        recoveryPreferences.edit().putBoolean("lamp_session_open", state.required)
            .putString("lamp_restore_lamp", state.lamp?.name)
            .putString("lamp_restore_switch", state.original?.name)
            .putString("lamp_write_in_flight", state.inFlight)
            .putString("lamp_restore_revision", state.revision).commit()
    })
    val recoveryRequired: Boolean get() = recovery.state.required
    val recoveryDetail: String get() = recovery.state.let {
        when {
            !it.required -> "조명 복귀 확인됨"
            it.lamp == null || it.original == null -> "이전 버전의 원래 조명 기록이 없어 자동 복구할 수 없어요"
            it.inFlight != null -> "이전 조명 요청의 종료를 확인하지 못했어요"
            else -> "${label(it.lamp)} 원래 스위치 ${it.original.name} · 정차 후 복구 필요"
        }
    }

    fun start() = onMain {
        if (session != null) return@onMain
        listener.onConnection(false, "Vehicle 서비스 응답 확인 중")
        listener.onAirbagAvailability(false, "에어백 전개 지원 좌석 확인 중")
        var vehicle: Vehicle? = null
        try {
            vehicle = Vehicle(appContext)
            vehicle.initialize()
            val s = Session(vehicle)
            session = s
            Lamp.entries.forEach { s.capabilities[it] = LampCapability(it, label(it), false, "지원 확인 중") }
            publishLamps(s)
            checkAirbags(s)
            Lamp.entries.forEach { checkLamp(s, it) }
            checkDoors(s)
            checkTires(s)
            checkBattery(s)
        } catch (e: Exception) {
            vehicle?.let { runCatching { it.release() } }
            session = null
            listener.onConnection(false, errorText(e))
            listener.onError("Vehicle 초기화", errorText(e))
            listOf("doors" to "문", "tires" to "타이어", "battery" to "EV 배터리", "airbag" to "에어백", "lights" to "조명")
                .forEach { (group, title) -> failedReading(group, group, title, e) }
        }
    }

    fun stop() = onMain {
        val s = session ?: return@onMain
        s.active = false
        session = null
        lampEpoch++
        capturePending = false
        s.airbags.forEach { (area, callback) ->
            runCatching { s.safety.unregisterSeatAirbagDeployed(area, callback) }
                .onFailure { listener.onError("에어백 구독 해제", it.message ?: "해제 실패") }
        }
        s.lights.forEach { (lamp, callback) ->
            runCatching { unregisterLight(s.light, lamp, callback) }
                .onFailure { listener.onError("${label(lamp)} 구독 해제", it.message ?: "해제 실패") }
        }
        s.airbags.clear()
        s.lights.clear()
        s.restoreWaiters.clear()
        if (recoveryRequired) lampError("연결 종료 · $recoveryDetail")
        releaseWhenIdle(s)
        listener.onConnection(false, "Vehicle 연결 종료")
    }

    fun captureLamp(lamp: Lamp, done: (Boolean, String) -> Unit) = onMain {
        val s = session
        if (!canControl() || s == null || s.capabilities[lamp]?.writable != true) {
            done(false, "읽기·쓰기와 ON/OFF를 모두 지원하는 램프가 아닙니다")
            return@onMain
        }
        if (recoveryRequired || capturePending || s.writeBusy) {
            done(false, "이전 램프 작업 또는 원래 스위치 복구를 먼저 완료해야 합니다")
            return@onMain
        }
        capturePending = true
        val epoch = ++lampEpoch
        request<LightSwitch?>(s, { value ->
            if (epoch != lampEpoch || !canControl()) {
                if (epoch == lampEpoch) capturePending = false
                done(false, "램프 시작이 취소되었습니다")
            } else {
                capturePending = false
                if (value == null || value !in s.allowedSwitches[lamp].orEmpty()) {
                    done(false, "원래 스위치가 null이거나 복구 가능한 지원값이 아닙니다")
                } else {
                    val saved = recovery.capture(lamp, value)
                    Log.i("DashLinkLamp", "capture lamp=$lamp original=$value saved=$saved")
                    recoveryChanged()
                    done(saved, if (saved) "원래 스위치 ${value.name} 저장" else "조명 복구 기록 저장 실패 · 출력 차단")
                }
            }
        }, { e ->
            if (epoch == lampEpoch) capturePending = false
            listener.onError("램프 원래 상태", errorText(e))
            done(false, errorText(e))
        }) { success, failure -> getSwitch(s.light, lamp, success, failure) }
    }

    fun setLamp(lamp: Lamp, on: Boolean, done: (Boolean, String) -> Unit) = onMain {
        val s = session
        if (!canControl() || s == null || recovery.state.lamp != lamp || s.capabilities[lamp]?.writable != true) {
            done(false, "램프 원래 스위치를 저장한 뒤 제어할 수 있습니다")
            return@onMain
        }
        if (s.writeBusy || s.restoreWaiters.isNotEmpty() || recovery.state.inFlight != null) {
            done(false, "램프 작업 중이거나 이전 쓰기 결과가 불명확합니다")
            return@onMain
        }
        val value = if (on) LightSwitch.ON else LightSwitch.OFF
        val writeToken = recovery.beginWrite()
        if (writeToken == null) {
            done(false, "조명 요청 기록 저장 실패 · 출력 차단")
            return@onMain
        }
        s.writeBusy = true
        Log.i("DashLinkLamp", "write lamp=$lamp value=$value")
        request<LightSwitch?>(s, { returned ->
            s.writeBusy = false
            val ok = returned == value && recovery.state.inFlight == null
            val detail = if (ok) "${value.name} 요청 응답" else "요청 ${value.name}, 반환 ${returned?.name ?: "null"} 불일치"
            if (!ok) listener.onError("램프 제어", detail)
            done(ok, detail)
            restoreWhenIdle(s)
        }, { e ->
            s.writeBusy = false
            lampError("write lamp=$lamp value=$value ${errorText(e)}")
            listener.onError("램프 제어", errorText(e))
            done(false, errorText(e))
            restoreWhenIdle(s)
        }, terminal = { error -> finishWrite(writeToken, error) }) { success, failure -> setSwitch(s.light, lamp, value, success, failure) }
    }

    fun restoreLamp(done: (Boolean, String) -> Unit) = onMain {
        lampEpoch++
        capturePending = false
        if (!recoveryRequired) {
            done(true, "복구할 램프 변경 없음")
            return@onMain
        }
        if (!canControl()) {
            lampError("restore deferred: CarUx or foreground restriction")
            done(false, "정차 후 조명 복구가 필요해요")
            return@onMain
        }
        val s = session
        if (s == null) {
            done(false, "Vehicle 연결이 없어 원래 스위치를 복구하지 못했습니다")
            return@onMain
        }
        s.restoreWaiters += done
        restoreWhenIdle(s)
    }

    fun recoverLamp(done: (Boolean, String) -> Unit) = restoreLamp(done)

    private fun restoreWhenIdle(s: Session) {
        if (!isCurrent(s) || s.writeBusy || s.restoreWaiters.isEmpty()) return
        val stored = recovery.state
        if (!stored.required) {
            finishRestore(s, true, "복구할 램프 변경 없음")
            return
        }
        if (!canControl() || stored.lamp == null || stored.original == null || stored.inFlight != null) {
            finishRestore(s, false, if (!canControl()) "정차 후 조명 복구가 필요해요" else recoveryDetail)
            return
        }
        val lamp = stored.lamp
        val original = stored.original
        if (s.capabilities[lamp]?.writable != true || original !in s.allowedSwitches[lamp].orEmpty()) {
            finishRestore(s, false, "원래 스위치 복구에 필요한 지원·권한을 확인하지 못했습니다")
            return
        }
        val writeToken = recovery.beginWrite()
        if (writeToken == null) {
            finishRestore(s, false, "복구 요청 기록 저장 실패 · 잠금 유지")
            return
        }
        s.writeBusy = true
        Log.i("DashLinkLamp", "restore lamp=$lamp original=$original")
        request<LightSwitch?>(s, { returned ->
            if (returned != original || !canControl() || recovery.state.inFlight != null) {
                s.writeBusy = false
                finishRestore(s, false, "복구 응답 ${returned?.name ?: "null"} · 정차·요청 종료 확인 필요")
                return@request
            }
            // Setter success echoes the request in 2.3.2; verify the switch with a separate read.
            request<LightSwitch?>(s, { observed ->
                s.writeBusy = false
                val ok = recovery.clearIfVerified(lamp, observed, canControl(), writeToken)
                Log.i("DashLinkLamp", "verify lamp=$lamp original=$original observed=$observed cleared=$ok")
                recoveryChanged()
                val detail = if (ok) "원래 스위치 ${original.name} 재조회 일치 · 점등 확인 아님"
                    else "복구 재조회 ${observed?.name ?: "null"} · 확인 미완료로 잠금 유지"
                finishRestore(s, ok, detail)
            }, { e ->
                s.writeBusy = false
                finishRestore(s, false, "복구 후 스위치 재조회 실패 · ${errorText(e)}")
            }) { success, failure -> getSwitch(s.light, lamp, success, failure) }
        }, { e ->
            s.writeBusy = false
            finishRestore(s, false, errorText(e))
        }, terminal = { error -> finishWrite(writeToken, error) }) { success, failure -> setSwitch(s.light, lamp, original, success, failure) }
    }

    private fun finishRestore(s: Session, ok: Boolean, detail: String) {
        val callbacks = s.restoreWaiters.toList()
        s.restoreWaiters.clear()
        if (!ok) lampError(detail)
        recoveryChanged()
        callbacks.forEach { it(ok, detail) }
    }

    private fun finishWrite(token: String, error: Exception?) {
        val uncertain = error is TimeoutException || error?.message?.let {
            it.contains("timeout", ignoreCase = true) || it.contains("timed out", ignoreCase = true)
        } == true
        val settled = !uncertain && recovery.finishWrite(token)
        Log.i("DashLinkLamp", "write terminal settled=$settled error=${error?.let(::errorText) ?: "none"}")
        recoveryChanged()
    }

    private fun recoveryChanged() = listener.onRecoveryChanged(recoveryRequired, recoveryDetail)
    private fun lampError(detail: String) {
        Log.w("DashLinkLamp", detail)
        listener.onError("램프 복구", detail)
    }

    private fun checkAirbags(s: Session) {
        request<Pair<CapabilityConfig, List<SeatArea>>>(s, { (config, areas) ->
            markConnected(s)
            val readable = config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE
            if (!readable || areas.isEmpty()) {
                listener.onAirbagAvailability(false, "전개 감시 불가: ${config.accessType}, ${areas.size}개 영역")
                capabilityReading("airbag", "airbag", "에어백 전개", readable, areas.isEmpty())
                return@request
            }
            listener.onAirbagAvailability(true, "${areas.distinct().size}개 좌석 영역 지원 · 구독 요청")
            areas.distinct().forEach { area -> observeAirbag(s, area) }
        }, { e ->
            failedReading("airbag", "airbag", "에어백 전개", e)
            listener.onAirbagAvailability(false, errorText(e))
            listener.onError("에어백 지원 확인", errorText(e))
        }) { success, failure ->
            s.safety.checkAirbagDeployedCapabilities({ config, areas -> success(config to areas) }, failure)
        }
    }

    private fun observeAirbag(s: Session, area: SeatArea) {
        val seat = area.values.joinToString(" + ") { it.name }
        val id = "airbag.$seat"
        val title = "${area.values.joinToString(" · ") { areaLabel(it.name) }} 에어백"
        reading(id, "airbag", title, ReadStatus.LOADING)
        fun publish(deployed: Boolean?) {
            listener.onAirbag(seat, deployed)
            reading(id, "airbag", title, if (deployed == null) ReadStatus.FAILED else ReadStatus.READABLE,
                deployed?.let { if (it) "전개됨" else "전개되지 않음" }.orEmpty(),
                if (deployed == null) "전개 상태를 확인하지 못했어요" else "전개 상태 조회 결과이며 사고 발생 시각을 뜻하지 않아요")
        }
        var updateReceived = false
        listener.onAirbag(seat, null)
        val callback = object : SeatAirbagDeployedListener {
            override fun onSeatAirbagDeployUpdated(deployed: Boolean?) = current(s) {
                updateReceived = true
                publish(deployed)
            }

            override fun onFailed(e: Exception) = current(s) {
                updateReceived = true
                listener.onAirbag(seat, null)
                failedReading(id, "airbag", title, e)
                listener.onAirbagAvailability(false, "$seat 구독 오류 · 감시 불완전")
                listener.onError("에어백 $seat 구독", errorText(e))
            }
        }
        s.airbags[area] = callback
        try {
            s.safety.registerSeatAirbagDeployed(area, callback)
        } catch (e: Exception) {
            listener.onAirbagAvailability(false, "$seat 구독 실패 · 감시 불완전")
            listener.onError("에어백 $seat 구독", errorText(e))
        }
        request<Boolean?>(s, { deployed ->
            // A subscription update is newer evidence than an outstanding initial read.
            if (!updateReceived) publish(deployed)
        }, { e ->
            if (!updateReceived) {
                listener.onAirbag(seat, null)
                failedReading(id, "airbag", title, e)
            }
            listener.onError("에어백 $seat 조회", errorText(e))
        }) { success, failure -> s.safety.hasSeatAirbagBeenDeployed(area, success, failure) }
    }

    private fun checkLamp(s: Session, lamp: Lamp) {
        reading("lights.${lamp.name}", "lights", label(lamp), ReadStatus.LOADING)
        request<Pair<CapabilityConfig, List<LightSwitch>>>(s, { (config, values) ->
            markConnected(s)
            s.allowedSwitches[lamp] = values
            val writable = config.accessType == AccessType.READ_WRITE && LightSwitch.ON in values && LightSwitch.OFF in values
            s.capabilities[lamp] = LampCapability(lamp, label(lamp), writable, "${config.accessType} · ${values.joinToString { it.name }.ifEmpty { "지원값 없음" }}")
            publishLamps(s)
        }, { e ->
            s.capabilities[lamp] = LampCapability(lamp, label(lamp), false, errorText(e))
            publishLamps(s)
            listener.onError("${label(lamp)} 지원 확인", errorText(e))
        }) { success, failure -> checkSwitch(s.light, lamp, { config, values -> success(config to values) }, failure) }
        request<Pair<CapabilityConfig, List<LightState>>>(s, { (config, values) ->
            markConnected(s)
            if ((config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE) && values.isNotEmpty()) {
                observeLight(s, lamp)
            } else {
                listener.onLampState(lamp, "관측 불가: ${config.accessType}")
                capabilityReading("lights.${lamp.name}", "lights", label(lamp), readable(config), values.isEmpty())
            }
        }, { e ->
            listener.onLampState(lamp, "관측 불가: ${errorText(e)}")
            failedReading("lights.${lamp.name}", "lights", label(lamp), e)
        }) { success, failure -> checkState(s.light, lamp, { config, values -> success(config to values) }, failure) }
    }

    private fun observeLight(s: Session, lamp: Lamp) {
        fun publish(state: LightState?) {
            listener.onLampState(lamp, state?.name ?: "알 수 없음 (null)")
            val value = when (state) { LightState.ON -> "켜짐"; LightState.OFF -> "꺼짐"; LightState.DAYTIME_RUNNING -> "주간 주행등"; null -> "" }
            reading("lights.${lamp.name}", "lights", label(lamp), if (state == null) ReadStatus.FAILED else ReadStatus.READABLE,
                value, if (state == null) "점등 상태를 확인하지 못했어요" else "차량 연결이 반환한 관측 상태예요")
        }
        var updateReceived = false
        val callback = object : LightStateListener {
            override fun onLightStateUpdated(state: LightState?) = current(s) {
                updateReceived = true
                publish(state)
            }

            override fun onFailed(e: Exception) = current(s) {
                updateReceived = true
                listener.onLampState(lamp, "관측 오류: ${errorText(e)}")
                failedReading("lights.${lamp.name}", "lights", label(lamp), e)
                listener.onError("${label(lamp)} 구독", errorText(e))
            }
        }
        s.lights[lamp] = callback
        try {
            registerLight(s.light, lamp, callback)
        } catch (e: Exception) {
            listener.onError("${label(lamp)} 구독", errorText(e))
        }
        request<LightState?>(s, { value ->
            if (!updateReceived) publish(value)
        }, { e ->
            if (!updateReceived) {
                listener.onLampState(lamp, "관측 오류: ${errorText(e)}")
                failedReading("lights.${lamp.name}", "lights", label(lamp), e)
            }
        }) { success, failure -> getState(s.light, lamp, success, failure) }
    }

    private fun checkDoors(s: Session) {
        val door = s.vehicle.getDoor()
        request<Pair<CapabilityConfig, Map<DoorArea, AreaBounds<Int>>>>(s, { (config, areas) ->
            markConnected(s)
            if (!readable(config) || areas.isEmpty()) {
                capabilityReading("doors.position", "doors", "문 열림 정보", readable(config), areas.isEmpty())
                return@request
            }
            areas.forEach { (area, bounds) ->
                val key = area.values.joinToString("+") { it.name }
                val title = "${area.values.joinToString(" · ") { areaLabel(it.name) }} 위치"
                readValue<Int>(s, "doors.position.$key", "doors", title,
                    "지원 위치 범위 ${bounds.minValue}~${bounds.maxValue} · 각도나 퍼센트가 아니에요",
                    { ReadingValues.doorPosition(it, bounds.minValue, bounds.maxValue) }) { success, failure ->
                    door.getCurrentDoorPosition(area, success, failure)
                }
            }
        }, { failedReading("doors.position", "doors", "문 열림 정보", it) }) { success, failure ->
            door.checkDoorPositionCapabilities({ config, areas -> success(config to areas) }, failure)
        }
        request<Pair<CapabilityConfig, List<DoorArea>>>(s, { (config, areas) ->
            markConnected(s)
            if (!readable(config) || areas.isEmpty()) {
                capabilityReading("doors.lock", "doors", "문 잠금 정보", readable(config), areas.isEmpty())
                return@request
            }
            areas.distinct().forEach { area ->
                val key = area.values.joinToString("+") { it.name }
                val title = "${area.values.joinToString(" · ") { areaLabel(it.name) }} 잠금"
                readValue<Boolean>(s, "doors.lock.$key", "doors", title, "잠금 상태를 읽기만 해요",
                    { it?.let { locked -> if (locked) "잠김" else "잠금 해제" } }) { success, failure ->
                    door.getDoorLockState(area, success, failure)
                }
            }
        }, { failedReading("doors.lock", "doors", "문 잠금 정보", it) }) { success, failure ->
            door.checkDoorLockCapabilities({ config, areas -> success(config to areas) }, failure)
        }
    }

    private fun checkTires(s: Session) {
        // getTirePressure requires PRIVATE_CAR_SAFETY in 2.3.2; the public API exposes the low-pressure threshold only.
        reading("tires.pressure", "tires", "현재 공기압", ReadStatus.FAILED,
            detail = "공개 SDK에서 현재 공기압을 조회할 수 없어요. 차량 미지원 판정은 아니에요")
        val tire = s.vehicle.getTire()
        request<Pair<CapabilityConfig, List<WheelArea>>>(s, { (config, areas) ->
            markConnected(s)
            if (!readable(config) || areas.isEmpty()) {
                capabilityReading("tires.threshold", "tires", "저압 경고 기준", readable(config), areas.isEmpty())
                return@request
            }
            areas.distinct().forEach { area ->
                val key = area.values.joinToString("+") { it.name }
                val title = "${area.values.joinToString(" · ") { areaLabel(it.name) }} 저압 경고 기준"
                readValue<Float>(s, "tires.threshold.$key", "tires", title,
                    "저압 경고 기준이며 지금 측정한 타이어 공기압은 아니에요", ReadingValues::lowPressure) { success, failure ->
                    tire.getLowTirePressure(area, success, failure)
                }
            }
        }, { failedReading("tires.threshold", "tires", "저압 경고 기준", it) }) { success, failure ->
            tire.checkLowTirePressureCapabilities({ config, areas -> success(config to areas) }, failure)
        }
    }

    private fun checkBattery(s: Session) {
        val battery = s.vehicle.getEvBattery()
        fun <T> query(id: String, title: String, detail: String, format: (T?) -> String?,
                      capability: ((CapabilityConfig) -> Unit, (Exception) -> Unit) -> Unit,
                      get: ((T?) -> Unit, (Exception) -> Unit) -> Unit) {
            reading(id, "battery", title, ReadStatus.LOADING)
            request<CapabilityConfig>(s, { config ->
                markConnected(s)
                if (readable(config)) readValue(s, id, "battery", title, detail, format, get)
                else capabilityReading(id, "battery", title, false, false)
            }, { failedReading(id, "battery", title, it) }, call = capability)
        }
        query<Int>("battery.soc", "EV 배터리 잔량", "고전압 구동 배터리 잔량이며 12V 배터리 상태는 아니에요",
            ReadingValues::stateOfCharge, battery::checkEvBatteryStateOfChargeCapabilities, battery::getEvBatteryStateOfCharge)
        query<Boolean>("battery.port", "충전구", "충전구 열림 상태이며 충전 케이블 연결 여부와 달라요",
            { it?.let { open -> if (open) "열림" else "닫힘" } }, battery::checkEvChargePortOpenCapabilities, battery::isEvChargePortOpen)
        query<Boolean>("battery.switch", "충전 스위치", "스위치 상태이며 실제 충전 진행 여부를 뜻하지 않아요",
            { it?.let { on -> if (on) "켜짐" else "꺼짐" } }, battery::checkEvChargeSwitchCapabilities, battery::getEvChargeSwitchState)
    }

    private fun <T> readValue(s: Session, id: String, group: String, title: String, detail: String,
                             format: (T?) -> String?, call: ((T?) -> Unit, (Exception) -> Unit) -> Unit) {
        reading(id, group, title, ReadStatus.LOADING)
        request<T?>(s, { value ->
            val formatted = format(value)
            reading(id, group, title, if (formatted == null) ReadStatus.FAILED else ReadStatus.READABLE,
                formatted.orEmpty(), if (formatted == null) "유효한 값을 확인하지 못했어요" else detail)
        }, { failedReading(id, group, title, it) }, call = call)
    }

    private fun readable(config: CapabilityConfig) = config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE

    private fun capabilityReading(id: String, group: String, title: String, readable: Boolean, empty: Boolean) {
        reading(id, group, title, if (readable && empty) ReadStatus.UNSUPPORTED else ReadStatus.FAILED,
            detail = if (readable && empty) "이 환경에서 제공하는 지원 영역이 없어요" else "이 앱에서 읽을 수 있는 권한·기능을 확인하지 못했어요")
    }

    private fun failedReading(id: String, group: String, title: String, error: Exception) {
        val status = ReadingValues.failureStatus(error.javaClass.name, error.message)
        val detail = when {
            status == ReadStatus.UNSUPPORTED -> "이 환경에서는 이 정보를 제공하지 않아요"
            error is TimeoutException -> "응답이 늦어 조회하지 못했어요. 다시 확인해 주세요"
            error.message == "ERROR_SECURITY_PERMISSION" -> "조회 권한이 없어 정보를 읽지 못했어요"
            else -> "차량 연결에서 정보를 읽지 못했어요. 다시 확인해 주세요"
        }
        reading(id, group, title, status, detail = detail)
    }

    private fun reading(id: String, group: String, title: String, status: ReadStatus, value: String = "", detail: String = "") {
        listener.onReading(VehicleReading(id, group, title, status, value, detail,
            if (status == ReadStatus.READABLE) SystemClock.elapsedRealtime() else 0L))
    }

    private fun areaLabel(name: String) = when (name) {
        "ROW_1_LEFT" -> "1열 왼쪽"; "ROW_1_RIGHT" -> "1열 오른쪽"
        "ROW_2_LEFT" -> "2열 왼쪽"; "ROW_2_RIGHT" -> "2열 오른쪽"
        "ROW_3_LEFT" -> "3열 왼쪽"; "ROW_3_RIGHT" -> "3열 오른쪽"
        "HOOD" -> "보닛"; "REAR" -> "트렁크"
        "LEFT_FRONT" -> "왼쪽 앞"; "RIGHT_FRONT" -> "오른쪽 앞"
        "LEFT_REAR" -> "왼쪽 뒤"; "RIGHT_REAR" -> "오른쪽 뒤"
        else -> "영역 미확인"
    }

    private fun <T> request(
        s: Session,
        success: (T) -> Unit,
        failure: (Exception) -> Unit,
        terminal: ((Exception?) -> Unit)? = null,
        call: ((T) -> Unit, (Exception) -> Unit) -> Unit,
    ) {
        if (!isCurrent(s)) return
        s.pending++
        var finished = false
        var terminalReceived = false
        lateinit var timeout: Runnable
        fun finish(result: T?, error: Exception?, sdkTerminal: Boolean) = onMain {
            // A timeout ends the UI wait, but only a terminal SDK callback settles a durable write.
            if (sdkTerminal && !terminalReceived) {
                terminalReceived = true
                terminal?.invoke(error)
            }
            if (finished) return@onMain
            finished = true
            main.removeCallbacks(timeout)
            s.pending--
            if (isCurrent(s)) {
                if (error != null) {
                    if (!s.connected) listener.onConnection(false, "Vehicle 응답 실패 · ${errorText(error)}")
                    failure(error)
                } else {
                    @Suppress("UNCHECKED_CAST")
                    success(result as T)
                }
            }
            releaseWhenIdle(s)
        }
        timeout = Runnable { finish(null, TimeoutException("Vehicle 응답 5초 초과 · 결과 불명확"), false) }
        main.postDelayed(timeout, 5_000)
        try {
            call({ finish(it, null, true) }, { finish(null, it, true) })
        } catch (e: Exception) {
            finish(null, e, false)
        }
    }

    private fun releaseWhenIdle(s: Session) {
        if (!s.active && s.pending == 0 && !s.released) {
            s.released = true
            runCatching { s.vehicle.release() }
        }
    }

    private fun markConnected(s: Session) {
        if (!s.connected) {
            s.connected = true
            listener.onConnection(true, "Vehicle capability 응답 수신")
        }
    }

    private fun publishLamps(s: Session) = listener.onLamps(Lamp.entries.mapNotNull { s.capabilities[it] })
    private fun isCurrent(s: Session) = s.active && session === s
    private fun current(s: Session, action: () -> Unit) = onMain { if (isCurrent(s)) action() }
    private fun onMain(action: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post(action) }

    // shortcut: 2.3.2 obfuscates VehicleException; retain its message until a public error accessor ships.
    private fun errorText(e: Exception) = "${e.javaClass.simpleName}: ${e.message?.take(180) ?: "원인 정보 없음"}"

    private fun label(lamp: Lamp) = when (lamp) {
        Lamp.HIGH_BEAM -> "상향등"
        Lamp.LOW_BEAM -> "하향등"
        Lamp.TAIL -> "미등"
        Lamp.FRONT_FOG -> "앞 안개등"
        Lamp.REAR_FOG -> "뒤 안개등"
    }

    private fun checkSwitch(light: Light, lamp: Lamp, success: (CapabilityConfig, List<LightSwitch>) -> Unit, failure: (Exception) -> Unit) = when (lamp) {
        Lamp.HIGH_BEAM -> light.checkHighBeamLightSwitchCapabilities(success, failure)
        Lamp.LOW_BEAM -> light.checkLowBeamLightSwitchCapabilities(success, failure)
        Lamp.TAIL -> light.checkTailLampLightSwitchCapabilities(success, failure)
        Lamp.FRONT_FOG -> light.checkFrontFogLightSwitchCapabilities(success, failure)
        Lamp.REAR_FOG -> light.checkRearFogLightSwitchCapabilities(success, failure)
    }

    private fun checkState(light: Light, lamp: Lamp, success: (CapabilityConfig, List<LightState>) -> Unit, failure: (Exception) -> Unit) = when (lamp) {
        Lamp.HIGH_BEAM -> light.checkHighBeamLightStateCapabilities(success, failure)
        Lamp.LOW_BEAM -> light.checkLowBeamLightStateCapabilities(success, failure)
        Lamp.TAIL -> light.checkTailLampLightStateCapabilities(success, failure)
        Lamp.FRONT_FOG -> light.checkFrontFogLightStateCapabilities(success, failure)
        Lamp.REAR_FOG -> light.checkRearFogLightStateCapabilities(success, failure)
    }

    private fun getSwitch(light: Light, lamp: Lamp, success: (LightSwitch?) -> Unit, failure: (Exception) -> Unit) = when (lamp) {
        Lamp.HIGH_BEAM -> light.getHighBeamLightSwitch(success, failure)
        Lamp.LOW_BEAM -> light.getLowBeamLightSwitch(success, failure)
        Lamp.TAIL -> light.getTailLampLightSwitch(success, failure)
        Lamp.FRONT_FOG -> light.getFrontFogLightSwitch(success, failure)
        Lamp.REAR_FOG -> light.getRearFogLightSwitch(success, failure)
    }

    private fun setSwitch(light: Light, lamp: Lamp, value: LightSwitch, success: (LightSwitch?) -> Unit, failure: (Exception) -> Unit) = when (lamp) {
        Lamp.HIGH_BEAM -> light.setHighBeamLightSwitch(value, success, failure)
        Lamp.LOW_BEAM -> light.setLowBeamLightSwitch(value, success, failure)
        Lamp.TAIL -> light.setTailLampLightSwitch(value, success, failure)
        Lamp.FRONT_FOG -> light.setFrontFogLightSwitch(value, success, failure)
        Lamp.REAR_FOG -> light.setRearFogLightSwitch(value, success, failure)
    }

    private fun getState(light: Light, lamp: Lamp, success: (LightState?) -> Unit, failure: (Exception) -> Unit) = when (lamp) {
        Lamp.HIGH_BEAM -> light.getCurrentHighBeamLightState(success, failure)
        Lamp.LOW_BEAM -> light.getCurrentLowBeamLightState(success, failure)
        Lamp.TAIL -> light.getCurrentTailLampLightState(success, failure)
        Lamp.FRONT_FOG -> light.getCurrentFrontFogLightState(success, failure)
        Lamp.REAR_FOG -> light.getCurrentRearFogLightState(success, failure)
    }

    private fun registerLight(light: Light, lamp: Lamp, callback: LightStateListener) = when (lamp) {
        Lamp.HIGH_BEAM -> light.registerHighBeamLightState(callback)
        Lamp.LOW_BEAM -> light.registerLowBeamLightState(callback)
        Lamp.TAIL -> light.registerTailLampLightState(callback)
        Lamp.FRONT_FOG -> light.registerFrontFogLightState(callback)
        Lamp.REAR_FOG -> light.registerRearFogLightState(callback)
    }

    private fun unregisterLight(light: Light, lamp: Lamp, callback: LightStateListener) = when (lamp) {
        Lamp.HIGH_BEAM -> light.unregisterHighBeamLightState(callback)
        Lamp.LOW_BEAM -> light.unregisterLowBeamLightState(callback)
        Lamp.TAIL -> light.unregisterTailLampLightState(callback)
        Lamp.FRONT_FOG -> light.unregisterFrontFogLightState(callback)
        Lamp.REAR_FOG -> light.unregisterRearFogLightState(callback)
    }
}
