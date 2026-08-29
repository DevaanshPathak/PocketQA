package com.indium.pocketqa.controller

import org.junit.Assert.assertEquals
import org.junit.Test

class BugRoutingTest {
    @Test fun `small grounded issue stays local`() {
        assertEquals(PatchRoute.LOCAL_GEMMA, BugRouter.route(sourceChars = 3_000, files = 2, confidence = .9))
    }

    @Test fun `large multi file issue escalates when cloud is ready`() {
        assertEquals(PatchRoute.OPENROUTER, BugRouter.route(sourceChars = 90_000, files = 9, confidence = .8, cloudReady = true))
    }

    @Test fun `large issue abstains without configured cloud`() {
        assertEquals(PatchRoute.NEEDS_CONFIGURATION, BugRouter.route(sourceChars = 90_000, files = 9, confidence = .8))
    }
}
