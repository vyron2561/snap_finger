package com.khalidsamira.love
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
class VoiceAssistantActivity : Activity(), TextToSpeech.OnInitListener {
 private val surfaceColor = 0xFF1B1D22.toInt()
 private val primaryText = 0xFFF1F1F1.toInt()
 private val secondaryText = 0xFFA1A1AA.toInt()
 private var recognizer: SpeechRecognizer? = null
 private var tts: TextToSpeech? = null
 private var ttsReady = false
 private lateinit var status: TextView
 private lateinit var result: TextView
 private lateinit var listen: Button
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  tts = TextToSpeech(this, this)
  val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(28,36,28,28); setBackgroundColor(0xFF101114.toInt()) }
  root.addView(TextView(this).apply { text="KOFAXA VOICE"; textSize=25f; gravity=Gravity.CENTER; setTextColor(0xFFF1F1F1.toInt()) }, LinearLayout.LayoutParams(-1,0,1f))
  status = TextView(this).apply { text="اضغط وتكلم بالدارجة المغربية"; textSize=18f; gravity=Gravity.CENTER; setTextColor(0xFFA1A1AA.toInt()) }
  result = TextView(this).apply { text="مثال: حلّ إنستا"; textSize=18f; gravity=Gravity.CENTER; setTextColor(-1) }
  listen = Button(this).apply { text="🎙 تكلم مع KOFAXA"; isAllCaps=false; setOnClickListener { listenNow() } }
  root.addView(status,LinearLayout.LayoutParams(-1,0,1f)); root.addView(result,LinearLayout.LayoutParams(-1,0,2f)); listen.setTextColor(0xFF101114.toInt()); listen.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFB9A7D9.toInt()); root.addView(listen,LinearLayout.LayoutParams(-1,58))
  root.addView(Button(this).apply { text="رجوع"; setTextColor(primaryText); backgroundTintList = android.content.res.ColorStateList.valueOf(surfaceColor); setOnClickListener { finish() } },LinearLayout.LayoutParams(-1,52))
  setContentView(root)
  if (SpeechRecognizer.isRecognitionAvailable(this)) {
   recognizer=SpeechRecognizer.createSpeechRecognizer(this)
   recognizer?.setRecognitionListener(object: RecognitionListener {
    override fun onReadyForSpeech(params:Bundle?) { status.text="كنسمع ليك..." }
    override fun onBeginningOfSpeech() { status.text="تفضل، كمل كلامك" }
    override fun onRmsChanged(value:Float) {}
    override fun onBufferReceived(buffer:ByteArray?) {}
    override fun onEndOfSpeech() { status.text="كنفهم الأمر..." }
    override fun onError(error:Int) { status.text="ما فهمتش، عاود حاول"; listen.isEnabled=true }
    override fun onResults(data:Bundle?) { val spoken=data?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty(); result.text=spoken; listen.isEnabled=true; command(spoken) }
    override fun onPartialResults(data:Bundle?) { data?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { result.text=it } }
    override fun onEvent(type:Int,params:Bundle?) {}
   })
  } else { status.text="التعرف على الصوت غير متوفر"; listen.isEnabled=false }
 }
 override fun onInit(code:Int) { ttsReady=code==TextToSpeech.SUCCESS; if(ttsReady) tts?.language=Locale("ar") }
 private fun listenNow() {
  val engine=recognizer ?: return
  listen.isEnabled=false
  val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ar-MA"); putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"ar-MA"); putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true); putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,false); putExtra(RecognizerIntent.EXTRA_PROMPT,"تكلم بالدارجة المغربية") }
  try { engine.startListening(intent) } catch(e:Exception) { listen.isEnabled=true; status.text="تعذر تشغيل الميكروفون" }
 }
 private fun command(raw:String) {
  val c=raw.lowercase(Locale.ROOT).replace(" ","").replace("ّ","").replace("أ","ا").replace("إ","ا").replace("آ","ا")
  val open=listOf("حل","فتح","افتح","شغل","open")
  when {
   listOf("انستغرام","انستا","instagram").any{c.contains(it)} && open.any{c.contains(it)} -> openApp("com.instagram.android","إنستغرام")
   listOf("واتساب","whatsapp").any{c.contains(it)} && open.any{c.contains(it)} -> openApp("com.whatsapp","واتساب")
   listOf("يوتيوب","youtube").any{c.contains(it)} && open.any{c.contains(it)} -> openApp("com.google.android.youtube","يوتيوب")
   listOf("رجع","لور","للور","back").any{c.contains(it)} -> { onBackPressed(); say("حاضر") }
   listOf("سد","خرج","الرئيسية","home").any{c.contains(it)} -> { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)); say("حاضر، رجعناك للرئيسية") }
   c.contains("صيفط") || c.contains("رسل") || c.contains("ميساج") -> { status.text="إرسال الرسائل مع مراجعة المستلم والنص قيد الإضافة"; say("غادي نضيف إرسال الرسائل مع التأكيد ديالك") }
   else -> { status.text="هاد الأمر مازال ما مضافش"; say("سمح ليا، ما فهمتش الأمر") }
  }
 }
 private fun openApp(pkg:String,label:String) { val i=packageManager.getLaunchIntentForPackage(pkg); if(i==null){status.text="$label ما مثبتش";say("$label ما مثبتش")}else{startActivity(i);status.text="فتحت $label";say("حاضر")} }
 private fun say(text:String) { if(ttsReady) tts?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"kofaxa_reply") }
 override fun onDestroy() { recognizer?.destroy(); tts?.stop(); tts?.shutdown(); super.onDestroy() }
}