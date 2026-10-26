package org.ambienttv.home

import org.junit.Assert.*
import org.junit.Test

class PublicConfigurationTest {
 @Test fun httpOnlyAcceptsPrivateAddressLiterals(){
  for(host in listOf("10.2.3.4","172.16.0.2","172.31.255.254","192.168.0.2"))assertTrue(ConnectionPolicy.isPrivateIpv4(host))
  for(host in listOf("example.com","localhost","127.0.0.1","169.254.1.2","172.15.0.2","172.32.0.2","8.8.8.8","10.0.0.256","010.0.0.1","10.0.0.1.example.com","10.0.0.1@evil.example"))assertFalse(host,ConnectionPolicy.isPrivateIpv4(host))
 }
 @Test fun weatherValidatesCoordinatesAndDoesNotHardcodeAHome(){
  val url=WeatherLocation.endpoint("0","0")
  assertTrue(url.contains("latitude=0.0000&longitude=0.0000"));assertTrue(url.contains("timezone=auto"))
  for((lat,lon) in listOf("NaN" to "0","Infinity" to "0","91" to "0","0" to "181","" to "0"))assertThrows(IllegalArgumentException::class.java){WeatherLocation.endpoint(lat,lon)}
 }
}
