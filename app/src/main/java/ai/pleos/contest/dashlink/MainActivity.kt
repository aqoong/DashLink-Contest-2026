package ai.pleos.contest.dashlink

import android.app.Activity
import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {
    private enum class Screen { CONTACT, CALL, LOCATION, VEHICLE, DETAIL, SETTINGS, DELETE_CONFIRM, DEMO, OUTPUT, WAITING, COUNTDOWN, SIGNAL, RESULT, RECOVERY }
    private data class ContactDraft(val name: String, val number: String)
    private lateinit var ui: DashboardView
    private lateinit var settings: UserSettings
    private var screen = Screen.CONTACT
    private var editing = false
    private var contact: EmergencyContact? = null
    private var contactError = false
    private var draftName = ""
    private var draftNumber = ""
    private var contactNameInput: EditText? = null
    private var contactNumberInput: EditText? = null
    private val main = Handler(Looper.getMainLooper())
    private val pulseHandler = Handler(Looper.getMainLooper())
    private val incident = IncidentController()
    private var vehicle: PleosVehicleGateway? = null
    private var location: PleosLocationGateway? = null
    private var car: Car? = null
    private var ux: CarUxRestrictionsManager? = null
    private var player: SosPlayback? = null
    private var visible = false
    private var closing = false
    private var sdkGeneration = 0
    private var uxGeneration = 0
    private var uxAllowed = false
    private var uxNotice = "차량의 화면 사용 조건을 확인하고 있어요."
    private var locationConsent = false
    private var fix: PositionFix? = null
    private var airbagAvailable = false
    private var lamps = emptyList<LampCapability>()
    private val airbags = linkedMapOf<String, Boolean?>()
    private val lampStates = mutableMapOf<Lamp, String>()
    private val readings = linkedMapOf<String, VehicleReading>()
    private var group = "doors"
    private var previewReadings = false
    private var diagram: VehicleDiagramView? = null
    private var details: LinearLayout? = null
    private var countdown: TextView? = null
    private var signalView: MorseProgressView? = null
    private var signalProgress = MorseProgress()
    private var inlineMessageShown = false
    private val messageButtons = linkedMapOf<MorseMessage, Button>()
    private val messagePanels = linkedMapOf<MorseMessage, LinearLayout>()
    private val outputInputs = mutableListOf<View>()
    private var messageResult: TextView? = null
    private var messageRecovery: Button? = null
    private var recovering = false
    private var recoveryNotice = ""
    private var requestText: TextView? = null
    private var observedText: TextView? = null
    private var signalDetail = ""
    private var resultTitle = "재생 완료"
    private var outcome: DemoOutcome? = null
    private var scenario = DemoScenario.OFFLINE
    private var sdkOutput = false
    private var fog = false
    private var selectedLamp: Lamp? = null
    private var message = MorseMessage.SOS
    private var testActive = false
    private var cancelled = false
    private var interruptedByStop = false
    private var testGeneration = 0
    private var pendingPermission = 0
    private val emulator = Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish")
    private val recoveryRequired get() = vehicle?.recoveryRequired ?: getPreferences(MODE_PRIVATE).getBoolean("lamp_session_open", false)
    private val signalBusy get() = testActive || player?.busy == true
    private val tick = object : Runnable {
        override fun run() {
            if (!visible) return
            val before = incident.phase
            incident.tick(SystemClock.elapsedRealtime())
            if (incident.phase == IncidentPhase.SIGNALING && before != IncidentPhase.SIGNALING) beginEmergency()
            countdown?.text = "${incident.secondsRemaining(SystemClock.elapsedRealtime())}"
            main.postDelayed(this, 200)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = UserSettings(this)
        loadContact()
        closeLocation()
        screen = when (settings.step) { 0 -> Screen.CONTACT; 1 -> Screen.CALL; 2 -> Screen.LOCATION; else -> Screen.VEHICLE }
        savedInstanceState?.let { saved ->
            screen = Screen.entries.firstOrNull { it.name == saved.getString("screen") } ?: screen
            group = saved.getString("group", "doors")
            editing = saved.getBoolean("editing")
            message = MorseMessage.entries.firstOrNull { it.name == saved.getString("message") } ?: MorseMessage.SOS
            scenario = DemoScenario.entries.firstOrNull { it.name == saved.getString("scenario") } ?: DemoScenario.OFFLINE
            fog = saved.getBoolean("fog")
            if (saved.getBoolean("interrupted")) {
                interruptedByStop = true
                screen = if (message == MorseMessage.SOS) Screen.RESULT else Screen.VEHICLE
                inlineMessageShown = message != MorseMessage.SOS
                resultTitle = "화면 변경으로 중지됨"
                signalDetail = "자동으로 다시 시작하지 않아요. 조명 복귀를 확인하지 못하면 추가 출력은 잠겨요."
            } else if (screen == Screen.RESULT) {
                // Result details belong to the old session; never restore a success claim without its evidence.
                screen = if (message == MorseMessage.SOS) Screen.DEMO else Screen.VEHICLE
            }
        }
        (lastNonConfigurationInstance as? ContactDraft)?.let { draft ->
            draftName = draft.name; draftNumber = draft.number
        }
        render()
    }
    override fun onRetainNonConfigurationInstance(): Any = ContactDraft(draftName, draftNumber)
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("screen", screen.name)
        outState.putString("group", group)
        outState.putBoolean("editing", editing)
        outState.putString("message", message.name)
        outState.putString("scenario", scenario.name)
        outState.putBoolean("fog", fog)
        outState.putBoolean("interrupted", testActive || player?.busy == true || (interruptedByStop && (screen == Screen.RESULT || screen == Screen.VEHICLE && inlineMessageShown)) || screen in listOf(Screen.WAITING, Screen.COUNTDOWN, Screen.SIGNAL))
        super.onSaveInstanceState(outState)
    }
    override fun onStart() {
        super.onStart()
        visible = true
        if (!closing) openConnections()
        main.post(tick)
    }
    override fun onStop() {
        saveDraft()
        visible = false
        main.removeCallbacks(tick)
        testGeneration++
        val inlineActive = screen == Screen.VEHICLE && (testActive || player?.busy == true)
        testActive = false
        incident.disarm()
        if (inlineActive || screen in listOf(Screen.WAITING, Screen.COUNTDOWN, Screen.SIGNAL)) {
            interruptedByStop = true
            cancelled = true
            resultTitle = "앱을 떠나 중지됨"
            signalDetail = "자동으로 다시 시작하지 않아요."
            screen = if (inlineActive) Screen.VEHICLE else Screen.RESULT
        }
        locationConsent = false
        closeLocation()
        closeUx()
        closing = true
        if (player?.busy == true) player?.stop() else if (!recovering) finishClosing()
        if (!isChangingConfigurations) { draftNumber = ""; draftName = "" }
        super.onStop()
    }
    private fun loadContact() {
        try { contact = settings.contact(); contactError = false }
        catch (_: Exception) { contact = null; contactError = true }
    }
    private fun saveDraft() {
        val name = contactNameInput
        val number = contactNumberInput
        if (screen == Screen.CONTACT && name != null && number != null) {
            draftName = name.text.toString()
            draftNumber = number.text.toString()
        }
    }
    private fun navigate(next: Screen) {
        if (player?.busy == true || recovering) return
        saveDraft()
        if (next != Screen.VEHICLE && next != Screen.DETAIL) previewReadings = false
        if (next in listOf(Screen.VEHICLE, Screen.SETTINGS, Screen.DEMO)) inlineMessageShown = false
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(ui.windowToken, 0)
        screen = next
        render()
        if (next == Screen.VEHICLE && settings.step == 3) ensureReadPermissions()
    }
    private fun button(label: String, primary: Boolean = false, action: () -> Unit) = ui.button(label, primary) {
        if (visible && uxAllowed && !closing) action() else toast("정차 상태를 확인한 뒤 다시 시도해 주세요")
    }
    private fun stopButton(label: String, action: () -> Unit) = ui.dangerButton(label) {
        if (visible && uxAllowed && !closing) action()
    }
    private fun render(preserveScroll: Boolean = false) {
        if (isFinishing || isDestroyed) return
        val previousScroll = if (preserveScroll && ::ui.isInitialized) (ui.content.parent as? ScrollView)?.scrollY ?: 0 else 0
        diagram = null; details = null; countdown = null; signalView = null; requestText = null; observedText = null
        messageButtons.clear(); messagePanels.clear(); outputInputs.clear(); messageResult = null; messageRecovery = null
        contactNameInput = null; contactNumberInput = null
        ui = DashboardView(this)
        setContentView(ui)
        if (!uxAllowed) {
            ui.title("정차 후 이용해 주세요", uxNotice)
            return
        }
        ui.badge.text = when {
            previewReadings -> "모의 데이터 · 실제 조회 아님"
            message != MorseMessage.SOS && signalBusy -> "에뮬레이터 · 일반 메시지"
            testActive || screen in listOf(Screen.SIGNAL, Screen.RESULT) -> if (scenario == DemoScenario.VEHICLE && message == MorseMessage.SOS) "에뮬레이터 시험 · 차량 연결 신호" else "모의테스트 · 실제 신고 없음"
            else -> "PLEOS · 에뮬레이터"
        }
        if (screen in listOf(Screen.DETAIL, Screen.SETTINGS, Screen.DELETE_CONFIRM, Screen.DEMO, Screen.OUTPUT, Screen.RECOVERY)) {
            ui.header.addView(button("뒤로") { onBackPressed() }, LinearLayout.LayoutParams(ui.dp(96), ui.dp(76)).apply { leftMargin = ui.dp(16) })
        }
        when (screen) {
            Screen.CONTACT -> contactScreen()
            Screen.CALL -> callScreen()
            Screen.LOCATION -> locationScreen()
            Screen.VEHICLE -> vehicleScreen()
            Screen.DETAIL -> detailScreen()
            Screen.SETTINGS -> settingsScreen()
            Screen.DELETE_CONFIRM -> deleteScreen()
            Screen.DEMO -> demoScreen()
            Screen.OUTPUT -> outputScreen()
            Screen.WAITING -> waitingScreen()
            Screen.COUNTDOWN -> countdownScreen()
            Screen.SIGNAL -> signalScreen()
            Screen.RESULT -> resultScreen()
            Screen.RECOVERY -> recoveryScreen()
        }
        if (preserveScroll) (ui.content.parent as? ScrollView)?.let { scroll -> scroll.post { scroll.scrollTo(0, previousScroll) } }
    }
    private fun contactScreen() {
        ui.title("긴급연락처", "선택 사항 · 기기에 암호화 저장해요.")
        val name = ui.input("이름 또는 별칭 (선택)", draftName.ifBlank { if (editing) contact?.name.orEmpty() else "" })
        val number = ui.input("전화번호", draftNumber.ifBlank { if (editing) contact?.number.orEmpty() else "" }).apply { inputType = InputType.TYPE_CLASS_PHONE }
        contactNameInput = name; contactNumberInput = number
        ui.add(name, 24); ui.add(number, 8)
        ui.add(ui.text("실제 연락은 하지 않아요.", 15, ui.muted))
        if (contactError) ui.add(ui.text("저장된 연락처를 읽지 못했어요. 다시 등록하거나 삭제해 주세요.", 16))
        ui.add(ui.actions(button(if (editing) "뒤로" else "건너뛰기") {
            if (editing) navigate(Screen.SETTINGS) else saveSetting { settings.step = 1; navigate(Screen.CALL) }
        }, button(if (editing) "저장" else "저장하고 다음", true) {
            if (normalizedContact(number.text.toString()) == null) number.error = "올바른 전화번호를 입력해 주세요"
            else saveSetting {
                settings.saveContact(name.text.toString(), number.text.toString()); loadContact()
                draftName = ""; draftNumber = ""
                if (editing) navigate(Screen.SETTINGS) else { settings.step = 1; navigate(Screen.CALL) }
            }
        }), 28)
        if (editing && (contact != null || contactError)) ui.add(button("긴급연락처 삭제") { saveSetting { settings.deleteContact(); loadContact(); draftName = ""; draftNumber = ""; navigate(Screen.SETTINGS) } })
    }
    private fun callScreen() {
        ui.title("119 긴급신고", "현재는 모의 신고이며 실제 전화하지 않아요.")
        ui.add(ui.toggle("119 긴급신고 허용", settings.callAllowed) { allowed ->
            saveSetting { settings.callAllowed = allowed }
        }, 36)
        ui.add(button(if (editing) "저장" else "다음", true) {
            saveSetting { if (editing) navigate(Screen.SETTINGS) else { settings.step = 2; navigate(Screen.LOCATION) } }
        }, 30)
        ui.add(button("뒤로") { navigate(if (editing) Screen.SETTINGS else Screen.CONTACT) })
    }
    private fun locationScreen() {
        ui.title("위치 사용", "앱 사용 중 사고 위치 확인에 사용해요.")
        ui.add(ui.card("저장·외부 전송 없음", "앱을 떠나면 위치를 지워요."), 32)
        ui.add(ui.actions(button(if (locationConsent) "위치 사용 중지" else "나중에") {
            locationConsent = false; closeLocation(); finishLocationStep()
        }, button("동의하고 위치 조회", true) {
            locationConsent = true; enableLocation(); finishLocationStep()
        }), 30)
        if (checkSelfPermission(PleosLocationGateway.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            ui.add(button("시스템 권한 설정") {
                startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:$packageName")))
            })
        }
    }
    private fun finishLocationStep() = saveSetting {
        if (!editing) settings.step = 3
        navigate(if (editing) Screen.SETTINGS else Screen.VEHICLE)
    }
    private fun vehicleScreen() {
        val refresh = ui.iconButton("정보 다시 확인", R.drawable.ic_refresh) {
            if (visible && uxAllowed && !closing && !signalBusy && !recovering) {
                inlineMessageShown = false
                ensureReadPermissions(); reconnect(); location?.refresh(); render(preserveScroll = true)
            }
        }
        val settingsButton = ui.iconButton("설정", R.drawable.ic_settings) {
            if (visible && uxAllowed && !closing && !signalBusy && !recovering) { editing = true; navigate(Screen.SETTINGS) }
        }
        listOf(refresh, settingsButton).forEach { action ->
            ui.header.addView(action, LinearLayout.LayoutParams(ui.dp(76), ui.dp(76)).apply { leftMargin = ui.dp(8) })
            outputInputs.add(action)
        }
        refresh.visibility = if (previewReadings) View.GONE else View.VISIBLE
        val carView = VehicleDiagramView(this) { selected -> group = selected; navigate(Screen.DETAIL) }
        diagram = carView
        val vehicleSection = ui.column().apply {
            background = ui.shape(ui.surface, 28)
            setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16))
            addView(carView, LinearLayout.LayoutParams(-1, ui.dp(if (resources.configuration.screenWidthDp >= 800) 400 else 460)))
        }
        ui.add(vehicleSection, 16)
        if (previewReadings) ui.add(button("모의 데이터 종료", true) { previewReadings = false; navigate(Screen.VEHICLE) }, 20)
        else messagesSection()
        refreshReadingsUi()
    }
    private fun displayedReadings(): List<VehicleReading> = if (!previewReadings) readings.values.toList() else listOf(
        VehicleReading("demo.door", "doors", "운전석 문", ReadStatus.READABLE, "닫힘", "모의 데이터"),
        VehicleReading("demo.tire", "tires", "왼쪽 앞 타이어", ReadStatus.FAILED, detail = "예시: 응답 시간 초과"),
        VehicleReading("demo.battery", "battery", "EV 배터리 충전량", ReadStatus.READABLE, "72 %", "모의 데이터"),
        VehicleReading("demo.airbag", "airbag", "에어백", ReadStatus.UNSUPPORTED, detail = "예시: 해당 정보 미지원"),
        VehicleReading("demo.lamp", "lights", "상향등", ReadStatus.READABLE, "꺼짐", "모의 데이터"),
        VehicleReading("demo.location", "location", "위치", ReadStatus.WAITING, detail = "이 예시에서는 위치를 사용하지 않아요"),
    )
    private fun detailScreen() {
        ui.title(groupTitle(group))
        details = ui.column().also { ui.add(it, 24) }
        if (group == "location") ui.add(button("위치 사용 설정") { editing = true; navigate(Screen.LOCATION) })
        ui.add(ui.actions(button("홈으로", true) { navigate(Screen.VEHICLE) }, button("다시 확인") {
            if (previewReadings) toast("모의 데이터예요. 종료 후 실제 정보를 조회해 주세요")
            else { ensureReadPermissions(); reconnect(); location?.refresh() }
        }))
        refreshReadingsUi()
    }
    private fun refreshReadingsUi() {
        if (!visible) return
        val values = displayedReadings()
        diagram?.updateReadings(values, connecting = vehicle != null && !previewReadings)
        details?.let { box ->
            box.removeAllViews()
            val items = values.filter { it.group == group }.sortedBy { it.label }
            if (items.isEmpty()) box.addView(ui.card("확인 중", "차량의 응답을 기다리고 있어요."))
            items.forEach { reading ->
                val state = when (reading.status) {
                    ReadStatus.READABLE -> "✓ 조회됨"
                    ReadStatus.FAILED -> "! 조회 안 됨"
                    ReadStatus.UNSUPPORTED -> "— 정보 미지원"
                    ReadStatus.LOADING -> "○ 확인 중"
                    ReadStatus.WAITING -> "○ 확인 전"
                }
                val color = when (reading.status) {
                    ReadStatus.READABLE -> Color.parseColor(if (ui.night) "#6EE7B7" else "#188052")
                    ReadStatus.FAILED -> Color.parseColor(if (ui.night) "#FCA5A5" else "#C1373D")
                    ReadStatus.UNSUPPORTED -> Color.parseColor(if (ui.night) "#FCD34D" else "#906300")
                    else -> ui.muted
                }
                val explanation = when {
                    reading.status == ReadStatus.READABLE && reading.group == "doors" -> ""
                    reading.status == ReadStatus.READABLE && reading.group == "location" -> "수신한 위치예요. 최신성과 정확도는 확인되지 않았어요."
                    else -> reading.detail
                }
                val card = ui.card(reading.label, listOf(reading.value, explanation).filter { it.isNotBlank() }.joinToString("\n"))
                card.addView(ui.text(state, 16, color, true))
                if (reading.receivedAt > 0) {
                    val wallTime = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - reading.receivedAt).coerceAtLeast(0)
                    card.addView(ui.text("수신 ${android.text.format.DateFormat.format("HH:mm:ss", wallTime)}", 13, ui.muted))
                }
                box.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(12) })
            }
        }
    }
    private fun putReading(reading: VehicleReading) {
        val before = readings.put(reading.id, reading)
        // Repeated unchanged SDK samples must not redraw the whole page or flood accessibility events.
        if (before?.copy(receivedAt = 0) != reading.copy(receivedAt = 0)) refreshReadingsUi()
    }
    private fun settingsScreen() {
        ui.title("설정")
        ui.add(ui.card("긴급연락처", if (contactError) "저장 정보 확인 필요" else contact?.masked ?: "등록하지 않음") { editing = true; draftName = ""; draftNumber = ""; navigate(Screen.CONTACT) })
        ui.add(ui.card("119 긴급신고", if (settings.callAllowed) "허용 · 현재는 모의 신고" else "사용 안 함") { editing = true; navigate(Screen.CALL) }, 10)
        ui.add(ui.card("위치 사용", if (locationConsent) "사용 중" else "사용 안 함") { editing = true; navigate(Screen.LOCATION) }, 10)
        outputControls()
        ui.add(ui.actions(button("모의테스트", true) { navigate(Screen.DEMO) }, button("홈으로") { navigate(Screen.VEHICLE) }), 20)
        ui.add(button("저장한 정보 삭제") { navigate(Screen.DELETE_CONFIRM) }, 20)
    }
    private fun deleteScreen() {
        ui.title("저장 정보를 삭제할까요?", "연락처와 신고 설정을 삭제해요.")
        if (recoveryRequired) ui.add(ui.card("조명 안전 잠금은 유지돼요", ""))
        ui.add(button("취소") { navigate(Screen.SETTINGS) })
        ui.fixedAction(stopButton("저장한 정보 삭제") { saveSetting {
            settings.clear(); loadContact(); editing = false; draftName = ""; draftNumber = ""
            locationConsent = false; closeLocation(); navigate(Screen.CONTACT)
        } })
    }
    private fun demoScreen() {
        ui.title("모의테스트", "실제 신고·외부 전송 없음")
        DemoScenario.entries.forEach { item ->
            ui.add(ui.card(item.title, item.detail) {
                scenario = item
                if (item == DemoScenario.READINGS) { previewReadings = true; navigate(Screen.VEHICLE) }
                else { message = MorseMessage.SOS; sdkOutput = false; outcome = null; navigate(Screen.OUTPUT) }
            }, 12)
        }
        ui.add(button("설정으로 돌아가기") { navigate(Screen.SETTINGS) })
    }
    private fun outputControls() {
        val previewButton = button(if (!sdkOutput) "✓ 화면 미리보기" else "화면 미리보기") {
            if (signalBusy) return@button
            sdkOutput = false; inlineMessageShown = false; render(preserveScroll = true)
        }
        val sdkButton = button(if (sdkOutput) "✓ 에뮬레이터 조명" else "에뮬레이터 조명") {
                if (signalBusy) return@button
                sdkOutput = true
                inlineMessageShown = false
                if (checkSelfPermission(CONTROL_PERMISSION) != PackageManager.PERMISSION_GRANTED) requestOnePermissionSet(arrayOf(CONTROL_PERMISSION), CONTROL_REQUEST)
                render(preserveScroll = true)
            }
        outputInputs.addAll(listOf(previewButton, sdkButton))
        ui.add(ui.actions(previewButton, sdkButton), 24)
        if (sdkOutput) {
            val eligible = lamps.filter { it.writable && (!fog || it.lamp != Lamp.HIGH_BEAM) }
            if (selectedLamp !in eligible.map { it.lamp }) selectedLamp = eligible.firstOrNull()?.lamp
            if (eligible.isEmpty()) ui.add(ui.card("사용 가능한 조명을 확인하지 못했어요", "지원과 권한을 확인한 뒤 다시 시도해 주세요."))
            else {
                val picker = Spinner(this)
                picker.adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, eligible.map { it.label }) {
                    override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                        super.getView(position, convertView, parent).apply { (this as TextView).textSize = 22f; setTextColor(ui.ink) }
                    override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                        super.getDropDownView(position, convertView, parent).apply {
                            (this as TextView).textSize = 22f; setTextColor(ui.ink); setBackgroundColor(ui.backgroundColor); minimumHeight = ui.dp(76)
                        }
                }
                picker.setSelection(eligible.indexOfFirst { it.lamp == selectedLamp }.coerceAtLeast(0))
                picker.minimumHeight = ui.dp(76)
                picker.contentDescription = "출력할 조명"
                picker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        if (!signalBusy) {
                            val chosen = eligible.getOrNull(position)?.lamp
                            if (chosen != selectedLamp) {
                                selectedLamp = chosen; inlineMessageShown = false; showInlineMessage()
                            }
                        }
                    }
                }
                outputInputs.add(picker)
                ui.add(picker)
            }
            if (recoveryRequired) ui.add(ui.card("조명 복구 필요", "추가 SDK 출력 잠금") { navigate(Screen.RECOVERY) })
        }
    }
    private fun outputScreen() {
        ui.title(scenario.title)
        outputControls()
        ui.add(ui.toggle("안개 상황", fog) { checked -> fog = checked; if (checked) selectedLamp = lamps.firstOrNull { it.writable && it.lamp == Lamp.REAR_FOG }?.lamp; render() })
        ui.add(ui.text("119 ${if (settings.callAllowed) "모의 신고 허용" else "사용 안 함"} · 연락처 ${if (contact == null) "미등록" else "등록됨"} · 위치 ${if (fix == null) "미확보" else "수신됨"}", 15, ui.muted))
        ui.add(ui.actions(button("뒤로") { navigate(Screen.DEMO) }, button("시험 시작", true) { startTest() }), 28)
    }
    private fun messagesSection() {
        val narrow = resources.configuration.screenWidthDp < 720
        val choices = if (narrow) ui.column() else ui.row().apply { gravity = android.view.Gravity.TOP }
        listOf(MorseMessage.OK, MorseMessage.THANKS, MorseMessage.SORRY).forEachIndexed { index, item ->
            val card = ui.column()
            val action = button(item.title, true) {
                if (signalBusy) {
                    if (message == item && !cancelled) cancelTest("중지됨")
                    return@button
                }
                if (!canOutput()) return@button
                scenario = DemoScenario.ONLINE; outcome = null; message = item; cancelled = false; testActive = true
                startSignal(item)
            }.apply {
                stateListAnimator = null
                elevation = 0f
            }
            messageButtons[item] = action
            card.addView(action, LinearLayout.LayoutParams(-1, -2))
            messagePanels[item] = ui.column().also { panel -> card.addView(panel, LinearLayout.LayoutParams(-1, -2)) }
            val params = if (narrow) LinearLayout.LayoutParams(-1, -2) else LinearLayout.LayoutParams(0, -2, 1f)
            if (index > 0) { if (narrow) params.topMargin = ui.dp(12) else params.leftMargin = ui.dp(12) }
            choices.addView(card, params)
        }
        val messageSection = ui.column().apply {
            background = ui.shape(if (ui.night) Color.parseColor("#283446") else Color.WHITE, 28)
            val padding = ui.dp(if (narrow) 16 else 24)
            setPadding(padding, padding, padding, padding)
            addView(choices, LinearLayout.LayoutParams(-1, -2))
        }
        ui.add(messageSection, 64)
        showInlineMessage()
    }
    private fun showInlineMessage() {
        if (screen != Screen.VEHICLE) return
        signalView = null; requestText = null; observedText = null; messageResult = null; messageRecovery = null
        messagePanels.forEach { (item, panel) ->
            panel.removeAllViews()
            panel.visibility = if (inlineMessageShown && item == message) View.VISIBLE else View.GONE
            if (panel.visibility == View.VISIBLE) {
                signalView = MorseProgressView(this, item, ui.blue, ui.muted).also { panel.addView(it, LinearLayout.LayoutParams(-1, -2)) }
                requestText = ui.text("", 18, ui.ink, true).also { panel.addView(it) }
                observedText = ui.text("", 18, ui.muted).also { panel.addView(it) }
                messageResult = ui.text("", 18, ui.muted).also { panel.addView(it) }
                messageRecovery = button("조명 복구") { recoveryNotice = ""; navigate(Screen.RECOVERY) }.also {
                    it.stateListAnimator = null
                    it.elevation = 0f
                    panel.addView(it)
                }
            }
        }
        updateSignalUi()
    }
    private fun canOutput(): Boolean {
        val reason = when {
            !visible || !uxAllowed || closing -> "정차 후 이용해 주세요"
            player?.busy == true || recovering -> "현재 신호가 끝난 뒤 다시 눌러 주세요"
            !emulator -> "이 버전은 Pleos 에뮬레이터에서만 시험해요"
            sdkOutput && recoveryRequired -> "이전 조명 복귀를 먼저 확인해야 해요"
            sdkOutput && checkSelfPermission(CONTROL_PERMISSION) != PackageManager.PERMISSION_GRANTED -> "조명 제어 권한이 필요해요"
            sdkOutput && (vehicle == null || lamps.none { it.lamp == selectedLamp && it.writable }) -> "제어할 조명을 확인하지 못했어요"
            sdkOutput && fog && selectedLamp == Lamp.HIGH_BEAM -> "안개 시나리오에서는 상향등을 사용하지 않아요"
            else -> null
        }
        if (reason != null) toast(reason)
        return reason == null
    }
    private fun startTest() {
        if (!canOutput()) return
        if (scenario == DemoScenario.VEHICLE && (!airbagAvailable || airbags.isEmpty() || airbags.values.any { it != false })) {
            toast("지원되는 에어백의 초기 미전개 상태를 확인하지 못했어요"); return
        }
        incident.disarm()
        airbags.forEach { (seat, deployed) -> incident.observeAirbag(seat, deployed, SystemClock.elapsedRealtime()) }
        incident.arm()
        testActive = true; cancelled = false; interruptedByStop = false; message = MorseMessage.SOS; outcome = null
        screen = Screen.WAITING; render()
        val token = ++testGeneration
        if (scenario != DemoScenario.VEHICLE) main.postDelayed({
            if (visible && uxAllowed && testActive && token == testGeneration && incident.phase == IncidentPhase.READY) {
                incident.simulate(SystemClock.elapsedRealtime()); screen = Screen.COUNTDOWN; render()
            }
        }, 3_000)
    }
    private fun waitingScreen() {
        ui.title("사고 신호 대기", scenario.title)
        ui.add(ui.card("10초 뒤 자동 요청", "사고 감지 후 응답이 없을 때"), 40)
        ui.fixedAction(stopButton("■ 시험 종료") { cancelTest("중지됨") })
    }
    private fun countdownScreen() {
        ui.title("사고 감지\n괜찮으신가요?")
        countdown = ui.text("10", 100, ui.danger, true).also { it.gravity = android.view.Gravity.CENTER; ui.add(it, 24, ui.dp(150)) }
        ui.fixedAction(stopButton("■ 괜찮아요 · 요청 취소") { cancelTest("도움 요청을 취소했어요") })
    }
    private fun beginEmergency() {
        outcome = demoOutcome(scenario, settings.callAllowed, contact != null, fix != null && locationConsent)
        if (!canOutput()) { signalDetail = "조명 출력 조건을 확인하지 못했어요"; incident.finish(); screen = Screen.RESULT; testActive = false; render(); return }
        startSignal(MorseMessage.SOS)
    }
    private fun startSignal(chosenMessage: MorseMessage) {
        if (!canOutput()) return
        interruptedByStop = false
        message = chosenMessage; signalDetail = "준비 중"; signalProgress = MorseProgress()
        if (chosenMessage == MorseMessage.SOS) { screen = Screen.SIGNAL; render() }
        else { inlineMessageShown = true; showInlineMessage() }
        val endpoint = vehicle
        val lamp = selectedLamp
        val usingSdk = sdkOutput
        val playback = SosPlayback(
            schedule = { delay, task -> pulseHandler.postDelayed({ task() }, delay) },
            cancelScheduled = { pulseHandler.removeCallbacksAndMessages(null) },
            capture = { done -> if (usingSdk && endpoint != null && lamp != null) endpoint.captureLamp(lamp, done) else done(true, "화면 미리보기") },
            write = { on, done -> if (usingSdk && endpoint != null && lamp != null) endpoint.setLamp(lamp, on, done) else done(true, "화면 미리보기") },
            restore = { done -> if (usingSdk && endpoint != null) endpoint.restoreLamp(done) else done(true, "화면 미리보기 종료") },
            onUpdate = { detail, _ -> signalDetail = detail; updateSignalUi() },
            onFinished = { restored ->
                val complete = player?.completedSignal == true && !cancelled
                val latchCleared = !usingSdk || !recoveryRequired
                signalDetail = when {
                    !latchCleared -> "조명 복구 필요 · SDK 출력 잠금"
                    !restored -> signalDetail
                    cancelled -> if (usingSdk) "신호 중지 · 원래 스위치 복귀 확인" else "화면 재생을 중지했어요"
                    !complete -> "신호 요청 실패 · ${if (usingSdk) "스위치 복귀 확인" else "화면 재생 종료"}"
                    usingSdk -> "요청 완료 · 스위치 복귀 확인\n실제 점멸 미확인"
                    else -> "화면 재생 완료"
                }
                incident.finish(); incident.disarm(); testActive = false
                resultTitle = if (cancelled) "중지됨" else if (complete && restored && latchCleared) "재생 완료" else "요청 결과를 확인해 주세요"
                screen = if (chosenMessage == MorseMessage.SOS) Screen.RESULT else Screen.VEHICLE
                if (closing || !visible) finishClosing()
                else if (chosenMessage == MorseMessage.SOS) render() else updateSignalUi()
            },
            pulses = MorseCode.sequence(chosenMessage), signalLabel = chosenMessage.wireText,
            onProgress = { signalProgress = it; updateSignalUi() },
        )
        player = playback
        playback.start()
        if (chosenMessage != MorseMessage.SOS) {
            messageButtons[chosenMessage]?.let { selected ->
                selected.post { selected.requestRectangleOnScreen(android.graphics.Rect(0, 0, selected.width, selected.height + ui.dp(220)), false) }
            }
        }
    }
    private fun signalScreen() {
        ui.header.visibility = View.GONE
        ui.badge.visibility = View.GONE
        ui.content.setPadding(ui.content.paddingLeft, 0, ui.content.paddingRight, ui.dp(8))
        ui.title(if (message == MorseMessage.SOS) "SOS · 3회" else if (message == MorseMessage.OK) "OK" else "${message.title} · ${message.wireText}")
        signalView = MorseProgressView(this, message, if (message == MorseMessage.SOS) ui.danger else ui.blue, ui.muted).also {
            it.background = ui.shape(ui.surface, 20)
            ui.add(it)
        }
        requestText = ui.text("", 20, ui.ink, true).also { ui.add(it) }
        observedText = ui.text("", 16, ui.muted).also { ui.add(it) }
        outcome?.let { ui.add(ui.card("119 · 긴급연락처", "${it.call}\n${it.guardian}")) }
        ui.fixedAction(stopButton("■ 중지") { cancelTest("중지됨") })
        updateSignalUi()
    }
    private fun updateSignalUi() {
        if (!visible || screen !in listOf(Screen.SIGNAL, Screen.VEHICLE)) return
        signalView?.update(signalProgress)
        val total = MorseCode.sequence(message).count { it.on }
        requestText?.text = "${if (sdkOutput) "SDK 요청" else "미리보기"} ${signalProgress.completedSymbols} / $total"
        requestText?.visibility = if (interruptedByStop) View.GONE else View.VISIBLE
        observedText?.text = if (sdkOutput) "실제 조명 · ${selectedLamp?.let { lampStates[it] } ?: "미확인"}" else ""
        observedText?.visibility = if (sdkOutput && !interruptedByStop) View.VISIBLE else View.GONE
        if (screen == Screen.VEHICLE) {
            messageButtons.forEach { (item, action) ->
                val active = signalBusy && item == message
                ui.styleMessageButton(action, stopping = active)
                action.text = if (active) "${item.title} · ${if (cancelled) "중지 중" else "■ 중지"}" else item.title
                action.isEnabled = !signalBusy || (active && !cancelled)
                action.alpha = if (action.isEnabled) 1f else 0.45f
            }
            outputInputs.forEach { it.isEnabled = !signalBusy; it.alpha = if (signalBusy) 0.45f else 1f }
            messageResult?.text = if (signalBusy) "" else signalDetail
            messageResult?.visibility = if (signalBusy) View.GONE else View.VISIBLE
            messageRecovery?.visibility = if (!signalBusy && recoveryRequired) View.VISIBLE else View.GONE
        }
    }
    private fun recoveryScreen() {
        ui.title(if (recoveryRequired) "조명 복구" else "조명 복구 확인")
        ui.add(ui.card(if (recovering) "복구 확인 중" else if (recoveryRequired) "SDK 출력 잠금" else "SDK 출력 가능",
            recoveryNotice.ifBlank { vehicle?.recoveryDetail ?: "차량 연결 확인 중" }))
        if (recoveryRequired && !recovering) ui.add(button("정차 상태에서 복구", true) {
            val endpoint = vehicle ?: return@button
            recovering = true; recoveryNotice = ""; render()
            endpoint.recoverLamp { _, detail ->
                recovering = false; recoveryNotice = detail
                if (!recoveryRequired) inlineMessageShown = false
                if (closing || !visible) finishClosing() else render()
            }
        })
        if (!recovering) ui.add(button("홈으로") { navigate(Screen.VEHICLE) })
    }
    private fun resultScreen() {
        ui.title(resultTitle)
        outcome?.let { ui.add(ui.card("119", it.call)); ui.add(ui.card("긴급연락처", it.guardian), 10) }
        ui.add(ui.card(if (message == MorseMessage.SOS) "SOS 신호" else if (message == MorseMessage.OK) "OK" else "${message.title} · ${message.wireText}", signalDetail.ifBlank { "조명 요청 전 취소했어요" }))
        if (recoveryRequired) ui.add(button("조명 복구") { recoveryNotice = ""; navigate(Screen.RECOVERY) })
        ui.add(ui.actions(button("홈으로", true) { navigate(Screen.VEHICLE) }, button(if (message == MorseMessage.SOS) "다른 모의테스트" else "다른 메시지") { navigate(if (message == MorseMessage.SOS) Screen.DEMO else Screen.VEHICLE) }), 26)
    }
    private fun cancelTest(title: String) {
        testGeneration++; testActive = false; cancelled = true; resultTitle = title
        incident.cancel(); incident.disarm()
        if (player?.busy == true) { player?.stop(); updateSignalUi() } else {
            signalDetail = "조명 요청 전 취소했어요 · 실제 신고 없음"
            if (message == MorseMessage.SOS) { screen = Screen.RESULT; render() } else updateSignalUi()
        }
    }
    private fun openConnections() {
        if (!visible || closing) return
        openUx()
        if (uxAllowed && settings.step == 3) {
            openVehicle()
            if (locationConsent) enableLocation()
        }
    }
    private fun ensureReadPermissions() {
        val missing = READ_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestOnePermissionSet(missing.toTypedArray(), VEHICLE_REQUEST) else if (vehicle == null) openVehicle()
    }
    private fun requestOnePermissionSet(permissions: Array<String>, code: Int) {
        if (pendingPermission != 0) return
        pendingPermission = code
        try { requestPermissions(permissions, code) }
        catch (_: Exception) { pendingPermission = 0; toast("권한 요청을 열지 못했어요. 다시 시도해 주세요.") }
    }
    private fun openVehicle() {
        if (vehicle != null || !visible || !uxAllowed || closing || settings.step < 3) return
        val token = ++sdkGeneration
        airbags.clear(); airbagAvailable = false; lamps = emptyList(); lampStates.clear()
        val endpoint = PleosVehicleGateway(this, object : PleosVehicleGateway.Listener {
            fun current() = visible && !closing && token == sdkGeneration
            override fun onConnection(ready: Boolean, detail: String) = Unit
            override fun onReading(reading: VehicleReading) { if (current()) putReading(reading) }
            override fun onAirbagAvailability(available: Boolean, detail: String) { if (current()) airbagAvailable = available }
            override fun onAirbag(seat: String, deployed: Boolean?) {
                if (!current()) return
                airbags[seat] = deployed
                if (testActive && scenario == DemoScenario.VEHICLE) {
                    val before = incident.phase
                    incident.observeAirbag(seat, deployed, SystemClock.elapsedRealtime())
                    if (before == IncidentPhase.READY && incident.phase == IncidentPhase.CHECKING) { screen = Screen.COUNTDOWN; render() }
                }
            }
            override fun onLamps(lamps: List<LampCapability>) {
                if (current()) {
                    this@MainActivity.lamps = lamps
                    if (!signalBusy && (screen == Screen.OUTPUT || screen == Screen.SETTINGS && sdkOutput)) render(preserveScroll = true)
                }
            }
            override fun onLampState(lamp: Lamp, state: String) { if (current()) { lampStates[lamp] = state; updateSignalUi() } }
            override fun onError(domain: String, detail: String) = Unit
            override fun onRecoveryChanged(required: Boolean, detail: String) {
                if (current() && screen == Screen.RECOVERY && !recovering) render()
            }
        }, getPreferences(MODE_PRIVATE)) { visible && uxAllowed && !closing }
        vehicle = endpoint; refreshReadingsUi(); endpoint.start()
    }
    private fun reconnect() {
        if (player?.busy == true || recovering || closing) return
        sdkGeneration++; vehicle?.stop(); vehicle = null
        readings.keys.filter { !it.startsWith("location") }.toList().forEach { readings.remove(it) }
        openConnections(); refreshReadingsUi()
    }
    private fun enableLocation() {
        if (!visible || !locationConsent || !uxAllowed || closing) return
        if (checkSelfPermission(PleosLocationGateway.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requestOnePermissionSet(arrayOf(PleosLocationGateway.PERMISSION), LOCATION_REQUEST); return
        }
        if (location != null) return
        readings["location"] = VehicleReading("location", "location", "차량 위치", ReadStatus.LOADING)
        location = PleosLocationGateway(this, object : PleosLocationGateway.Listener {
            override fun onStatus(ready: Boolean, detail: String) {
                if (!visible || !locationConsent) return
                val current = readings["location"]
                val updated = when {
                    detail == "이 환경은 Pleos 위치 SDK를 지원하지 않습니다" -> VehicleReading("location", "location", "차량 위치", ReadStatus.UNSUPPORTED, detail = "이 환경에서 위치 정보를 제공하지 않아요")
                    fix != null -> current?.copy(detail = detail) ?: return
                    !ready -> VehicleReading("location", "location", "차량 위치", ReadStatus.FAILED, detail = detail)
                    else -> current?.copy(detail = detail) ?: return
                }; putReading(updated)
            }
            override fun onLocation(fix: PositionFix?) {
                if (!visible || !locationConsent) return
                this@MainActivity.fix = fix
                putReading(VehicleReading("location", "location", "차량 위치", if (fix != null) ReadStatus.READABLE else ReadStatus.FAILED,
                    fix?.let { String.format(Locale.US, "%.5f, %.5f", it.latitude, it.longitude) }.orEmpty(), fix?.quality ?: "위치를 확인하지 못했어요", fix?.receivedElapsedMs ?: 0))
            }
            override fun onError(detail: String) = Unit
        }).also { it.start() }
    }
    private fun closeLocation() {
        location?.stop(); location = null; fix = null
        readings["location"] = VehicleReading("location", "location", "차량 위치", ReadStatus.WAITING, detail = "위치 사용 안 함 · 좌표는 앱을 떠나면 지워져요")
        refreshReadingsUi()
    }
    private fun openUx() {
        if (car != null) return
        val token = ++uxGeneration
        uxAllowed = false
        uxNotice = "차량의 화면 사용 조건을 확인하고 있어요."
        try {
            car = Car.createCar(this, main, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                if (!visible || closing || token != uxGeneration) { connected.disconnect(); return@createCar }
                if (!ready) { updateUx(null); return@createCar }
                try {
                    ux = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                    ux?.registerListener { if (visible && token == uxGeneration) updateUx(it) }
                    updateUx(ux?.currentCarUxRestrictions)
                } catch (_: Exception) { updateUx(null) }
            }
        } catch (_: Exception) { updateUx(null) }
    }
    private fun updateUx(value: CarUxRestrictions?) {
        if (!visible) return
        val allowed = value != null && !value.isRequiresDistractionOptimization && value.activeRestrictions == CarUxRestrictions.UX_RESTRICTIONS_BASELINE
        val changed = allowed != uxAllowed
        uxNotice = when {
            value == null -> "차량의 화면 사용 조건을 확인하지 못했어요."
            !allowed -> "차량에서 이 화면의 사용을 제한하고 있어요. 정차 후에도 계속되면 기본 차량 화면에서 다시 열어 주세요."
            else -> ""
        }
        val wasAllowed = uxAllowed
        uxAllowed = allowed
        if (wasAllowed && !allowed) {
            saveDraft()
            if (testActive || player?.busy == true) cancelTest("주행 제한으로 중지됨")
            locationConsent = false; closeLocation()
        }
        if (changed || !allowed) render()
        if (allowed && settings.step == 3 && vehicle == null) { openVehicle(); ensureReadPermissions() }
        if (allowed && locationConsent) enableLocation()
    }
    private fun closeUx() {
        uxGeneration++; runCatching { ux?.unregisterListener() }; ux = null
        runCatching { car?.disconnect() }; car = null; uxAllowed = false
    }
    private fun finishClosing() {
        sdkGeneration++; vehicle?.stop(); vehicle = null; player = null
        readings.clear(); closeLocation(); closing = false
        if (visible) openConnections()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        pendingPermission = 0
        if (!visible) return
        when (requestCode) {
            VEHICLE_REQUEST -> reconnect()
            CONTROL_REQUEST -> { reconnect(); render() }
            LOCATION_REQUEST -> {
                if (checkSelfPermission(PleosLocationGateway.PERMISSION) == PackageManager.PERMISSION_GRANTED) enableLocation() else {
                    locationConsent = false; closeLocation()
                    readings["location"] = VehicleReading("location", "location", "차량 위치", ReadStatus.FAILED, detail = "위치 권한이 허용되지 않았어요"); refreshReadingsUi()
                }
                if (settings.step == 3) ensureReadPermissions()
            }
        }
    }
    @Deprecated("Platform back is retained for this API 28+ Activity")
    override fun onBackPressed() {
        if (player?.busy == true || testActive) { cancelTest("요청을 중지했어요"); return }
        when (screen) {
            Screen.CONTACT -> if (editing) navigate(Screen.SETTINGS) else super.onBackPressed()
            Screen.CALL -> navigate(if (editing) Screen.SETTINGS else Screen.CONTACT)
            Screen.LOCATION -> navigate(if (editing) Screen.SETTINGS else Screen.CALL)
            Screen.DETAIL, Screen.SETTINGS, Screen.RESULT -> { previewReadings = false; navigate(Screen.VEHICLE) }
            Screen.DEMO, Screen.DELETE_CONFIRM, Screen.RECOVERY -> navigate(Screen.SETTINGS)
            Screen.OUTPUT -> navigate(Screen.DEMO)
            Screen.VEHICLE -> if (previewReadings) { previewReadings = false; render() } else super.onBackPressed()
            else -> navigate(Screen.VEHICLE)
        }
    }
    private fun groupTitle(value: String) = when (value) {
        "doors" -> "문과 잠금"; "tires" -> "타이어"; "battery" -> "EV 배터리"; "airbag" -> "에어백"; "lights" -> "외부 조명"; else -> "차량 위치"
    }
    private fun saveSetting(action: () -> Unit) { try { action() } catch (_: Exception) { toast("정보를 저장하지 못했어요. 다시 시도해 주세요.") } }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    companion object {
        private const val VEHICLE_REQUEST = 100
        private const val LOCATION_REQUEST = 101
        private const val CONTROL_REQUEST = 102
        private const val CONTROL_PERMISSION = "pleos.car.permission.CONTROL_CAR_LIGHTS"
        private val READ_PERMISSIONS = arrayOf("pleos.car.permission.CAR_SAFETY", "pleos.car.permission.CAR_LIGHTS", "pleos.car.permission.CAR_DOORS", "pleos.car.permission.CAR_ENERGY")
    }
}
