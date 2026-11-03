package io.github.jazzybeen.androiddynamicisland.core

import io.github.jazzybeen.androiddynamicisland.vpn.embedded.TunnelConfig
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TunnelConfigTest{
    private val server="""{"type":"vless","server":"vpn.example.com","server_port":443,"uuid":"00000000-0000-4000-8000-000000000001","tls":{"enabled":true,"server_name":"example.com"}}"""
    @Test fun importsOnlyTheProxyAndBuildsPrivateRuntime(){
        val input="""{"outbounds":[$server,{"type":"direct","tag":"direct"}],"experimental":{"clash_api":{"external_controller":"0.0.0.0:9090"}},"route":{"rule_set":[{"path":"/secret"}]}}"""
        val profile=TunnelConfig.importProfile(input)
        val runtime=JSONObject(TunnelConfig.runtime(profile))
        assertFalse(runtime.has("experimental"))
        assertFalse(runtime.getJSONObject("route").has("rule_set"))
        assertEquals("tun",runtime.getJSONArray("inbounds").getJSONObject(0).getString("type"))
        assertEquals("proxy",runtime.getJSONObject("route").getString("final"))
        assertEquals("hijack-dns",runtime.getJSONObject("route").getJSONArray("rules").getJSONObject(0).getString("action"))
        assertEquals("remote",runtime.getJSONObject("dns").getString("final"))
    }
    @Test fun importsVlessRealityWithoutDamagingBase64PublicKey(){
        val link="vless://00000000-0000-4000-8000-000000000001@vpn.example.com:443?security=reality&type=tcp&sni=example.com&pbk=a+b-c_&sid=ab12&flow=xtls-rprx-vision#Test"
        val proxy=TunnelConfig.importProfile(link).getJSONObject("proxy")
        assertEquals("a+b-c_",proxy.getJSONObject("tls").getJSONObject("reality").getString("public_key"))
        assertEquals("xtls-rprx-vision",proxy.getString("flow"))
    }
    @Test(expected=IllegalArgumentException::class) fun refusesPrivateFileReferences(){
        val proxy=JSONObject(server);proxy.getJSONObject("tls").put("certificate_path","/data/private.pem")
        TunnelConfig.importProfile(proxy.toString())
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsOutOfRangePorts(){TunnelConfig.importProfile(JSONObject(server).put("server_port",70000).toString())}
    @Test(expected=IllegalStateException::class) fun refusesAmbiguousServerSelection(){TunnelConfig.importProfile("""{"outbounds":[$server,$server]}""")}
}
