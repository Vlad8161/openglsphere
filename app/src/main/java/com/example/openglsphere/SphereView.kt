package com.example.openglsphere

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.support.v4.view.animation.LinearOutSlowInInterpolator
import android.view.MotionEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Created by applexis on 24.10.18.
 */

class SphereView(context: Context?) : GLSurfaceView(context), GLSurfaceView.Renderer {
    private val POLYGON_COUNT = 4000
    private val POLYGON_SIZE = 0.02f
    private val SPHERE_RADIUS = 0.5f
    private val VELOCITY_SCALE = 32f
    private val VELOCITY_STACK_SIZE = 10
    private val mProjectionMatrix = FloatArray(16)
    private var mProgramId: Int = 0
    private var mVertexShaderId: Int = 0

    private var mAngleXLocation: Int = 0
    private var mAngleYLocation: Int = 0
    private var mRotationAngleLocation: Int = 0
    private var mTextureLocation: Int = 0
    private var mTextureUnitLocation: Int = 0

    private var mTextureId: Int = 0

    private val xAngles = FloatArray(POLYGON_COUNT)
    private val yAngles = FloatArray(POLYGON_COUNT)
    private var mRotationAngle = 0f

    private var mOldSwipePosition: Float? = null
    private var mLastTime: Long = 0L
    private var mLastVelocities = ArrayList<Float>()
    private var mLastPlayTime: Long = 0L

    private var mRotationAngleAnimator: ValueAnimator? = null

    private val vertexShaderSrc = """
        attribute vec4 a_Position;
        attribute vec2 a_Texture;
        uniform float u_AngleX;
        uniform float u_AngleY;
        uniform float u_RotationAngle;
        uniform mat4 u_ProjectionMatrix;
        varying vec2 v_Texture;

        float pi = 3.1415926;

        mat4 translateZ = mat4(
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, -18, 1
        );

        void main() {
            float angleY = u_AngleY + u_RotationAngle;
            mat4 rotX = mat4(
                1,          0,           0, 0,
                0, cos(u_AngleX), -sin(u_AngleX), 0,
                0, sin(u_AngleX),  cos(u_AngleX), 0,
                0,          0,           0, 1
            );
            mat4 rotY = mat4(
                 cos(angleY), 0, sin(angleY), 0,
                          0, 1,          0, 0,
                -sin(angleY), 0, cos(angleY), 0,
                          0, 0,          0, 1
            );
            gl_Position = u_ProjectionMatrix
                * translateZ
                * rotY
                * rotX
                * a_Position;
            v_Texture = a_Texture;
        }
        """
            .trimIndent()

    private val fragmentShaderSrc = """
        precision mediump float;
        uniform sampler2D u_TextureUnit;
        varying vec2 v_Texture;

        void main() {
            gl_FragColor = texture2D(u_TextureUnit, v_Texture);
        }
        """
            .trimIndent()

    init {
        setEGLContextClientVersion(2)
        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                mOldSwipePosition = event.x
                mLastTime = System.currentTimeMillis()
                mRotationAngleAnimator?.cancel()
                mLastVelocities.clear()
            }
            MotionEvent.ACTION_UP -> {
                val velocity = if (mLastVelocities.size > 0) {
                    mLastVelocities
                            .also { it.mapIndexed { i, value -> value * i / (0 until mLastVelocities.size).sum() } }
                            .also { it.sort() }
                            .sum() / mLastVelocities.size
                            //.get(mLastVelocities.size / 2)
                } else {
                    0f
                }
                animateAngle(velocity)
                //animateAngle(mLastVelocities.sum() / mLastVelocities.size)
                mOldSwipePosition = null
                mLastVelocities.clear()
            }
            MotionEvent.ACTION_MOVE -> {
                val newSwipePosition = event.x
                val oldSwipePosition = mOldSwipePosition
                val currentTime = System.currentTimeMillis()
                if (oldSwipePosition != null) {
                    var velocity = (newSwipePosition - oldSwipePosition) / width * VELOCITY_SCALE
                    velocity = if (velocity < 0) {
                        -Math.pow(-velocity.toDouble(), 2.0).toFloat()
                    } else {
                        Math.pow(velocity.toDouble(), 2.0).toFloat()
                    }
                    velocity /= currentTime - mLastTime
                    mRotationAngle += velocity
                    mLastVelocities.add(velocity)
                }
                if (mLastVelocities.size > VELOCITY_STACK_SIZE) {
                    mLastVelocities.removeAt(VELOCITY_STACK_SIZE)
                }
                mOldSwipePosition = newSwipePosition
                mLastTime = currentTime
            }
        }
        return true
    }

    override fun onDrawFrame(gl: GL10) {
        glClear(GL_COLOR_BUFFER_BIT)
        for (i in 0 until POLYGON_COUNT) {
            glUniform1f(mAngleXLocation, xAngles[i])
            glUniform1f(mAngleYLocation, yAngles[i])
            glActiveTexture(GLES20.GL_TEXTURE0)
            glBindTexture(GLES20.GL_TEXTURE_2D, mTextureLocation)
            glUniform1i(mTextureUnitLocation, GLES20.GL_TEXTURE0)
            glUniform1f(mRotationAngleLocation, mRotationAngle)
            glDrawArrays(GL_TRIANGLE_FAN, 0, 4)
        }
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        val ratio = width.toFloat() / height.toFloat()
        glViewport(0, 0, width, height)
        Matrix.frustumM(mProjectionMatrix, 0, -ratio / 4, ratio / 4, -1f / 4, 1f / 4, 3f, 26f)
        val uProjectionMatrixLocation = glGetUniformLocation(mProgramId, "u_ProjectionMatrix")
        glUniformMatrix4fv(uProjectionMatrixLocation, 1, false, mProjectionMatrix, 0)
    }

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig?) {
        glClearColor(0f, 1f, 1f, 0f)
        glEnable(GLES20.GL_CULL_FACE)
        glCullFace(GLES20.GL_BACK)

        mVertexShaderId = createShader(GL_VERTEX_SHADER, vertexShaderSrc)
        if (mVertexShaderId == 0) {
            throw RuntimeException("Unable to create vertexShader")
        }
        val fragmentShaderId = createShader(GL_FRAGMENT_SHADER, fragmentShaderSrc)
        if (fragmentShaderId == 0) {
            glDeleteShader(mVertexShaderId)
            throw RuntimeException("Unable to create fragmentShader")
        }
        mProgramId = createProgram(mVertexShaderId, fragmentShaderId)
        if (mProgramId == 0) {
            glDeleteShader(mVertexShaderId)
            glDeleteShader(fragmentShaderId)
            throw RuntimeException("Unable to create program")
        }
        glUseProgram(mProgramId)

        val vertices = floatArrayOf(
                -POLYGON_SIZE, -POLYGON_SIZE, -SPHERE_RADIUS,
                POLYGON_SIZE, -POLYGON_SIZE, -SPHERE_RADIUS,
                POLYGON_SIZE, POLYGON_SIZE, -SPHERE_RADIUS,
                -POLYGON_SIZE, POLYGON_SIZE, -SPHERE_RADIUS
        )
        val vertexData = ByteBuffer.allocateDirect(vertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .also { it.put(vertices) }
                .also { it.position(0) }
        val textureCoordinates = floatArrayOf(
                0f, 1f,
                0f, 0f,
                1f, 0f,
                1f, 1f,
                0f, 1f,
                1f, 0f
        )
        val textureCoordinateData = ByteBuffer.allocateDirect(textureCoordinates.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .also { it.put(textureCoordinates) }
                .also { it.position(0) }

        for (i in 1 until POLYGON_COUNT) {
            xAngles[i] = (Random().nextGaussian() / 1.3).toFloat()
            yAngles[i] = (Math.random() * Math.PI * 2).toFloat()
        }

        mAngleXLocation = glGetUniformLocation(mProgramId, "u_AngleX")
        mAngleYLocation = glGetUniformLocation(mProgramId, "u_AngleY")
        mTextureUnitLocation = glGetUniformLocation(mProgramId, "u_TextureUnit")
        mRotationAngleLocation = glGetUniformLocation(mProgramId, "u_RotationAngle")

        val uColorLocation = glGetUniformLocation(mProgramId, "u_Color")
        glUniform4f(uColorLocation, 1.0f, 0.0f, 0.0f, 1.0f)

        val aPositionLocation = glGetAttribLocation(mProgramId, "a_Position")
        glVertexAttribPointer(aPositionLocation, 3, GL_FLOAT, false, 0, vertexData)
        glEnableVertexAttribArray(aPositionLocation)

        mTextureLocation = glGetAttribLocation(mProgramId, "a_Texture")
        glVertexAttribPointer(mTextureLocation, 2, GL_FLOAT, false, 0, textureCoordinateData)
        glEnableVertexAttribArray(mTextureLocation)

        createTextures()
    }

    private fun createShader(type: Int, shaderText: String): Int {
        val shaderId = glCreateShader(type)
        if (shaderId == 0) {
            return 0
        }

        val glResult = intArrayOf(0)
        glShaderSource(shaderId, shaderText)
        glCompileShader(shaderId)
        glGetShaderiv(shaderId, GL_COMPILE_STATUS, glResult, 0)
        if (glResult[0] == 0) {
            glDeleteShader(shaderId)
            return 0
        }

        return shaderId
    }

    private fun createProgram(vertexShaderId: Int, fragmentShaderId: Int): Int {
        val programId = glCreateProgram()
        if (programId == 0) {
            return 0
        }

        val result = intArrayOf(0)
        glAttachShader(programId, vertexShaderId)
        glAttachShader(programId, fragmentShaderId)
        glLinkProgram(programId)
        glGetProgramiv(programId, GL_LINK_STATUS, result, 0)
        if (result[0] == 0) {
            glDeleteProgram(programId)
            return 0
        }

        return programId
    }

    private fun createTextures() {
        val textureIds = IntArray(1)
        glGenTextures(1, textureIds, 0)
        if (textureIds[0] == 0) {
            throw RuntimeException("Unable to create textures")
        }

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        val bmp = BitmapFactory.decodeResource(resources, R.drawable.ic_stat_name)
        GLES20.glActiveTexture(GL_TEXTURE0)
        GLES20.glBindTexture(GL_TEXTURE_2D, textureIds[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        bmp.recycle()
        GLES20.glBindTexture(GL_TEXTURE_2D, textureIds[0])
        mTextureId = textureIds[0]
    }

    private fun animateAngle(velocity: Float) {
        mRotationAngleAnimator?.cancel()
        mLastPlayTime = 0L
        mRotationAngleAnimator = ObjectAnimator.ofFloat(velocity, 0f)
                .also { it.duration = 3000 }
                .also { it.interpolator = LinearOutSlowInInterpolator() }
                .also {
                    it.addUpdateListener {
                        val deltaT = it.currentPlayTime - mLastPlayTime
                        mRotationAngle += (it.animatedValue as Float) * deltaT
                        mLastPlayTime = it.currentPlayTime
                        requestRender()
                    }
                }
                .also { it.start() }
    }
}