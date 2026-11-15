package ai.pleos.contest.dashlink
import org.junit.Assert.*
import org.junit.Test
class DemoScenarioTest {
    @Test fun disabledCallAndMissingContactOverrideOnlineScenario() {
        val result = demoOutcome(DemoScenario.ONLINE, false, false, true)
        assertEquals("자동 신고 사용 안 함", result.call)
        assertEquals("긴급연락처 미등록 · 전송 안 함", result.guardian)
    }
    @Test fun phoneAndInternetAreIndependent() {
        val phoneOnly = demoOutcome(DemoScenario.NO_INTERNET, true, true, true)
        assertTrue(phoneOnly.call.contains("완료"))
        assertTrue(phoneOnly.guardian.contains("실패"))
        val internetOnly = demoOutcome(DemoScenario.NO_PHONE, true, true, true)
        assertTrue(internetOnly.call.contains("실패"))
        assertTrue(internetOnly.guardian.contains("완료"))
    }
    @Test fun missingLocationNeverBecomesDelivered() {
        assertEquals("위치 미확보 · 전송 안 함", demoOutcome(DemoScenario.ONLINE, true, true, false).guardian)
    }
    @Test fun contactRejectsEmergencyAndMalformedNumbers() {
        listOf("119", "112", "", "12345", "+12+34567890", "010-ABCD-1234", "010••••1234").forEach { assertNull(normalizedContact(it)) }
        assertEquals("+821012345678", normalizedContact("+82 10-1234-5678"))
    }
}
