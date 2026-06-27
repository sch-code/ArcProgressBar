
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.toColorInt
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 弧形进度条
 * 根据view的宽高，自动计算出过两个底角和上边的弧形，
 * 宽高比要大于2
 */
class MaxArcProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val TAG = "MaxArcProgressView"

    // 画笔
    private val bgArcPaint = Paint().apply {
        isAntiAlias = true
        strokeWidth = 20f
        style = Paint.Style.STROKE
        color = "#33FFFFFF".toColorInt()
        strokeCap = Paint.Cap.ROUND
    }

    private val progressArcPaint = Paint().apply {
        isAntiAlias = true
        strokeWidth = 20f
        style = Paint.Style.STROKE
        color = "#D93EDF".toColorInt()
        strokeCap = Paint.Cap.ROUND
    }

    // Thumb 画笔
    private val thumbPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val thumbStrokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = "#60FFFFFF".toColorInt()
        strokeWidth = 20f
    }

    // Thumb 半径（单位像素）
    private var thumbRadius = 12f

    private val pandding = 30f

    // 进度数据 0-1
    private var currentProgress = 0.8f
    private var progressListener: OnProgressChangeListener? = null

    //下方数据自动计算
    // 圆环矩形
    private val circleRect = RectF()

    // 弧形基础参数（度）
    private var arcStartAngle = 0f
    private var arcTotalSweep = 0f

    // 圆心坐标
    private var centerX = 0f
    private var centerY = 0f


    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val safeW = w - pandding * 2
        val safeH = h - pandding * 2
        calcArcGeo(safeW, safeH, pandding)
        invalidate()
    }

    private fun calcArcGeo(safeW: Float, safeH: Float, offset: Float) {
        if (safeW < 10f || safeH < 5f) {
            arcTotalSweep = 0f
            return
        }
        /**
         * r² = (w/2)² + (r-h)²
         * r² = w²/4 + h² + r² - 2rh
         * 2rh = w²/4 + h²
         * r = (w²/4 + h²)/2h = w²/8h + h/2  = (w²+4h²)/8h
         */
        val r = (safeW * safeW + 4 * safeH * safeH) / (8 * safeH)
        val cx = offset + safeW / 2f
        val cy = offset + r
        circleRect.set(cx - r, cy - r, cx + r, cy + r)

        // 保存圆心用于触摸映射
        centerX = cx
        centerY = cy

        val halfW = safeW / 2f
        val y = safeH - r

        val radStart = atan2(y.toDouble(), (-halfW).toDouble())
        val radSweep = 2 * asin((halfW / r).toDouble())

        // 转为角度，并归一化起始角到 [0, 360)
        arcStartAngle = (Math.toDegrees(radStart).toFloat() % 360 + 360) % 360
        arcTotalSweep = Math.toDegrees(radSweep).toFloat()
        // 保证扫角为正且不大于360
        if (arcTotalSweep < 0) arcTotalSweep += 360f
        if (arcTotalSweep > 360f) arcTotalSweep = 360f

        Log.d(TAG, "起始角(度)=$arcStartAngle, 扫角(度)=$arcTotalSweep")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (arcTotalSweep <= 0.1f) return

        // 背景弧
        canvas.drawArc(circleRect, arcStartAngle, arcTotalSweep, false, bgArcPaint)

        // 进度弧
        val progressRatio = (currentProgress).coerceIn(0f, 1f)
        val drawSweep = arcTotalSweep * progressRatio
        canvas.drawArc(circleRect, arcStartAngle, drawSweep, false, progressArcPaint)

        // 绘制 Thumb（进度指示圆点）
        val thumbAngle = Math.toRadians((arcStartAngle + drawSweep).toDouble())
        val radius = circleRect.width() / 2f
        val thumbX = centerX + radius * cos(thumbAngle).toFloat()
        val thumbY = centerY + radius * sin(thumbAngle).toFloat()
        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint)
        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbStrokePaint)
        Log.v(TAG, "当前进度:$currentProgress 绘制扫角:$drawSweep")
    }

    // --------------------- 触摸处理 ---------------------
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN,  MotionEvent.ACTION_MOVE -> {
                mapTouchToProgress(event.x, event.y)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * 将触摸点坐标映射到进度值并更新
     */
    private fun mapTouchToProgress(touchX: Float, touchY: Float) {
        if (arcTotalSweep <= 0.1f) return

        // 计算触摸点相对于圆心的角度（度），归一化到 [0, 360)
        val dx = touchX - centerX
        val dy = touchY - centerY
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        angle = (angle % 360 + 360) % 360

        // 计算相对于起始角的偏移（逆时针）
        var offset = (angle - arcStartAngle + 360) % 360
        // 限制在总扫角范围内，防止越界
        if (offset > arcTotalSweep) {
            // 如果超出，则映射到最近端点
            offset = if (offset - arcTotalSweep < 180f) arcTotalSweep else 0f
        }
        val ratio = (offset / arcTotalSweep).coerceIn(0f, 1f)
        setProgress(ratio)
        progressListener?.onProgressChanged(currentProgress)
    }

    // --------------------- 公共方法 ---------------------
    fun setProgress(progress: Float) {
        Log.e(TAG, "设置进度:$progress")
        val newValue = progress.coerceIn(0f, 1f)
        if (newValue != currentProgress) {
            currentProgress = newValue
            invalidate()
        }
    }
    private var progressAnimator: ValueAnimator? = null
    /** 带动画的进度设置 */
    fun setProgressAnimated(progress: Float, duration: Long = 300) {
        val clamped = progress.coerceIn(0f, 1f)
        if (clamped == currentProgress) return

        progressAnimator?.cancel()
        progressAnimator = ValueAnimator.ofFloat(currentProgress, clamped).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                setProgress(anim.animatedValue as Float)
            }
            start()
        }
    }

    fun setOnProgressChangeListener(listener: OnProgressChangeListener?) {
        progressListener = listener
    }

    interface OnProgressChangeListener {
        fun onProgressChanged(progress: Float)
    }
}
