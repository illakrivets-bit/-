package com.marhaban.openme

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var header: TextView
    private lateinit var status: TextView
    private lateinit var message: TextView
    private lateinit var log: TextView
    private lateinit var reaper: ReaperView
    private lateinit var soundButton: Button
    private lateinit var startButton: Button
    private lateinit var nextButton: Button
    private lateinit var soundLevel: SeekBar
    private lateinit var soundValue: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val audioHandler = Handler(Looper.getMainLooper())
    private var running = false
    private var soundEnabled = true
    private var soundLevelValue = 22
    private var step = 0
    private var redMode = false
    private var tone: ToneGenerator? = null
    private val logLines = mutableListOf<String>()

    private data class Scene(
        val status: String,
        val message: String,
        val red: Boolean = false,
        val reaper: Boolean = false,
        val sound: Int = 0
    )

    private val scenes = listOf(
        Scene("جارٍ فتح الملف", "مرحباً...\nلم يتم العثور على وصف لهذا البرنامج.", sound = 1),
        Scene("فحص غير مكتمل", "الاسم غير مسجل في قاعدة البيانات.\nالمصدر: غير معروف.", sound = 0),
        Scene("تحذير", "هناك شيء غير طبيعي هنا.", red = true, sound = 2),
        Scene("اتصال غير معروف", "لا تغلق النافذة الآن.", sound = 1),
        Scene("خطأ في النظام", "تعذر قراءة البيانات.\nرمز الخطأ: 0x0000A17", red = true, reaper = true, sound = 3),
        Scene("تم العثور على سجل", "لماذا عدت إلى هنا؟", sound = 0),
        Scene("فشل الاتصال", "انتهت الجلسة... أم أنها لم تبدأ؟", red = true, sound = 2),
        Scene("اكتملت العملية", "لم يتم إجراء أي تغيير على الهاتف.", sound = 1)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(20, 20, 20)
        window.navigationBarColor = Color.rgb(20, 20, 20)
        buildScreen()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun makeText(value: String, size: Float, color: Int, align: Int = Gravity.CENTER): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = align
            textDirection = View.TEXT_DIRECTION_RTL
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }

    private fun buildScreen() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(16), dp(18), dp(12))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(Color.rgb(23, 25, 24))
        }

        header = makeText("مرحباً أيها المستخدم، افتحني", 19f, Color.rgb(230, 232, 229))
        root.addView(header)

        root.addView(makeText("الإصدار 0.8.13  •  2015  •  ملف غير معروف", 10f, Color.rgb(125, 137, 129))
            .apply { setPadding(0, dp(5), 0, dp(18)) })

        status = makeText("في انتظار التشغيل", 15f, Color.rgb(191, 199, 192))
        root.addView(status)

        message = makeText("لا توجد معلومات متاحة.", 14f, Color.rgb(215, 219, 216))
        message.setPadding(dp(8), dp(15), dp(8), dp(15))
        root.addView(message)

        reaper = ReaperView(this).apply { visibility = View.GONE }
        root.addView(reaper, LinearLayout.LayoutParams(-1, dp(150)))

        val logHeading = makeText("سجل النشاط", 10f, Color.rgb(133, 145, 136), Gravity.RIGHT)
        logHeading.setPadding(0, dp(8), 0, dp(5))
        root.addView(logHeading)

        log = makeText("تم فتح البرنامج.\nفي انتظار الإدخال...", 10f, Color.rgb(145, 157, 147), Gravity.RIGHT).apply {
            typeface = Typeface.MONOSPACE
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(Color.rgb(31, 35, 32))
            maxLines = 5
        }
        root.addView(log, LinearLayout.LayoutParams(-1, dp(92)))

        val settingsTitle = makeText("إعدادات المؤثرات", 11f, Color.rgb(145, 157, 147), Gravity.RIGHT)
        settingsTitle.setPadding(0, dp(9), 0, dp(3))
        root.addView(settingsTitle)

        soundButton = makeButton("الصوت: يعمل") {
            soundEnabled = !soundEnabled
            soundButton.text = if (soundEnabled) "الصوت: يعمل" else "الصوت: متوقف"
            if (!soundEnabled) tone?.stopTone()
        }
        root.addView(soundButton)

        soundValue = makeText("مستوى المؤثرات: 22٪", 10f, Color.rgb(145, 157, 147), Gravity.RIGHT)
        root.addView(soundValue)
        soundLevel = SeekBar(this).apply {
            max = 35
            progress = soundLevelValue
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                    soundLevelValue = value
                    soundValue.text = "مستوى المؤثرات: $value٪"
                    tone?.release()
                    tone = null
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        root.addView(soundLevel)

        startButton = makeButton("بدء التجربة") { startSimulation() }
        root.addView(startButton)

        nextButton = makeButton("المشهد التالي") { showNextScene() }
        nextButton.isEnabled = false
        root.addView(nextButton)

        root.addView(makeButton("إيقاف فوري / خروج") { stopSimulation() })

        root.addView(makeText("محاكاة آمنة • لا تغييرات على الهاتف", 9f, Color.rgb(105, 116, 108))
            .apply { setPadding(0, dp(9), 0, 0) })

        setContentView(root)
    }

    private fun makeButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(57, 65, 59))
            setOnClickListener { action() }
        }

    private fun startSimulation() {
        if (running) return
        running = true
        step = 0
        logLines.clear()
        startButton.isEnabled = false
        nextButton.isEnabled = true
        appendLog("بدأت الجلسة")
        showScene(scenes[step])
        scheduleNext()
    }

    private fun scheduleNext() {
        handler.removeCallbacksAndMessages(null)
        if (running && step < scenes.size - 1) {
            handler.postDelayed({ showNextScene() }, 4200)
        }
    }

    private fun showNextScene() {
        if (!running) return
        if (step < scenes.size - 1) {
            step++
            showScene(scenes[step])
            scheduleNext()
        } else {
            finishSequence()
        }
    }

    private fun showScene(scene: Scene) {
        redMode = scene.red
        root.setBackgroundColor(if (scene.red) Color.rgb(145, 12, 18) else Color.rgb(23, 25, 24))
        status.text = scene.status
        status.setTextColor(if (scene.red) Color.WHITE else Color.rgb(191, 199, 192))
        message.text = scene.message
        message.setTextColor(if (scene.red) Color.WHITE else Color.rgb(215, 219, 216))
        reaper.visibility = if (scene.reaper) View.VISIBLE else View.GONE
        reaper.invalidate()
        appendLog(scene.message.replace("\n", " "))
        if (scene.sound > 0) playEffect(scene.sound)
    }

    private fun appendLog(line: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logLines.add("$time  $line")
        while (logLines.size > 5) logLines.removeAt(0)
        log.text = logLines.joinToString("\n")
    }

    private fun finishSequence() {
        running = false
        handler.removeCallbacksAndMessages(null)
        nextButton.isEnabled = false
        startButton.isEnabled = true
        root.setBackgroundColor(Color.rgb(23, 25, 24))
        reaper.visibility = View.GONE
        status.text = "انتهت الجلسة"
        message.text = "لم يتم إجراء أي تغيير على الهاتف."
        appendLog("أغلقت الجلسة بأمان")
    }

    private fun playEffect(kind: Int) {
        if (!soundEnabled || soundLevelValue == 0) return
        try {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_MUSIC, soundLevelValue)
            audioHandler.removeCallbacksAndMessages(null)
            tone?.stopTone()
            val pattern = when (kind) {
                1 -> listOf(ToneGenerator.TONE_PROP_BEEP, ToneGenerator.TONE_DTMF_2)
                2 -> listOf(ToneGenerator.TONE_PROP_NACK, ToneGenerator.TONE_PROP_NACK)
                else -> listOf(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, ToneGenerator.TONE_DTMF_0, ToneGenerator.TONE_PROP_BEEP)
            }
            pattern.forEachIndexed { index, toneType ->
                audioHandler.postDelayed({
                    if (soundEnabled && soundLevelValue > 0) {
                        tone?.startTone(toneType, 115)
                    }
                }, index * 190L)
            }
        } catch (_: Exception) { }
    }

    private fun stopSimulation() {
        running = false
        handler.removeCallbacksAndMessages(null)
        audioHandler.removeCallbacksAndMessages(null)
        tone?.stopTone()
        root.setBackgroundColor(Color.rgb(23, 25, 24))
        reaper.visibility = View.GONE
        status.text = "تم إيقاف الجلسة"
        message.text = "أغلقت التجربة بأمان."
        nextButton.isEnabled = false
        startButton.isEnabled = true
        appendLog("أوقف المستخدم التجربة")
    }

    override fun onPause() {
        super.onPause()
        if (running) stopSimulation()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        audioHandler.removeCallbacksAndMessages(null)
        tone?.release()
        tone = null
        super.onDestroy()
    }
}

private class ReaperView(context: android.content.Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(Color.rgb(105, 5, 12))

        paint.color = Color.rgb(5, 5, 5)
        val hood = Path().apply {
            moveTo(w * .5f, h * .04f)
            cubicTo(w * .18f, h * .12f, w * .20f, h * .58f, w * .29f, h * .78f)
            lineTo(w * .71f, h * .78f)
            cubicTo(w * .80f, h * .58f, w * .82f, h * .12f, w * .5f, h * .04f)
            close()
        }
        canvas.drawPath(hood, paint)

        paint.color = Color.rgb(24, 24, 24)
        canvas.drawOval(w * .37f, h * .24f, w * .63f, h * .59f, paint)

        paint.color = Color.rgb(175, 20, 25)
        canvas.drawCircle(w * .445f, h * .41f, w * .014f, paint)
        canvas.drawCircle(w * .555f, h * .41f, w * .014f, paint)

        paint.color = Color.rgb(4, 4, 4)
        val robe = Path().apply {
            moveTo(w * .29f, h * .70f)
            lineTo(w * .71f, h * .70f)
            lineTo(w * .92f, h)
            lineTo(w * .08f, h)
            close()
        }
        canvas.drawPath(robe, paint)

        paint.color = Color.argb(75, 235, 235, 235)
        paint.strokeWidth = 1.5f
        for (i in 0..8) {
            val y = h * (0.10f + i * 0.10f)
            canvas.drawLine(w * .04f, y, w * .96f, y, paint)
        }

        paint.color = Color.argb(110, 255, 255, 255)
        paint.textSize = h * .07f
        paint.typeface = Typeface.MONOSPACE
        canvas.drawText("رمز مجهول  //  ٠٠", w * .05f, h * .94f, paint)
    }
}
