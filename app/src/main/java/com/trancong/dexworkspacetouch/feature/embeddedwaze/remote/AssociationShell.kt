package com.trancong.dexworkspacetouch.feature.embeddedwaze.remote
import android.os.SystemClock
internal data class SessionAssociation(val id:Int,val mac:String)
internal object AssociationShell {
 private const val PROFILE="android.app.role.COMPANION_DEVICE_APP_STREAMING"
 fun create():SessionAssociation{
  val n=SystemClock.elapsedRealtime().toLong()
  val mac="02:57:%02X:%02X:%02X:%02X".format((n shr 24)and 255,(n shr 16)and 255,(n shr 8)and 255,n and 255)
  command("cmd","companiondevice","associate","0","com.android.shell",mac,PROFILE,"false")
  repeat(40){ parse(mac)?.let{return it};Thread.sleep(50)}
  error("Association was not visible after creation: $mac")
 }
 fun remove(value:SessionAssociation?){if(value!=null)command("cmd","companiondevice","disassociate","0","com.android.shell",value.mac)}
 private fun parse(mac:String):SessionAssociation?=command("cmd","companiondevice","list","0").lineSequence().mapNotNull{
  val p=it.split('|').map(String::trim);if(p.size>=3&&p[1]=="com.android.shell"&&p[2].equals(mac,true)) SessionAssociation(p[0].toInt(),mac) else null
 }.firstOrNull()
 private fun command(vararg args:String):String{val p=ProcessBuilder(*args).redirectErrorStream(true).start();val text=p.inputStream.bufferedReader().use{it.readText()};check(p.waitFor()==0){"Command failed: "+args.joinToString(" ")+" "+text};return text}
}
