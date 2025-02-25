package com.venture.mooncast.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.venture.mooncast.R
import com.venture.mooncast.ui.common.CalendarScreen
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

class Sphere(
    private val radius: Float,
    private val latitudeBands: Int,
    private val longitudeBands: Int
) {
    private var vertexBuffer: FloatBuffer
    private var normalBuffer: FloatBuffer
    private var textureBuffer: FloatBuffer
    private var indexBuffer: ShortBuffer
    private var numIndices: Int

    init {
        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val textures = mutableListOf<Float>()
        val indices = mutableListOf<Short>()

        for (lat in 0..latitudeBands) {
            val theta = lat * PI / latitudeBands
            val sinTheta = sin(theta)
            val cosTheta = cos(theta)

            for (long in 0..longitudeBands) {
                val phi = long * 2 * PI / longitudeBands
                val sinPhi = sin(phi)
                val cosPhi = cos(phi)

                val x = cosPhi * sinTheta
                val y = cosTheta
                val z = sinPhi * sinTheta

                // Vertex
                vertices.add((x * radius).toFloat())
                vertices.add((y * radius).toFloat())
                vertices.add((z * radius).toFloat())

                // Normal
                normals.add(x.toFloat())
                normals.add(y.toFloat())
                normals.add(z.toFloat())

                // Texture
                textures.add(long.toFloat() / longitudeBands)
                textures.add(lat.toFloat() / latitudeBands)
            }
        }

        // Create indices
        for (lat in 0 until latitudeBands) {
            for (long in 0 until longitudeBands) {
                val first = (lat * (longitudeBands + 1) + long).toShort()
                val second = (first + longitudeBands + 1).toShort()

                indices.add(first)
                indices.add(second)
                indices.add((first + 1).toShort())
                indices.add(second)
                indices.add((second + 1).toShort())
                indices.add((first + 1).toShort())
            }
        }

        numIndices = indices.size

        // Initialize buffers
        vertexBuffer = createBuffer(vertices.toFloatArray())
        normalBuffer = createBuffer(normals.toFloatArray())
        textureBuffer = createBuffer(textures.toFloatArray())
        indexBuffer = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(indices.toShortArray())
        indexBuffer.position(0)
    }

    private fun createBuffer(data: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(data)
            .apply { position(0) }
    }

    fun draw(program: Int) {
        // Vertex position
        val positionHandle = GLES20.glGetAttribLocation(program, "aPosition").also { position ->
            GLES20.glEnableVertexAttribArray(position)
            GLES20.glVertexAttribPointer(
                position, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer
            )
        }

        // Normal
        val normalHandle = GLES20.glGetAttribLocation(program, "aNormal").also { normal ->
            GLES20.glEnableVertexAttribArray(normal)
            GLES20.glVertexAttribPointer(
                normal, 3, GLES20.GL_FLOAT, false, 0, normalBuffer
            )
        }

        // Texture coordinate
        val texCoordHandle = GLES20.glGetAttribLocation(program, "aTextureCoord").also { texCoord ->
            GLES20.glEnableVertexAttribArray(texCoord)
            GLES20.glVertexAttribPointer(
                texCoord, 2, GLES20.GL_FLOAT, false, 0, textureBuffer
            )
        }
        Log.d("MoonPhase", "Handles: pos=$positionHandle norm=$normalHandle tex=$texCoordHandle")

        // Draw
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES, numIndices,
            GLES20.GL_UNSIGNED_SHORT, indexBuffer
        )
    }
}

class MoonRenderer(private val context: Context) : GLSurfaceView.Renderer {
    private var program = 0
    private lateinit var sphere: Sphere
    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private var phase = 0f
    private var textureId = 0
    private var rotationAngle = 0f

    private val vertexShader = """
        uniform mat4 uMVPMatrix;
        uniform mat4 uMVMatrix;
        attribute vec4 aPosition;
        attribute vec3 aNormal;
        attribute vec2 aTextureCoord;
        varying vec2 vTextureCoord;
        varying vec3 vNormal;
        varying vec3 vPosition;
        
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vTextureCoord = aTextureCoord;
            vNormal = normalize(mat3(uMVMatrix) * aNormal);
            vPosition = (uMVMatrix * aPosition).xyz;
        }
    """.trimIndent()

    private val fragmentShader = """
    precision mediump float;
    uniform sampler2D uTexture;
    uniform vec3 uLightDir;
    uniform vec3 uViewPos;
    varying vec2 vTextureCoord;
    varying vec3 vNormal;
    varying vec3 vPosition;
    
    void main() {
        vec3 normal = normalize(vNormal);
        vec3 viewDir = normalize(uViewPos - vPosition);
        vec3 lightDir = normalize(uLightDir);
        
        float dotProduct = dot(normal, lightDir);
        float darkSideFactor = smoothstep(-1.0, 1.0, dotProduct);

        // Ambient
        float ambientStrength = 0.2;
        vec3 ambient = ambientStrength * vec3(1.0) * mix(0.7, 1.0, darkSideFactor);
        
        // Diffuse
        float diff = pow(max(dotProduct, 0.0), 2.0);
        vec3 diffuse = diff * vec3(1.0);
 
        // Specular
        float specularStrength = 0.1;
       vec3 reflectDir = reflect(-lightDir, normal);
       float spec = pow(max(dot(viewDir, reflectDir), 0.0), 32.0);
       vec3 specular = specularStrength * spec * vec3(1.0) * darkSideFactor;
        
        vec4 texColor = texture2D(uTexture, vTextureCoord);
        vec3 result = (ambient + diffuse + specular) * texColor.rgb;
        gl_FragColor = vec4(result, texColor.a);
    }
"""

    fun updatePhase(newPhase: Float) {
        phase = newPhase
    }

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        program = createProgram()
        sphere = Sphere(1f, 50, 50)
        textureId = loadTexture()
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)

        val ratio = width.toFloat() / height.toFloat()
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 2f, 7f)
    }

    override fun onDrawFrame(unused: GL10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Set camera position
        Matrix.setLookAtM(
            viewMatrix, 0,
            0f, 0f, 3f,  // eye
            0f, 0f, 0f,  // center
            0f, 1f, 0f   // up
        )

        // Calculate light direction based on phase
        val lightAngle = phase * 2f * PI.toFloat()
        val lightX = cos(lightAngle)
        val lightZ = sin(lightAngle)

        // Set program and uniforms
        GLES20.glUseProgram(program)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, rotationAngle, 0f, 1f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)

        GLES20.glUniformMatrix4fv(
            GLES20.glGetUniformLocation(program, "uMVPMatrix"),
            1, false, mvpMatrix, 0
        )
        GLES20.glUniformMatrix4fv(
            GLES20.glGetUniformLocation(program, "uMVMatrix"),
            1, false, modelMatrix, 0
        )
        GLES20.glUniform3f(
            GLES20.glGetUniformLocation(program, "uLightDir"),
            lightX, 0f, lightZ
        )

        // Bind texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(
            GLES20.glGetUniformLocation(program, "uTexture"), 0
        )

        sphere.draw(program)
    }

    private fun createProgram(): Int {
        val program = GLES20.glCreateProgram()
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShader)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader)

        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linked = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
        Log.d("MoonPhase", "Program linked: ${linked[0]}")

        if (linked[0] == 0) {
            val info = GLES20.glGetProgramInfoLog(program)
            Log.e("MoonPhase", "Program linking failed: $info")
        }

        return program
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        Log.d("MoonPhase", "Shader compiled: ${compiled[0]} Type: $type")

        if (compiled[0] == 0) {
            val info = GLES20.glGetShaderInfoLog(shader)
            Log.e("MoonPhase", "Shader compilation failed: $info")
        }
        return shader
    }

    private fun loadTexture(): Int {
        val textureHandle = IntArray(1)
        GLES20.glGenTextures(1, textureHandle, 0)

        if (textureHandle[0] != 0) {
            val options = BitmapFactory.Options()
            options.inSampleSize = 8  // Reduce image size
            val bitmap = BitmapFactory.decodeResource(
                context.resources,
                R.raw.moon_texture,
                options
            )
            Log.d("MoonPhase", "Resized bitmap: ${bitmap.width}x${bitmap.height}")

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
            bitmap.recycle()
        }
        return textureHandle[0]
    }
}

@Composable
fun MoonPhaseScreen() {
    val context = LocalContext.current
    var phase by remember { mutableFloatStateOf(0f) }
    var renderer: MoonRenderer? by remember { mutableStateOf(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AndroidView(
            modifier = Modifier
                .size(300.dp)
                .aspectRatio(1f)
                .background(Color.Red), // Added for visibility
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setZOrderOnTop(true) // Try toggling this
                    setEGLContextClientVersion(2)
                    renderer = MoonRenderer(context)
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                }
            }
        ) { view ->
            renderer?.updatePhase(phase)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Slider(
            value = phase,
            onValueChange = { phase = it },
            modifier = Modifier.fillMaxWidth()
        )
    }

    CalendarScreen()
}

@Preview
@Composable
private fun MoonPhaseScreenPreview() {
    MoonPhaseScreen()
}

@Preview
@Composable
private fun CalendarScreenPreview() {
    CalendarScreen()
}