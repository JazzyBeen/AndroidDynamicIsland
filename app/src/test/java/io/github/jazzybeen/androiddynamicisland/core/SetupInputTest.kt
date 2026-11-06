package io.github.jazzybeen.androiddynamicisland.core

import io.github.jazzybeen.androiddynamicisland.vpn.embedded.SetupHosts
import io.github.jazzybeen.androiddynamicisland.vpn.embedded.SetupInput
import org.junit.Assert.*
import org.junit.Test

class SetupInputTest {
    @Test fun vlessLinkIsAReadyConfig(){assertTrue(SetupInput.classify("  vless://id@host:443?security=tls#x \n") is SetupInput.Config)}
    @Test fun jsonIsAReadyConfig(){assertTrue(SetupInput.classify("{\"outbounds\":[]}") is SetupInput.Config)}
    @Test fun httpsUrlBecomesAnOriginWithoutPath(){assertEquals(SetupInput.Host("https://api.vpn.test"),SetupInput.classify("https://API.vpn.test/api/v1/x?y=1"))}
    @Test fun opaqueTokenIsAKey(){assertEquals(SetupInput.Key("Ab3_k-9.xyZ"),SetupInput.classify("Ab3_k-9.xyZ"))}
    @Test fun proseIsIgnored(){assertNull(SetupInput.classify("привет как дела"));assertNull(SetupInput.classify(""));assertNull(SetupInput.classify(null));assertNull(SetupInput.classify("short"))}
    @Test fun plainHttpIsNotTrusted(){assertNull(SetupInput.classify("http://api.vpn.test"))}
    @Test fun candidatesSkipLibraryHostsAndRankByFrequency(){
        val blob="x https://firebase.googleapis.com/a https://api.sota.test/api/v1/public y\u0000https://api.sota.test/b https://cdn.sota.test/c https://github.com/z"
        assertEquals(listOf("https://api.sota.test","https://cdn.sota.test"),SetupHosts.candidates(blob))
    }
    @Test fun apiAskingForTheKeyOutranksAJsonNotFound(){
        assertEquals(2,SetupHosts.apiScore(400,"{\"code\":400,\"response\":{\"error\":\"X-Access-Key header is required\"}}"))
        assertEquals(1,SetupHosts.apiScore(404,"{\"detail\":\"Not Found\"}"))
        assertEquals(2,SetupHosts.apiScore(200,"[]"))
    }
    @Test fun htmlRedirectsAndServerErrorsAreNotTheApi(){
        assertEquals(0,SetupHosts.apiScore(200,"<!doctype html>"));assertEquals(0,SetupHosts.apiScore(301,"{}"));assertEquals(0,SetupHosts.apiScore(502,"{}"))
    }
    @Test fun countryNameIsRussian(){assertEquals("Нидерланды",SetupHosts.countryName("nl"));assertEquals("",SetupHosts.countryName("XYZ"))}
}
