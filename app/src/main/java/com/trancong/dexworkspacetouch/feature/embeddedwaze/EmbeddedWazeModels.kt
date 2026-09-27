package com.trancong.dexworkspacetouch.feature.embeddedwaze
import android.view.MotionEvent
data class VdmPoint(val x: Float, val y: Float)
fun mapPoint(x: Float, y: Float, viewWidth: Int, viewHeight: Int, displayWidth: Int = 900, displayHeight: Int = 675): VdmPoint {
 require(viewWidth > 0 && viewHeight > 0)
 return VdmPoint((x * displayWidth / viewWidth).coerceIn(0f,displayWidth.toFloat()),(y * displayHeight / viewHeight).coerceIn(0f,displayHeight.toFloat()))
}
fun virtualAction(actionMasked: Int): Int = when(actionMasked) {
 MotionEvent.ACTION_DOWN -> 0; MotionEvent.ACTION_UP -> 1; MotionEvent.ACTION_MOVE -> 2; MotionEvent.ACTION_CANCEL -> 3
 else -> error("Unsupported touch action=$actionMasked")
}
const val VIRTUAL_TOOL_TYPE_PALM = 5
fun virtualToolType(action:Int):Int=if(action==MotionEvent.ACTION_CANCEL)VIRTUAL_TOOL_TYPE_PALM else MotionEvent.TOOL_TYPE_FINGER
fun pressureFor(actionMasked:Int,sourcePressure:Float):Float=if(actionMasked==MotionEvent.ACTION_DOWN)255f else sourcePressure.coerceAtLeast(0f)
fun isWazeMainActivity(packageName:String?,className:String?)=packageName=="com.waze"&&className=="com.waze.MainActivity"
fun dispatcherReady(focusedWindow:Boolean,targetWindow:Boolean,touchableAtPoint:Boolean,transitionIdle:Boolean,layoutIdle:Boolean,targetRunning:Boolean,descriptorMatchesDisplay:Boolean)=focusedWindow&&targetWindow&&touchableAtPoint&&transitionIdle&&layoutIdle&&targetRunning&&descriptorMatchesDisplay
enum class CleanupStep{INPUT,TASK,DEVICE,ASSOCIATION,REFERENCES}
class CleanupSequence{private val done=linkedSetOf<CleanupStep>();fun next()=CleanupStep.entries.firstOrNull{it !in done};fun complete(step:CleanupStep){require(step==next());done+=step};val finished get()=done.size==CleanupStep.entries.size}
fun tapPoint(left:Int,top:Int,right:Int,bottom:Int,displayWidth:Int,displayHeight:Int):VdmPoint{require(right>left&&bottom>top);return VdmPoint((left+right)/2f,(top+bottom)/2f)}
