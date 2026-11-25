package com.marhaban.openme

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.media.AudioManager
import android.media.ToneGenerator

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var message: TextView
    private lateinit var log: TextView
    private lateinit var reaper: ReaperView
    private lateinit var soundButton: Button
    private lateinit var startButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var soundEnabled = true
    private var step = 0
    private var tone: ToneGenerator? = null

    private val sequence = listOf(
        "جارٍ فتح الملف...",
        "لم يتم العثور على اسم معروف.",
        "هناك شيء غير طبيعي هنا.",
        "لا تنظر خلفك.",
        "تم إيقاف الاتصال.",
        "انتهت الجلسة."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(20, 20, 20)
        window.navigationBarColor = Color.rgb(20, 20, 20)
        buildScreen()
    }

    private fun text(value: String, size: Float, color: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            textDirection = View.TEXT_DIRECTION_RTL
        }

    private fun buildScreen() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(20, 20, 20, 16)
            setBackgroundColor(Color.rgb(24, 26, 25))
        }

        root.addView(text("مرحباً أيها المستخدم، افتحني", 19f, Color.rgb(224, 228, 224)))
        root.addView(text("الإصدار 0.8.13  •  2015", 11f, Color.rgb(132, 143, 135)).apply {
            setPadding(0, 7, 0, 22)
        })

        status = text("في انتظار التشغيل", 16f, Color.rgb(185, 195, 187))
        root.addView(status)

        message = text("لا توجد معلومات متاحة.", 14f, Color.rgb(205, 210, 206)).apply {
            setPadding(8, 18, 8, 18)
        }
        root.addView(message)

        reaper = ReaperView(this).apply { visibility = View.GONE }
        root.addView(reaper, LinearLayout.LayoutParams(-1, 0, 1f))

        log = text("سجل النشاط\nتم فتح البرنامج.\nفي انتظار الإدخال...", 11f, Color.rgb(145, 157, 147)).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            textDirection = View.TEXT_DIRECTION_RTL
            setPadding(10, 12, 10, 12)
            setBackgroundColor(Color.rgb(32, 36, 33))
        }
        root.addView(log, LinearLayout.LayoutParams(-1, 0, 1f))

        soundButton = button("الصوت: يعمل") { 
            soundEnabled = !soundEnabled
            soundButton.text = if (soundEnabled) "الصوت: يعمل" else "الصوت: متوقف"
            if (!soundEnabled) tone?.stopTone()
        }
        root.addView(soundButton)

        startButton = button("بدء التجربة") { startSimulation() }
        root.addView(startButton)

        root.addView(button("إيقاف فوري / خروج") { stopSimulation() })

        root.addView(text("محاكاة آمنة داخل التطبيق فقط", 10f, Color.rgb(105, 116, 108)).apply {
            setPadding(0, 12, 0, 0)
        })

        setContentView(root)
    }

    private fun button(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(62, 70, 64))
            setOnClickListener { action() }
        }

    private fun startSimulation() {
        if (running) return
        running = true
        step = 0
        startButton.isEnabled = false
        log.text = "سجل النشاط\nبدأت الجلسة..."
        nextStep()
    }

    private fun nextStep() {
        if (!running) return
        val line = sequence[step]
        message.text = line
        status.text = when (step) {
            0 -> "جارٍ الاتصال"
            1, 2, 3 -> "تحذير: نشاط غير معروف"
            else -> "حالة النظام: غير معروفة"
        }
        log.append("\n• $line")

        if (step == 2 || step == 4) {
            showRedError(step == 2)
        } else {
            root.setBackgroundColor(Color.rgb(24, 26, 25))
            reaper.visibility = View.GONE
            if (soundEnabled) playBeep()
        }

        step++
        if (step < sequence.size) {
            handler.postDelayed({ nextStep() }, 3200)
        } else {
            running = false
            startButton.isEnabled = true
            status.text = "انتهت الجلسة"
            message.text = "لم يتم إجراء أي تغيير على الهاتف."
            root.setBackgroundColor(Color.rgb(24, 26, 25))
            reaper.visibility = View.GONE
        }
    }

    private fun showRedError(showFigure: Boolean) {
        root.setBackgroundColor(Color.rgb(145, 15, 20))
        status.text = "خطأ في النظام"
        message.text = if (showFigure) "تعذر تحديد مصدر الرسالة." else "تم العثور على عنصر مجهول."
        reaper.visibility = if (showFigure) View.VISIBLE else View.GONE
        if (soundEnabled) playBeep()
    }

    private fun playBeep() {
        try {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_MUSIC, 35)
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
        } catch (_: Exception) { }
    }

    private fun stopSimulation() {
        running = false
        handler.removeCallbacksAndMessages(null)
        tone?.stopTone()
        root.setBackgroundColor(Color.rgb(24, 26, 25))
        reaper.visibility = View.GONE
        status.text = "تم إيقاف الجلسة"
        message.text = "أغلقت التجربة بأمان."
        startButton.isEnabled = true
        log.append("\n• أوقف المستخدم التجربة.")
    }

    override fun onPause() {
        super.onPause()
        if (running) stopSimulation()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
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

        paint.color = Color.rgb(8, 8, 8)
        val hood = Path().apply {
            moveTo(w * .5f, h * .08f)
            cubicTo(w * .22f, h * .12f, w * .20f, h * .50f, w * .30f, h * .76f)
            lineTo(w * .70f, h * .76f)
            cubicTo(w * .80f, h * .50f, w * .78f, h * .12f, w * .5f, h * .08f)
            close()
        }
        canvas.drawPath(hood, paint)
        paint.color = Color.rgb(35, 35, 35)
        canvas.drawOval(w * .37f, h * .25f, w * .63f, h * .57f, paint)
        paint.color = Color.rgb(180, 15, 20)
        canvas.drawCircle(w * .44f, h * .41f, w * .018f, paint)
        canvas.drawCircle(w * .56f, h * .41f, w * .018f, paint)
        paint.color = Color.rgb(5, 5, 5)
        val cloak = Path().apply {
            moveTo(w * .30f, h * .70f)
            lineTo(w * .70f, h * .70f)
            lineTo(w * .90f, h)
            lineTo(w * .10f, h)
            close()
        }
        canvas.drawPath(cloak, paint)

        paint.color = Color.argb(80, 255, 255, 255)
        paint.strokeWidth = 2f
        for (i in 0..8) {
            val y = h * (0.12f + i * 0.095f)
            canvas.drawLine(w * .08f, y, w * .92f, y, paint)
        }
    }
}
