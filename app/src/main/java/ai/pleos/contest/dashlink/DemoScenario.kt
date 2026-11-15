package ai.pleos.contest.dashlink

enum class DemoScenario(val title: String, val detail: String, val phone: Boolean, val internet: Boolean) {
    ONLINE("통신 가능한 사고", "전화와 인터넷이 가능한 상황", true, true),
    OFFLINE("통신이 끊긴 사고", "빛으로 전하는 마지막 도움 요청", false, false),
    NO_INTERNET("인터넷만 끊김", "전화는 가능하지만 위치를 보낼 수 없는 상황", true, false),
    NO_PHONE("통화만 불가", "인터넷으로 연락처에 위치를 전달하는 상황", false, true),
    CANCEL("괜찮아요 · 요청 취소", "10초 안에 응답해 도움 요청을 취소", true, true),
    READINGS("조회 상태 살펴보기", "예시 데이터로 성공·오류·미지원 살펴보기", false, false),
    VEHICLE("차량 연결 신호로 시험", "지원되는 에어백의 새 전개 신호 기다리기", true, true),
}

data class DemoOutcome(val call: String, val guardian: String)

fun demoOutcome(scenario: DemoScenario, callAllowed: Boolean, contactExists: Boolean, locationAvailable: Boolean) = DemoOutcome(
    when {
        !callAllowed -> "자동 신고 사용 안 함"
        !scenario.phone -> "통화 불가 상황 · 모의 연결 실패"
        else -> "모의 신고 요청 완료 · 실제 발신 없음"
    },
    when {
        !contactExists -> "긴급연락처 미등록 · 전송 안 함"
        !locationAvailable -> "위치 미확보 · 전송 안 함"
        !scenario.internet -> "인터넷 연결 불가 · 모의 전송 실패"
        else -> "모의 전송 완료 · 실제 발송 없음"
    },
)

fun normalizedContact(number: String): String? {
    val value = number.trim().replace("-", "").replace(" ", "")
    return value.takeIf { it.matches(Regex("\\+?[0-9]{9,15}")) }
}
