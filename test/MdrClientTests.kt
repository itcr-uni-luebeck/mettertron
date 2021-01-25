package de.uniluebeck.itcr.highmed

import de.uniluebeck.itcr.highmed.mettertron.poc.api.admin.MdrLoginState
import de.uniluebeck.itcr.highmed.mettertron.poc.api.admin.ReceivedMdrLoginState
import org.junit.Test
class MdrClientTests {


    @Test
    fun testMdrExpiryCorrect() {
        val receivedMdrLoginState = ReceivedMdrLoginState(
            accessToken = "no-token",
            expiresInSeconds = 1,
            tokenType = "foo",
            scope = "bar"
        )
        val state = MdrLoginState.fromReceivedMdrLoginState(receivedMdrLoginState)
        val expiresAt1 = state.expiresAt
        assert(!state.isExpired) {
            "state is expired when it shouldn't be"
        }
        Thread.sleep(1_250)
        val expiresAt2 = state.expiresAt
        assert(expiresAt1 == expiresAt2) {
            "expiry is changing over time"
        }
        assert(state.isExpired) {
            "state is not expired after it should have!"
        }
    }
}