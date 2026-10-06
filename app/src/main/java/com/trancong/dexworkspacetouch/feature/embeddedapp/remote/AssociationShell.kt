package com.trancong.dexworkspacetouch.feature.embeddedapp.remote
import android.os.SystemClock
import com.trancong.dexworkspacetouch.diagnostics.embedded.EmbeddedEvidence
internal data class SessionAssociation(val id:Int,val mac:String)
internal object AssociationShell {
 private const val PROFILE="android.app.role.COMPANION_DEVICE_APP_STREAMING"
 @Synchronized fun create(diagnosticSid:String?=null):SessionAssociation{
  val n=SystemClock.elapsedRealtime().toLong()
  val mac="02:57:%02X:%02X:%02X:%02X".format((n shr 24)and 255,(n shr 16)and 255,(n shr 8)and 255,n and 255)
  EmbeddedEvidence.remote("allocation.association_attempt",diagnosticSid,mapOf("attempted" to "true"))
  command("cmd","companiondevice","associate","0","com.android.shell",mac,PROFILE,"false",diagnosticSid=diagnosticSid)
  repeat(40){ parse(mac,diagnosticSid)?.let{return it};Thread.sleep(50)}
  error("Association was not visible after creation: $mac")
 }
 @Synchronized fun remove(value:SessionAssociation?,diagnosticSid:String?=null){if(value!=null)command("cmd","companiondevice","disassociate","0","com.android.shell",value.mac,diagnosticSid=diagnosticSid)}
 private fun parse(mac:String,diagnosticSid:String?):SessionAssociation?=command("cmd","companiondevice","list","0",diagnosticSid=diagnosticSid).lineSequence().mapNotNull{
  val p=it.split('|').map(String::trim);if(p.size>=3&&p[1]=="com.android.shell"&&p[2].equals(mac,true)) SessionAssociation(p[0].toInt(),mac) else null
 }.firstOrNull()
 private fun command(vararg args:String,diagnosticSid:String?=null):String{val p=ProcessBuilder(*args).redirectErrorStream(true).start();val text=p.inputStream.bufferedReader().use{it.readText()};val exit=p.waitFor();EmbeddedEvidence.remote("association.command",diagnosticSid,mapOf("step" to args.getOrElse(2){"unknown"},"exit_code" to exit.toString()));check(exit==0){"Command failed: "+args.joinToString(" ")+" "+text};return text}
}
